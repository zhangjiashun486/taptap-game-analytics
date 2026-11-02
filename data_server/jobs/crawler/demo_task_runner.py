# -*- coding: utf-8 -*-
"""
隔离演示任务执行器（第三阶段）。

只允许管理端发起安全的小规模任务：
  game-id  抓取一个榜单页，写入 data/demo/
  detail   最多抓取 N 条详情，写入 data/demo/
  archive  校验本地文件，生成演示归档清单；可选上传 HDFS /taptap/demo/

正式数据保护：
  - 不修改 data/raw/taptap_details.jsonl
  - 不修改 data/raw/progress.json
  - 不修改 data/game_ids.json
  - 不写 MySQL 正式业务表（任务日志由 Java 写）
"""
import argparse
import importlib.util
import json
import os
import random
import sys
import time
import uuid
from pathlib import Path

import requests

sys.stdout.reconfigure(encoding="utf-8", line_buffering=True)

BASE = Path(__file__).resolve().parents[3]
DEMO_DIR = BASE / "data" / "demo"
CRAWLER_DIR = BASE / "data_server" / "jobs" / "crawler"
IDS_FILE = BASE / "data" / "game_ids.json"
OFFICIAL_DETAILS = BASE / "data" / "raw" / "taptap_details.jsonl"
DEMO_DETAILS = DEMO_DIR / "taptap_details_demo.jsonl"
DEMO_IDS = DEMO_DIR / "game_ids_demo.json"
DEMO_PROGRESS = DEMO_DIR / "progress_demo.json"
DEMO_MANIFEST = DEMO_DIR / "archive_manifest.json"

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")


def log(message):
    print(f"[LOG] {message}", flush=True)


def warn(message):
    print(f"[WARN] {message}", flush=True)


def progress(value, stage):
    print(f"[PROGRESS] {value} {stage}", flush=True)


def result(total, success, failed=0):
    print(f"[RESULT] total={total} success={success} failed={failed}", flush=True)


def build_xua():
    return ("V=1&PN=WebApp&LANG=zh_CN&VN_CODE=102&VN=0.1.0&LOC=CN&PLT=PC&DS=Android"
            f"&UID={uuid.uuid4()}&OS=Windows&OSV=10&DT=PC")


