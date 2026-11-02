# -*- coding: utf-8 -*-
"""
【文件作用】
    第 1 步爬虫：TapTap 游戏“闭合爬取”——把能公开访问到的游戏 ID 尽量全部收集起来。

【爬取思路】（对应课设"数据采集"环节）
    6个榜单
→ 拿到第一批游戏和标签
→ 爬标签页
→ 标签页又发现新游戏、新标签
→ 继续入队
→ 直到队列为空
→ 输出 all_app_ids.json

【为什么这么设计】
    - 只用公开接口/公开页面：不做登录、不逆向签名，合规风险低；
    - 低频访问（每次请求随机 0.6~1.3 秒）：避免给对方服务器造成压力；
    - 断点续跑：每个标签处理完就存盘，中断/关机后可继续，不重复劳动；
    - 失败退避：连续失败就暂停，避免触发风控。

【输入】无（从榜单种子开始）
【输出】
    data/raw/closure/state.json        断点进度（游戏集合/已爬标签/队列）
    data/raw/closure/all_app_ids.json  最终全部游戏 ID（本次实测 10,221 个）
    data/raw/closure/status.txt        结束状态（DONE/PAUSED/ERROR + 数量）

【运行方式】
    python 01_crawl_games.py [标签上限] [每标签最大页数] [时间上限分钟]
    默认: 标签不限 / 每标签 15 页 / 600 分钟
"""
import json
import os
import random
import re
import signal
import subprocess
import sys
import time
import urllib.parse
import uuid
from pathlib import Path

import requests

# ---------------- 路径配置 ----------------
# 本文件位于 <项目>/data_server/jobs/crawler/01_crawl_games.py
# parents[3] = 项目根目录（HunuProjec2），输出的数据统一放到根目录 data/ 下
BASE = Path(__file__).resolve().parents[3]
OUT_DIR = BASE / "data" / "raw" / "closure"
OUT_DIR.mkdir(parents=True, exist_ok=True)
STATE_FILE = OUT_DIR / "state.json"          # 断点文件：记录已爬到哪、还剩什么
IDS_FILE = OUT_DIR / "all_app_ids.json"      # 最终结果：全部去重后的游戏 ID
STATUS_FILE = OUT_DIR / "status.txt"         # 结束状态标记（给管理端/自己看）

# ---------------- 请求头 ----------------
# X-UA 是 TapTap Web 端约定的客户端标识参数（浏览器访问时也会携带），不是签名，只是标识设备/语言
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
HEADERS = {"User-Agent": UA, "Accept-Language": "zh-CN,zh;q=0.9"}
session = requests.Session()
session.headers.update(HEADERS)

interrupted = False                          # 收到 Ctrl+C 时置 True，用于优雅退出并保存断点


def build_xua():
    """生成 X-UA 参数：包含语言、平台、随机 UID 等信息（模拟正常 Web 客户端）"""
    return ("V=1&PN=WebApp&LANG=zh_CN&VN_CODE=102&VN=0.1.0&LOC=CN&PLT=PC&DS=Android"
            f"&UID={uuid.uuid4()}&OS=Windows&OSV=10&DT=PC")


def http_get(url, params=None, tries=3):
    """带重试的 GET 请求：失败/被限流时退避重试，返回文本；彻底失败返回 None"""
    for i in range(tries):
        try:
            resp = session.get(url, params=params, timeout=25)
            if resp.status_code == 200:                       # 正常返回
                return resp.text
            if resp.status_code in (429, 403, 412):           # 被限流/风控 → 等更久再试
                time.sleep(10 + i * 15)
                continue
            return None                                        # 其他状态码当作失败
        except Exception:                                      # 网络异常 → 短暂等待重试
            time.sleep(5)
    return None


def save_state(games, visited_tags, tag_queue, history):
    """把当前进度写入断点文件（先写临时文件再替换，避免中途断电写坏 JSON）"""
    tmp = str(STATE_FILE) + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump({"games": sorted(games), "tags_visited": sorted(visited_tags),
                   "tag_queue": tag_queue, "history": history, "board_done": True},
                  f, ensure_ascii=False)
    os.replace(tmp, STATE_FILE)


