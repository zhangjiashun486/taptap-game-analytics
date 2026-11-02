# -*- coding: utf-8 -*-
"""
【文件作用】
    第 2 步爬虫：根据游戏 ID 清单，逐个抓取 TapTap 游戏“完整详情”，保存为 JSONL。

【上下游关系】
    上游：01_crawl_games.py 产出 data/game_ids.json（本次 10,221 个 ID）
    下游：data_server/jobs/import_games.py 读取本脚本产出的 JSONL，导入 MySQL

【接口】
    GET https://www.taptap.cn/webapiv2/app/v6/detail?id={游戏ID}
    返回 JSON：data.app 中包含 标题/评分/评论数/关注数/热度/标签/简介/厂商/发行时间 等字段

【设计要点】
    - 断点续跑：progress.json 记录“已完成/确认无效”的 ID，中断后可继续；
    - 失败重试：单个 ID 最多重试 3 次，网络异常/限流会退避；
    - 已确认无效（下架/删除）的 ID 记入 dead 列表，不再重复请求；
    - 低频访问：默认每条间隔 0.7~1.2 秒（本次 10,219 条耗时约 195 分钟）；
    - 只存元数据：不下载图片/游戏包，不采集游戏内容，仅教学用途。

【输出】
    data/raw/taptap_details.jsonl   每行一款游戏的完整 JSON（本次 163MB / 10,219 行）
    data/raw/progress.json          断点（done / dead）
    data/raw/status.txt             结束状态

【运行方式】
    $env:TAPTAP_DB_PASSWORD 不需要（本脚本不连数据库）
    python 02_crawl_details.py
"""
import json
import os
import random
import signal
import subprocess
import time
import uuid
from pathlib import Path

import requests

# ---------------- 路径配置 ----------------
BASE = Path(__file__).resolve().parents[3]                 # 项目根目录 HunuProjec2
RAW_DIR = BASE / "data" / "raw"                            # 原始数据目录
RAW_DIR.mkdir(parents=True, exist_ok=True)
IDS_FILE = BASE / "data" / "game_ids.json"                 # 待爬游戏 ID（来自第 1 步爬虫）
FALLBACK_IDS = RAW_DIR / "closure" / "all_app_ids.json"    # 兼容：第 1 步爬虫的直接输出
OUT_FILE = RAW_DIR / "taptap_details.jsonl"                # 结果文件（JSONL，一行一条）
PROGRESS_FILE = RAW_DIR / "progress.json"                  # 断点文件
STATUS_FILE = RAW_DIR / "status.txt"                       # 结束状态

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
interrupted = False                                        # Ctrl+C 标志位


def build_xua():
    """构造 X-UA 参数（客户端标识，非签名）：语言/平台/随机 UID"""
    return ("V=1&PN=WebApp&LANG=zh_CN&VN_CODE=102&VN=0.1.0&LOC=CN&PLT=PC&DS=Android"
            f"&UID={uuid.uuid4()}&OS=Windows&OSV=10&DT=PC")


def fetch_detail(app_id):
    """请求单个游戏详情。返回 (app字典, 'ok')；失败返回 (None, 原因文本)"""
    headers = {"User-Agent": UA,
               "Referer": f"https://www.taptap.cn/app/{app_id}",   # 带上来源页，模拟正常访问
               "Accept": "application/json, text/plain, */*"}
    params = {"id": app_id, "X-UA": build_xua()}                   # 接口参数：id + X-UA
    resp = requests.get("https://www.taptap.cn/webapiv2/app/v6/detail",
                        params=params, headers=headers, timeout=25)
    data = resp.json()                                             # 解析 JSON
    if not data.get("success"):                                    # 接口层失败（如游戏不存在）
        return None, str(data.get("data", {}).get("msg", "fail"))
    app = (data.get("data") or {}).get("app")                      # 真正的游戏数据在 data.app
    if not app:
        return None, "no-app"
    return app, "ok"


def load_progress():
    """读取断点：已完成 ID 集合 + 无效 ID 集合"""
    if PROGRESS_FILE.exists():
        with open(PROGRESS_FILE, encoding="utf-8") as f:
            p = json.load(f)
        return set(p.get("done", [])), set(p.get("dead", []))
    return set(), set()


def save_progress(done, dead):
    """原子写断点：先写 .tmp 再替换，防止写一半断电损坏文件"""
    tmp = str(PROGRESS_FILE) + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump({"done": sorted(done), "dead": sorted(dead)}, f, ensure_ascii=False)
    os.replace(tmp, PROGRESS_FILE)