def load_detail_module():
    module_path = CRAWLER_DIR / "02_crawl_details.py"
    spec = importlib.util.spec_from_file_location("tap_crawl_details_reference", module_path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def run_game_id(limit):
    DEMO_DIR.mkdir(parents=True, exist_ok=True)
    progress(10, "连接榜单")
    url = "https://www.taptap.cn/webapiv2/app-top/v2/hits"
    params = {"type_name": "hot", "from": 0, "limit": limit, "X-UA": build_xua()}
    headers = {"User-Agent": UA, "Referer": "https://www.taptap.cn/top/download"}
    response = requests.get(url, params=params, headers=headers, timeout=25)
    response.raise_for_status()
    payload = response.json()
    if not payload.get("success"):
        raise RuntimeError(f"榜单接口失败: {payload.get('data')}")

    progress(55, "解析游戏 ID")
    items = ((payload.get("data") or {}).get("list") or [])[:limit]
    ids = []
    for item in items:
        app = item.get("app") or {}
        if app.get("id") is not None:
            ids.append(int(app["id"]))

    progress(85, "写入演示 ID 文件")
    DEMO_IDS.write_text(json.dumps(ids, ensure_ascii=False, indent=2), encoding="utf-8")
    progress(100, "任务完成")
    log(f"榜单演示采集完成，共 {len(ids)} 个游戏 ID")
    result(len(ids), len(ids), 0)


def run_detail(limit):
    DEMO_DIR.mkdir(parents=True, exist_ok=True)
    detail_module = load_detail_module()
    ids = json.loads(IDS_FILE.read_text(encoding="utf-8"))
    todo = ids[:limit]
    if not todo:
        raise RuntimeError(f"没有可用游戏 ID: {IDS_FILE}")

    success = 0
    failed = 0
    progress(3, "读取游戏 ID")
    log(f"演示详情补爬：计划 {len(todo)} 条，输出仅写入 data/demo/")

    with DEMO_DETAILS.open("w", encoding="utf-8") as out:
        for index, app_id in enumerate(todo, start=1):
            stage = f"详情抓取 {index}/{len(todo)}"
            progress(3 + int(index / len(todo) * 92), stage)
            try:
                app, message = detail_module.fetch_detail(str(app_id))
                if app is None:
                    failed += 1
                    warn(f"ID {app_id} 抓取失败: {message}")
                else:
                    out.write(json.dumps({"id": app_id, "app": app}, ensure_ascii=False) + "\n")
                    out.flush()
                    success += 1
                    log(f"已完成 {index}/{len(todo)}：{app.get('title') or app_id}")
            except Exception as exc:
                failed += 1
                warn(f"ID {app_id} 异常: {repr(exc)[:120]}")
            time.sleep(random.uniform(0.35, 0.7))

    DEMO_PROGRESS.write_text(json.dumps({
        "mode": "demo",
        "done": success,
        "failed": failed,
        "total": len(todo),
        "time": time.strftime("%Y-%m-%d %H:%M:%S"),
    }, ensure_ascii=False, indent=2), encoding="utf-8")

    progress(100, "任务完成")
    result(len(todo), success, failed)


def count_lines(path):
    if not path.exists():
        return 0
    count = 0
    with path.open("rb") as file:
        for _ in file:
            count += 1
    return count


def upload_manifest_to_demo_hdfs(data):
    host = os.getenv("HDFS_HOST", "192.168.100.101")
    port = os.getenv("HDFS_WEB_PORT", "50070")
    user = os.getenv("HDFS_USER", "root")
    base = f"http://{host}:{port}/webhdfs/v1"
    params = {"user.name": user}

    mkdir = requests.put(f"{base}/taptap/demo", params={**params, "op": "MKDIRS"}, timeout=20)
    if mkdir.status_code != 200 or not mkdir.json().get("boolean", False):
        raise RuntimeError(f"MKDIRS 失败: {mkdir.status_code}")

    create = requests.put(
        f"{base}/taptap/demo/archive_manifest.json",
        params={**params, "op": "CREATE", "overwrite": "true"},
        allow_redirects=False,
        timeout=30,
    )
    if create.status_code == 201:
        return create.status_code
    if create.status_code != 307 or "Location" not in create.headers:
        raise RuntimeError(f"CREATE 失败: {create.status_code} {create.text[:120]}")

    location = urlparse(create.headers["Location"])
    datanode = urlunparse((
        location.scheme,
        f"{host}:{location.port or 50075}",
        location.path,
        location.params,
        location.query,
        location.fragment,
    ))
    written = requests.put(
        datanode,
        data=data,
        headers={"Content-Type": "application/octet-stream"},
        timeout=60,
    )
    if written.status_code not in (200, 201):
        raise RuntimeError(f"DataNode 上传失败: {written.status_code}")
    return written.status_code


def run_archive(use_hdfs):
    DEMO_DIR.mkdir(parents=True, exist_ok=True)
    progress(10, "校验正式数据")
    official_count = count_lines(OFFICIAL_DETAILS)
    log(f"正式详情文件行数（只读检查）：{official_count}")

    progress(35, "校验演示数据")
    demo_count = count_lines(DEMO_DETAILS)
    log(f"演示详情文件行数：{demo_count}")
    time.sleep(0.4)

    progress(60, "生成归档清单")
    manifest = {
        "mode": "DEMO",
        "official_details_readonly": official_count,
        "demo_details": demo_count,
        "mysql_write": False,
        "hdfs_target": "/taptap/demo/archive_manifest.json",
        "time": time.strftime("%Y-%m-%d %H:%M:%S"),
    }
    data = json.dumps(manifest, ensure_ascii=False, indent=2).encode("utf-8")
    DEMO_MANIFEST.write_bytes(data)
    log(f"演示归档清单已写入：{DEMO_MANIFEST}")

    if use_hdfs:
        progress(82, "上传 HDFS 演示目录")
        try:
            status = upload_manifest_to_demo_hdfs(data)
            log(f"HDFS 演示归档成功，HTTP {status}，目标 /taptap/demo/")
        except Exception as exc:
            warn(f"HDFS 演示归档未完成（不影响正式数据）：{repr(exc)[:120]}")
    else:
        log("未开启 HDFS 上传，仅生成本地演示清单")

    progress(100, "任务完成")
    result(demo_count, demo_count, 0)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--mode", required=True, choices=["game-id", "detail", "archive"])
    parser.add_argument("--limit", type=int, default=20)
    parser.add_argument("--hdfs", action="store_true")
    args = parser.parse_args()
    limit = max(1, min(args.limit, 20))

    log(f"隔离演示任务启动：mode={args.mode}, limit={limit}")
    try:
        if args.mode == "game-id":
            run_game_id(limit)
        elif args.mode == "detail":
            run_detail(limit)
        else:
            run_archive(args.hdfs)
    except Exception as exc:
        print(f"[ERROR] {repr(exc)}", flush=True)
        raise SystemExit(1)


if __name__ == "__main__":
    main()