def load_state():
    """读取断点文件；不存在则返回初始状态（第一次运行）"""
    if STATE_FILE.exists():
        with open(STATE_FILE, encoding="utf-8") as f:
            st = json.load(f)
        return set(st["games"]), set(st["tags_visited"]), list(st["tag_queue"]), st["history"], st.get("board_done", False)
    return set(), set(), [], [], False


def mark_and_notify(reason, games_n, visited_n, queue_n):
    """结束时写状态文件，并弹一个 Windows 提示框（可选功能，失败不影响主流程）"""
    with open(STATUS_FILE, "w", encoding="utf-8") as f:
        f.write(f"reason={reason}\ngames={games_n}\nvisited_tags={visited_n}\nqueue={queue_n}\n"
                f"time={time.strftime('%Y-%m-%d %H:%M:%S')}")
    msg = {"DONE": f"TapTap 爬虫已全部跑完！\n唯一游戏总数：{games_n}\n共爬标签：{visited_n}",
           "PAUSED": f"爬虫已暂停（可续跑）。当前唯一游戏：{games_n}",
           "TIMEOUT": f"爬虫达到时间上限暂停。当前唯一游戏：{games_n}",
           "ERROR": f"爬虫异常停止，请查看日志。当前唯一游戏：{games_n}"}.get(reason, f"状态：{reason}")
    try:
        ps = ("Add-Type -AssemblyName System.Windows.Forms; "
              f"[System.Windows.Forms.MessageBox]::Show('{msg}', 'TapTap 爬虫提醒')")
        subprocess.Popen(["powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command", ps])
    except Exception:
        pass                                                # 弹窗失败（如无桌面环境）就跳过


def on_interrupt(sig, frame):
    """捕获 Ctrl+C：只置标志位，让主循环走到安全点再存盘退出"""
    global interrupted
    interrupted = True


signal.signal(signal.SIGINT, on_interrupt)


def crawl_boards(games, tag_queue, history):
    """第 1 阶段：爬 6 个榜单，把榜单里的游戏 ID 和标签加入种子"""
    board_types = ["hot", "new", "reserve", "pop", "sell", "in_app_event_reserve"]
    for type_name in board_types:                            # 逐个榜单
        frm = 0                                              # 分页游标（每页 10 条）
        while True:
            params = {"type_name": type_name, "from": frm, "limit": 10, "X-UA": build_xua()}
            txt = http_get("https://www.taptap.cn/webapiv2/app-top/v2/hits", params)#get请求返回json
            if not txt:
                print(f"  榜单 {type_name} 请求失败，跳过", flush=True)
                break
            data = json.loads(txt)                           # 解析 JSON，变成 Python字典
            if not data.get("success"):                      # 接口返回失败
                break
            items = data["data"].get("list") or []           # 本页游戏列表
            if not items:                                    # 没有更多数据 → 该榜结束
                break
            for item in items:
                app = item.get("app") or {}                  # 游戏对象
                if app.get("id"):
                    games.add(int(app["id"]))                # 收集游戏 ID（set 自动去重）
                for tag in app.get("tags") or []:            # 收集标签作为下一阶段种子
                    value = tag.get("value")
                    if value and value not in tag_queue:     #不为空，且不重复
                        tag_queue.append(value)
            total = data["data"].get("total") or 0
            frm += 10
            if frm >= total:                                 # 翻到最后一页
                break
            time.sleep(random.uniform(0.7, 1.2))             # 低频：每页之间随机等待
        print(f"  榜单 {type_name} 完成，累计游戏 {len(games)}", flush=True)
    return games, tag_queue


