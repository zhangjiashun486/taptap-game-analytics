# -*- coding: utf-8 -*-
"""
TapTap 游戏数据导入 MySQL
用法: python import_games.py [limit]
   limit 可选：只导入前 N 条（测试用）；不填=全量
密码来源: 环境变量 TAPTAP_DB_PASSWORD，或运行时交互输入
"""
import json, os, io, sys, time, getpass
from datetime import datetime
import pymysql

BASE = os.getenv("TAPTAP_DATA_ROOT", os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))
JSONL = os.path.join(BASE, "data", "raw", "taptap_details.jsonl")#这个 JSONL 就是之前爬虫保存下来的游戏详情数据。
#os.getenv("变量名", "默认值") 的意思是：
# 先读取环境变量
#
# 如果环境变量存在
# → 使用环境变量的值
#
# 如果环境变量不存在
# → 使用默认值
HOST = os.getenv("TAPTAP_DB_HOST", "192.168.100.101")
PORT = int(os.getenv("TAPTAP_DB_PORT", "3306"))
USER = os.getenv("TAPTAP_DB_USER", "root")
DB   = os.getenv("TAPTAP_DB_NAME", "taptap_course")

def parse_score(v):
    try:
        f = float(v)
        return f if f > 0 else None
    except Exception:
        return None

def parse_ts(v):
    try:
        return datetime.fromtimestamp(int(v)).strftime("%Y-%m-%d %H:%M:%S")
    # datetime.fromtimestamp(...) 把 Unix 时间戳转换成 Python 的日期时间对象   1743465600 ->  2025-04-01 08:00:00
    #   strftime("%Y-%m-%d %H:%M:%S") 把日期对象格式化成字符串。         ->"2025-04-01 08:00:00"
    except Exception:
        return None

UPSERT = """
INSERT INTO t_game
(game_id, title, rating_score, latest_score, review_count, fans_count, hits_total,
 wish_count, reserve_count, price, platform, released_time, developer, intro, icon_url)
VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
ON DUPLICATE KEY UPDATE
 title=VALUES(title), rating_score=VALUES(rating_score), latest_score=VALUES(latest_score),
 review_count=VALUES(review_count), fans_count=VALUES(fans_count), hits_total=VALUES(hits_total),
 wish_count=VALUES(wish_count), reserve_count=VALUES(reserve_count), price=VALUES(price),
 platform=VALUES(platform), released_time=VALUES(released_time), developer=VALUES(developer),
 intro=VALUES(intro), icon_url=VALUES(icon_url)
"""
# 如果 game_id 不存在 -> INSERT 插入
# 如果 game_id 已经存在 -> UPDATE 更新
def main():
    limit = int(sys.argv[1]) if len(sys.argv) > 1 else 0
    # 如果传了参数：
    #     把第一个参数转成整数，赋给 limit
    #
    # 如果没传参数：
    #     limit = 0

    t0 = time.time()
    # time.time() 返回当前时间戳   记录程序开始的时间


    pwd = os.getenv("TAPTAP_DB_PASSWORD") or getpass.getpass("MySQL 密码: ")
    # 优先从环境变量读密码
    # 读不到再让用户输入
    #getpath.getpath()手动输入密码的方法： 但用户输入的内容不会显示在屏幕上。

    conn = pymysql.connect(host=HOST, port=PORT, user=USER, password=pwd, database=DB,
                           charset="utf8mb4", autocommit=False)
    cur = conn.cursor()

    games, links, tag_names, skipped, total = [], [], set(), 0, 0
    with io.open(JSONL, encoding="utf-8") as f:
        for line in f:
            if not line.strip():
                continue
            rec = json.loads(line)
            app = rec.get("app") or {}
            gid = app.get("id") or rec.get("id")
            if not gid:
                skipped += 1
                continue
            total += 1
            stat = app.get("stat") or {}
            rating = stat.get("rating") or {}
            devs = app.get("developers") or []
            dev = None
            for d in devs:
                if d.get("label") == "厂商":
                    dev = d.get("name"); break
            if not dev and devs:
                dev = devs[0].get("name")
            intro = ((app.get("description") or {}).get("text") or "").replace("<br/>", "\n").replace("<br>", "\n")
            icon = (app.get("icon") or {}).get("original_url") or (app.get("icon") or {}).get("url")
            platform = ((app.get("platform_info") or {}).get("current_platform") or {}).get("key")
            games.append((gid, (app.get("title") or "")[:255], parse_score(rating.get("score")),
                          parse_score(rating.get("latest_score")), int(stat.get("review_count") or 0),
                          int(stat.get("fans_count") or 0), int(stat.get("hits_total") or 0),
                          int(stat.get("wish_count") or 0), int(stat.get("reserve_count") or 0),
                          None, platform, parse_ts(app.get("released_time")), dev, intro, icon))
            for t in app.get("tags") or []:
                name = (t.get("value") or "").strip()
                if name:
                    tag_names.add(name)
                    links.append((gid, name))
            if limit and total >= limit:
                break

    print(f"解析完成: {len(games)} 款游戏, {len(tag_names)} 个标签, {len(links)} 条游戏-标签关系, 跳过 {skipped}", flush=True)

    # 1) 游戏主表（分批 upsert）
    B = 200
    for i in range(0, len(games), B):
        cur.executemany(UPSERT, games[i:i+B])
        conn.commit()
        print(f"  游戏入库 {min(i+B, len(games))}/{len(games)}", flush=True)

    # 2) 标签表
    if tag_names:
        cur.executemany("INSERT IGNORE INTO t_tag(tag_name) VALUES (%s)", [(n,) for n in tag_names])
        conn.commit()
    cur.execute("SELECT tag_id, tag_name FROM t_tag")
    tag_map = {name: tid for tid, name in cur.fetchall()}

    # 3) 游戏-标签关联
    rel = {(gid, tag_map[n]) for gid, n in links if n in tag_map}
    rel = list(rel)
    for i in range(0, len(rel), 500):
        cur.executemany("INSERT IGNORE INTO t_game_tag(game_id, tag_id) VALUES (%s,%s)", rel[i:i+500])
        conn.commit()
    print(f"  标签关联入库 {len(rel)} 条", flush=True)

    # 4) 采集日志
    cur.execute(
        "INSERT INTO t_crawl_log(batch_id, task_type, status, total, success, failed, started_at, finished_at, message) "
        "VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s)",
        (f"import-{datetime.now().strftime('%Y%m%d%H%M')}", "详情导入", "DONE", total, len(games), skipped,
         datetime.fromtimestamp(t0), datetime.now(), "JSONL 导入 MySQL"))
    conn.commit()

    cur.execute("SELECT COUNT(*) FROM t_game"); g = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM t_tag"); tg = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM t_game_tag"); gt = cur.fetchone()[0]
    print(f"\n数据库现状: t_game={g}, t_tag={tg}, t_game_tag={gt}", flush=True)
    print(f"耗时: {time.time()-t0:.1f}s", flush=True)
    cur.close(); conn.close()

if __name__ == "__main__":
    main()