# -*- coding: utf-8 -*-
"""
【文件作用】
    把本地数据文件归档到 HDFS（WebHDFS HTTP 接口），实现“本地 + HDFS 两段式保存”。

【两段式存储设计】
    第一段（本地）：爬虫先写 data/raw/*.jsonl，支持断点续传、出错重试、方便查看；
    第二段（HDFS）：本脚本把本地文件上传到 HDFS /taptap/... 做归档（只保留最新数据，覆盖写）。
    每个文件上传后读回 HDFS 内容，核对字节数和 SHA-256；校验通过才计入成功。
    内容按块读取，不把大文件一次装入内存；读回校验会增加网络传输和耗时。

【为什么用 WebHDFS】
    HDFS 官方提供 HTTP 接口（WebHDFS），不需要在 Windows 安装 Hadoop 客户端，
    也不用给 Java 加 Hadoop 依赖，Python 用 requests 即可上传。

【运行方式】
    $env:TAPTAP_DB_PASSWORD = "你的密码"      # 可选：用于写采集日志；不配也能上传
    python upload_to_hdfs.py --small          # 只上传小文件（测试用）
    python upload_to_hdfs.py                  # 上传全部（含 163MB 详情文件）
    python upload_to_hdfs.py --dry-run        # 只检查文件与 HDFS 连通性，不上传
"""
import argparse
import hashlib
import json
import os
import sys
import time
from pathlib import Path
from urllib.parse import urlparse, urlunparse

import requests

# 把 data_server 目录加入模块搜索路径：
# 这样无论是 `python jobs/upload_to_hdfs.py` 还是 IDEA 绿色启动，都能正常 import config
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import config

# 项目根目录：本文件位于 <项目>/data_server/jobs/upload_to_hdfs.py
BASE = Path(__file__).resolve().parents[2]

# WebHDFS 接口地址
HDFS_API = f"http://{config.HDFS_HOST}:{config.HDFS_WEB_PORT}/webhdfs/v1"

# 待归档文件清单：(本地路径, HDFS 路径)
FILES = [
    (BASE / "data" / "game_ids.json",                        f"{config.HDFS_BASE}/raw/game_ids.json"),
    (BASE / "data" / "raw" / "closure" / "all_app_ids.json", f"{config.HDFS_BASE}/raw/closure/all_app_ids.json"),
    (BASE / "data" / "raw" / "taptap_details.jsonl",         f"{config.HDFS_BASE}/raw/taptap_details.jsonl"),
    (BASE / "data" / "output" / "game_clusters.csv",         f"{config.HDFS_BASE}/output/game_clusters.csv"),
    (BASE / "data" / "output" / "cluster_summary.json",      f"{config.HDFS_BASE}/output/cluster_summary.json"),
]

HASH_CHUNK_SIZE = 1024 * 1024                       # 每次读取 1 MB


def params(op: str, **extra) -> dict:
    """WebHDFS 通用参数：操作类型 + 用户"""
    p = {"op": op, "user.name": config.HDFS_USER}
    p.update(extra)
    return p


def mkdirs(hdfs_dir: str) -> None:
    """在 HDFS 上递归创建目录（已存在也不报错）"""
    r = requests.put(HDFS_API + hdfs_dir, params=params("MKDIRS"), timeout=15)
    if r.status_code != 200 or not r.json().get("boolean", False):
        raise RuntimeError(f"创建 HDFS 目录失败: {hdfs_dir} -> {r.status_code} {r.text[:120]}")


def ensure_parent_dirs(hdfs_path: str) -> None:
    """确保文件所在的多级目录都存在，例如 /taptap/raw/closure"""
    parts = hdfs_path.strip("/").split("/")[:-1]      # 去掉文件名，保留目录部分
    cur = ""
    for part in parts:
        cur += "/" + part
        mkdirs(cur)


def hdfs_size(hdfs_path: str):
    """查询 HDFS 上文件大小；不存在返回 None"""
    r = requests.get(HDFS_API + hdfs_path, params=params("GETFILESTATUS"), timeout=15)
    if r.status_code == 404:
        return None
    if r.status_code != 200:
        return None
    return r.json().get("FileStatus", {}).get("length")