def notify(msg):
    """完成后弹 Windows 提示框；无桌面环境时静默失败"""
    try:
        ps = ("Add-Type -AssemblyName System.Windows.Forms; "
              f"[System.Windows.Forms.MessageBox]::Show('{msg}', 'TapTap 详情补爬提醒')")
        subprocess.Popen(["powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command", ps])
    except Exception:
        pass


def mark(reason, done_n, dead_n):
    """写结束状态 + 弹提示"""
    with open(STATUS_FILE, "w", encoding="utf-8") as f:
        f.write(f"reason={reason}\ndone={done_n}\ndead={dead_n}\ntime={time.strftime('%Y-%m-%d %H:%M:%S')}")
    msg = {"DONE": f"TapTap 详情补爬完成！\n成功：{done_n} 款\n无效/下架：{dead_n} 款",
           "PAUSED": f"详情补爬已暂停（可续跑）。\n已完成：{done_n} 款",
           "TIMEOUT": f"详情补爬达到时间上限暂停。\n已完成：{done_n} 款",
           "ERROR": f"详情补爬异常停止，请查看日志。\n已完成：{done_n} 款"}.get(reason, f"状态：{reason}")
    notify(msg)


def on_interrupt(sig, frame):
    """Ctrl+C：置标志位，主循环安全退出并存断点"""
    global interrupted
    interrupted = True


signal.signal(signal.SIGINT, on_interrupt)


def main():
    t0 = time.time()

    # 优先使用 data/game_ids.json；若不存在则退回第 1 步爬虫的 all_app_ids.json
    ids_path = IDS_FILE if IDS_FILE.exists() else FALLBACK_IDS
    with open(ids_path, encoding="utf-8") as f:
        ids = json.load(f)

    done, dead = load_progress()                        # 读取断点
    todo = [x for x in ids if x not in done and x not in dead]   # 本次要爬的 ID
    print(f"[补爬] 总数 {len(ids)} | 已完成 {len(done)} | 无效 {len(dead)} | 待爬 {len(todo)}", flush=True)

    base_delay = 0.7                                    # 基础间隔（秒），连续失败会加大
    consec_fail = 0                                     # 连续失败计数
    reason = None
    out = open(OUT_FILE, "a", encoding="utf-8")         # 追加模式：支持断点续跑

    try:
        for app_id in todo:
            if interrupted or (time.time() - t0) > 8 * 3600:   # 中断或超过 8 小时 → 退出
                reason = "PAUSED" if interrupted else "TIMEOUT"
                break
            ok = False
            last_msg = ""
            for attempt in range(3):                    # 单个 ID 最多重试 3 次
                try:
                    app, msg = fetch_detail(app_id)
                    if app is not None:
                        out.write(json.dumps({"id": app_id, "app": app}, ensure_ascii=False) + "\n")
                        out.flush()                     # 立即刷盘，保证中断也不丢数据
                        done.add(app_id)
                        consec_fail = 0
                        ok = True
                        break
                    last_msg = msg
                    if msg and ("不存在" in msg or "invalid" in msg.lower()):   # 明确无效 → 不再重试
                        break
                    time.sleep(5 + attempt * 5)         # 退避后重试
                except Exception as e:
                    last_msg = repr(e)[:80]
                    time.sleep(6 + attempt * 8)
            if not ok:
                if "Connection" in last_msg or "timeout" in last_msg.lower() or "Read timed" in last_msg:
                    consec_fail += 1
                    if consec_fail >= 5:                # 连续网络失败 → 休息 60 秒并放慢速度
                        print("连续失败 5 次，退避 60 秒", flush=True)
                        time.sleep(60)
                        consec_fail = 0
                        base_delay = min(base_delay + 0.3, 3.0)
                else:
                    dead.add(app_id)                    # 接口明确说无效 → 记入 dead
            if len(done) % 25 == 0:                     # 每 25 条保存一次断点
                save_progress(done, dead)
                print(f"  已完成 {len(done)}/{len(ids)} | 无效 {len(dead)} | 已用 {(time.time()-t0)/60:.1f}min", flush=True)
            time.sleep(random.uniform(base_delay, base_delay + 0.5))    # 低频间隔
    except Exception as e:
        print("异常:", repr(e), flush=True)
        reason = "ERROR"
    finally:
        out.close()
        save_progress(done, dead)                       # 退出前保存断点
        if reason is None:
            reason = "DONE" if len(done) + len(dead) >= len(ids) else "PAUSED"
        mark(reason, len(done), len(dead))
        print(f"\n[结束] {reason} | 成功 {len(done)} | 无效 {len(dead)} | 总 {len(ids)} | "
              f"耗时 {(time.time()-t0)/60:.1f} 分钟", flush=True)


if __name__ == "__main__":
    main()