def crawl_tags(games, visited_tags, tag_queue, history, tag_cap, page_cap, time_cap_min, t0):
    """第 2 阶段：广度优先爬标签页；标签页会发现新标签，继续入队直到跑完/达到上限"""
    consec_fail = 0                                          # 连续失败计数
    while tag_queue and len(visited_tags) < tag_cap and (time.time() - t0) < time_cap_min * 60 and not interrupted:
        tag = tag_queue.pop(0)                               # 取出一个待爬标签
        if tag in visited_tags:
            continue
        visited_tags.add(tag)
        before = len(games)                                  # 记录爬之前游戏数，便于统计本标签新增
        enc_tag = urllib.parse.quote(tag)                    # URL 编码（中文标签）
        page = 1
        while page <= page_cap and not interrupted:
            url = f"https://www.taptap.cn/tag/{enc_tag}"
            txt = http_get(url, {"page": page} if page > 1 else None)
            if not txt:
                consec_fail += 1
                if consec_fail >= 3:                         # 连续失败过多 → 保存断点后退出
                    save_state(games, visited_tags, tag_queue, history)
                    return "ERROR"
                break
            ids = set(int(x) for x in re.findall(r'/app/(\d+)', txt))   # 页面里的游戏 ID
            if not ids:
                break
            games.update(ids)                                # 去重合并
            consec_fail = 0
            for raw in re.findall(r'href="/tag/([^"]+)"', txt):         # 页面里的新标签
                value = urllib.parse.unquote(raw)
                if value and value not in visited_tags and value not in tag_queue:
                    tag_queue.append(value)
            if f'?page={page+1}' not in txt:                 # 没有下一页链接 → 该标签结束
                break
            page += 1
            time.sleep(random.uniform(0.6, 1.2))
        gained = len(games) - before
        history.append({"tag": tag, "pages": page, "gained": gained, "games": len(games), "queue": len(tag_queue)})
        save_state(games, visited_tags, tag_queue, history)  # 每个标签处理完就存断点
        if len(history) % 25 == 0:
            print(f"  已爬标签 {len(visited_tags)} | 唯一游戏 {len(games)} | 队列 {len(tag_queue)}", flush=True)
        time.sleep(random.uniform(0.5, 0.9))
    return None


def main():
    # 命令行参数：标签上限 / 单标签最大页数 / 总时间上限（分钟）
    tag_cap = int(sys.argv[1]) if len(sys.argv) > 1 else 100000
    page_cap = int(sys.argv[2]) if len(sys.argv) > 2 else 15
    time_cap_min = float(sys.argv[3]) if len(sys.argv) > 3 else 600

    t0 = time.time()
    games, visited_tags, tag_queue, history, board_done = load_state()
    print(f"[启动] 已有游戏 {len(games)} | 已爬标签 {len(visited_tags)} | 队列 {len(tag_queue)}", flush=True)

    reason = None
    try:
        if not board_done:                                   # 第一次运行：先爬榜单
            games, tag_queue = crawl_boards(games, tag_queue, history)
            save_state(games, visited_tags, tag_queue, history)
        reason = crawl_tags(games, visited_tags, tag_queue, history, tag_cap, page_cap, time_cap_min, t0)
    except Exception as e:                                   # 任何未预期异常 → 记 ERROR 但仍保存进度
        print("异常:", repr(e), flush=True)
        reason = "ERROR"
    finally:
        save_state(games, visited_tags, tag_queue, history)
        with open(IDS_FILE, "w", encoding="utf-8") as f:
            json.dump(sorted(games), f, ensure_ascii=False)
        if reason is None:
            if interrupted:
                reason = "PAUSED"
            elif not tag_queue:
                reason = "DONE"
            else:
                reason = "TIMEOUT"
        mark_and_notify(reason, len(games), len(visited_tags), len(tag_queue))
        print(f"\n[结束] {reason} | 唯一游戏 {len(games)} | 已爬标签 {len(visited_tags)} | "
              f"队列剩余 {len(tag_queue)} | 耗时 {(time.time()-t0)/60:.1f} 分钟", flush=True)


if __name__ == "__main__":
    main()