def sha256_chunks(chunks) -> tuple[int, str]:
    """逐块计算内容指纹，同时记录实际读取的字节数。"""
    digest = hashlib.sha256()
    size = 0
    for chunk in chunks:
        if chunk:
            digest.update(chunk)
            size += len(chunk)
    return size, digest.hexdigest()


def local_sha256(local: Path) -> tuple[int, str]:
    """上传前记录本地文件内容；校验以这份内容为准。"""
    with local.open("rb") as f:
        return sha256_chunks(iter(lambda: f.read(HASH_CHUNK_SIZE), b""))


def datanode_url(location: str) -> str:
    """沿用单虚拟机部署的 IP 替换，避免 Windows 无法解析 DataNode 主机名。"""
    loc = urlparse(location)
    return urlunparse((loc.scheme, f"{config.HDFS_HOST}:{loc.port or 50075}",
                       loc.path, loc.params, loc.query, loc.fragment))


def hdfs_sha256(hdfs_path: str) -> tuple[int, str]:
    """通过 WebHDFS OPEN 读回文件，用相同的 SHA-256 算法计算指纹。"""
    with requests.get(HDFS_API + hdfs_path, params=params("OPEN"),
                      stream=True, allow_redirects=False, timeout=(10, 60)) as r:
        if r.status_code == 200:
            return sha256_chunks(r.iter_content(chunk_size=HASH_CHUNK_SIZE))
        if r.status_code != 307:
            raise RuntimeError(f"读取 HDFS 文件失败: {hdfs_path} -> HTTP {r.status_code}")
        location = r.headers.get("Location")
        if not location:
            raise RuntimeError(f"读取 HDFS 文件失败: 重定向缺少 Location: {hdfs_path}")
        url = datanode_url(location)

    with requests.get(url, stream=True, allow_redirects=False, timeout=(10, 60)) as r:
        if r.status_code != 200:
            raise RuntimeError(f"读取 HDFS 数据失败: {hdfs_path} -> HTTP {r.status_code}")
        return sha256_chunks(r.iter_content(chunk_size=HASH_CHUNK_SIZE))


def verify_upload(hdfs_path: str, expected_size: int, expected_sha256: str) -> None:
    """只有完整读回且内容一致才成功；大小相同的错误内容也会被识别。"""
    remote_size, remote_sha256 = hdfs_sha256(hdfs_path)
    if remote_size != expected_size or remote_sha256 != expected_sha256:
        raise RuntimeError(
            f"SHA-256 校验失败: {hdfs_path}; "
            f"本地 {expected_size} 字节/{expected_sha256}; "
            f"HDFS {remote_size} 字节/{remote_sha256}，请检查后重新上传"
        )
    print(f"    SHA-256 校验通过: {expected_sha256}", flush=True)


def upload(local: Path, hdfs_path: str) -> int:
    """
    两步上传（WebHDFS CREATE 规范）：
      第 1 步：向 NameNode 请求创建文件，拿到 DataNode 的重定向地址；
      第 2 步：把文件内容 PUT 到 DataNode。
      第 3 步：读回 HDFS 内容，与上传前本地 SHA-256 和字节数比较。
    校验通过才返回字节数；上传/读取/校验失败都会抛出异常。
    """
    ensure_parent_dirs(hdfs_path)                              # 先建目录
    size, expected_sha256 = local_sha256(local)                # 上传前计算内容指纹

    # 第 1 步：请求创建（overwrite=true → 覆盖旧文件，只保留最新数据）
    r1 = requests.put(HDFS_API + hdfs_path,
                      params=params("CREATE", overwrite="true"),
                      allow_redirects=False, timeout=30)
    if r1.status_code not in (307, 201):
        raise RuntimeError(f"创建 HDFS 文件失败: {r1.status_code} {r1.text[:150]}")
    if r1.status_code == 201:                                  # 直接成功也必须读回校验
        verify_upload(hdfs_path, size, expected_sha256)
        return size

    # 拿到 DataNode 地址；把主机名换成 IP（避免 Windows 解析不了虚拟机主机名）
    loc = datanode_url(r1.headers["Location"])

    # 第 2 步：上传文件内容
    t0 = time.time()
    with open(local, "rb") as f:
        r2 = requests.put(loc, data=f, headers={"Content-Type": "application/octet-stream"}, timeout=1800)
    if r2.status_code not in (201, 200):
        raise RuntimeError(f"上传数据失败: {r2.status_code} {r2.text[:150]}")

    used = time.time() - t0
    speed = size / 1024 / 1024 / used if used > 0 else 0
    print(f"    -> {hdfs_path}  {size/1024/1024:.1f} MB  用时 {used:.1f}s  ({speed:.1f} MB/s)", flush=True)
    verify_upload(hdfs_path, size, expected_sha256)
    return size


