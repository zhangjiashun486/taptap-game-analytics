# -*- coding: utf-8 -*-
"""
【文件作用】
    模拟用户行为数据（仅用于课程演示）。
    真实情况下，t_behavior 由用户在用户端点击"收藏/玩过/评分"等产生；
    本脚本用程序模拟一批用户行为，方便推荐接口和管理端图表有数据可看。

【做什么】
    1. 清理旧的演示用户（用户名以 demo_user_ 开头）及其行为记录；
    2. 创建 N 个演示用户（默认 20 个，密码统一 123456，BCrypt 加密）；
    3. 每个用户随机挑选 8~15 款游戏，生成：
       - 浏览 VIEW：每款游戏 1~3 条
       - 收藏/点赞/玩过/评分：每款游戏随机 1~2 种（评分 4~10 分，带权重）
    4. 打印统计并写报告 JSON。

【安全】只删除/创建用户名以 demo_user_ 开头的演示用户，不影响真实用户。
【运行】
    $env:TAPTAP_DB_PASSWORD = "你的密码"
    python simulate_behavior.py
"""
import json
import os
import random
import sys
from collections import Counter
from pathlib import Path

import bcrypt
import pymysql

BASE = Path(__file__).resolve().parents[3]          # 项目根 HunuProjec2
sys.path.insert(0, str(Path(__file__).resolve().parents[2]))   # data_server（为了 import config）
import config                                        # noqa: E402

USERS = 20                # 演示用户数量
PASSWORD = "123456"       # 演示用户密码（和 login 接口配合使用）
GAMES_PER_USER = (8, 15)  # 每个用户交互的游戏数量范围
SEED = 42                 # 固定随机种子，保证结果可复现

random.seed(SEED)


def connect():
    """连接 MySQL（配置来自 config.py：环境变量 TAPTAP_DB_PASSWORD）"""
    return pymysql.connect(**config.db_config())


def cleanup_demo_users(cur):
    """删除旧的演示用户及其行为记录（只匹配 demo_user_% ，不动真实用户）"""
    cur.execute("SELECT id FROM t_user WHERE username LIKE 'demo_user_%'")
    ids = [row[0] for row in cur.fetchall()]
    if ids:
        placeholders = ",".join(["%s"] * len(ids))
        cur.execute(f"DELETE FROM t_behavior WHERE user_id IN ({placeholders})", ids)
        cur.execute(f"DELETE FROM t_user WHERE id IN ({placeholders})", ids)
    return len(ids)


def main():
    conn = connect()
    cur = conn.cursor()

    removed = cleanup_demo_users(cur)
    print(f"清理旧演示用户: {removed} 个")

    # 1) 选候选游戏：有评分、且评论数 >= 1000（避免"没人玩的满分游戏"）
    cur.execute("SELECT game_id FROM t_game WHERE rating_score IS NOT NULL AND review_count >= 1000")
    games = [row[0] for row in cur.fetchall()]
    print(f"候选游戏: {len(games)} 款")
    if not games:
        print("没有候选游戏，退出"); return

    # 2) 创建演示用户（同一个 BCrypt 哈希可复用，密码都是 123456）
    password_hash = bcrypt.hashpw(PASSWORD.encode(), bcrypt.gensalt(prefix=b"2a")).decode()
    user_ids = []
    for i in range(1, USERS + 1):
        username = f"demo_user_{i:02d}"
        nickname = f"演示用户{i:02d}"
        cur.execute("INSERT INTO t_user(username, password_hash, nickname, role, status, created_at, updated_at) "
                    "VALUES(%s, %s, %s, 'USER', 1, NOW(), NOW())",
                    (username, password_hash, nickname))
        user_ids.append(cur.lastrowid)
    conn.commit()
    print(f"创建演示用户: {len(user_ids)} 个（密码都是 {PASSWORD}）")

    # 3) 为每个用户生成行为
    counts = Counter()
    for uid in user_ids:
        picked = random.sample(games, random.randint(*GAMES_PER_USER))
        for gid in picked:
            # 浏览：1~3 条
            for _ in range(random.randint(1, 3)):
                cur.execute("INSERT INTO t_behavior(user_id, game_id, behavior_type, score, created_at) "
                            "VALUES(%s, %s, 'VIEW', NULL, NOW())", (uid, gid))
                counts["VIEW"] += 1
            # 正向行为：随机 1~2 种（不重复）
            for behavior in random.sample(["FAVORITE", "LIKE", "PLAYED", "RATE"], random.randint(1, 2)):
                score = None
                if behavior == "RATE":
                    score = random.choices([10, 9, 8, 7, 6, 5, 4], weights=[15, 25, 25, 15, 10, 5, 5])[0]
                cur.execute("INSERT INTO t_behavior(user_id, game_id, behavior_type, score, created_at) "
                            "VALUES(%s, %s, %s, %s, NOW())", (uid, gid, behavior, score))
                counts[behavior] += 1
        conn.commit()   # 每个用户提交一次

    # 4) 汇总
    total = sum(counts.values())
    print(f"\n共写入行为记录 {total} 条：")
    for behavior, n in counts.most_common():
        print(f"  {behavior}: {n}")

    report = {"users": len(user_ids), "total_behaviors": total, "by_type": dict(counts),
              "password": PASSWORD, "seed": SEED}
    out = BASE / "data" / "output" / "simulated_behavior_report.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n报告已保存: {out}")

    cur.close(); conn.close()


if __name__ == "__main__":
    main()