def write_crawl_log(total_files: int, success: int, failed: int, total_bytes: int, started, finished):
    """写采集日志到 MySQL t_crawl_log（数据库不可用时只提示，不影响上传结果）"""
    try:
        import pymysql
        conn = pymysql.connect(**config.db_config())
        cur = conn.cursor()
        cur.execute("""INSERT INTO t_crawl_log(batch_id, task_type, status, total, success, failed,
                                               started_at, finished_at, message)
                       VALUES(%s,%s,%s,%s,%s,%s,%s,%s,%s)""",
                    (f"hdfs-{time.strftime('%Y%m%d%H%M')}", "HDFS归档",
                     "DONE" if failed == 0 else "FAILED", total_files, success, failed,
                     started, finished, f"归档 {total_bytes/1024/1024:.1f} MB 到 {config.HDFS_BASE}"))
        conn.commit(); cur.close(); conn.close()
        print("已写入采集日志 t_crawl_log")
    except Exception as e:
        print(f"（提示）采集日志写入失败，不影响上传：{e}")


def main():
    parser = argparse.ArgumentParser(description="本地数据归档到 HDFS（WebHDFS）")
    parser.add_argument("--small", action="store_true", help="只上传小文件（跳过 163MB 详情文件）")
    parser.add_argument("--dry-run", action="store_true", help="只检查文件与 HDFS 连通性，不上传")
    args = parser.parse_args()

    print(f"HDFS: {HDFS_API}  用户: {config.HDFS_USER}  目录: {config.HDFS_BASE}")

    # 连通性检查：列一下 HDFS 根目录
    try:
        r = requests.get(HDFS_API + "/", params=params("LISTSTATUS"), timeout=10)
        print(f"HDFS 连通: HTTP {r.status_code}")
    except Exception as e:
        print(f"HDFS 连接失败: {e}"); sys.exit(1)

    files = [f for f in FILES if f[0].exists()]                 # 只处理存在的文件
    if args.small:
        files = [f for f in files if f[0].stat().st_size < 10 * 1024 * 1024]   # 跳过大于 10MB 的
    print(f"待上传 {len(files)} 个文件：")
    for local, remote in files:
        print(f"  {local.relative_to(BASE)}  ({local.stat().st_size/1024/1024:.1f} MB) -> {remote}")

    if args.dry_run:
        print("dry-run：不上传。"); return

    started = time.strftime("%Y-%m-%d %H:%M:%S")
    total_bytes, success, failed = 0, 0, 0
    file_results = []
    for local, remote in files:
        print(f"\n上传 {local.name} ...", flush=True)
        try:
            verified_bytes = upload(local, remote)
            total_bytes += verified_bytes
            success += 1
            file_results.append({"local": str(local), "hdfs": remote,
                                 "verified": True, "bytes": verified_bytes})
        except Exception as e:
            failed += 1
            file_results.append({"local": str(local), "hdfs": remote,
                                 "verified": False, "error": str(e)})
            print(f"  上传或校验失败: {e}")
    finished = time.strftime("%Y-%m-%d %H:%M:%S")

    # 本地留存一份上传记录
    log_path = BASE / "data" / "raw" / "hdfs_upload_log.json"
    with log_path.open("w", encoding="utf-8") as f:
        json.dump({"time": finished, "files": len(files), "success": success, "failed": failed,
                   "bytes": total_bytes, "hdfs_base": config.HDFS_BASE,
                   "verification": "SHA-256", "file_results": file_results},
                  f, ensure_ascii=False, indent=2)

    print(f"\n完成：校验成功 {success}，失败 {failed}，校验通过 {total_bytes/1024/1024:.1f} MB")
    write_crawl_log(len(files), success, failed, total_bytes, started, finished)
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()
