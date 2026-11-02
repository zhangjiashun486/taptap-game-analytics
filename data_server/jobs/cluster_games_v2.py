# -*- coding: utf-8 -*-
"""
【文件作用】
    TapTap 游戏 KMeans 聚类分析（最终版 v2，对应数据库 run_id=2）。

【做什么】
    1. 从 MySQL(t_game / t_game_tag) 读取 10,219 款游戏及其标签；
    2. 对有评分的 8,340 款游戏做特征工程（6 个数值特征）；
    3. 用肘部法 + 轮廓系数评估不同 K，最终业务可解释性选 K=5；
    4. 运行 KMeans 得到每款游戏所属的簇；
    5. 按簇的特征给簇命名（热门大作/口碑神作/小众佳作/预约爆款/争议待改进）；
    6. 结果写入 t_cluster_run / t_cluster_result / t_cluster_meta 三张表。

【输入】MySQL taptap_course 库（t_game、t_game_tag、t_tag）
【输出】t_cluster_run(运行参数) / t_cluster_result(游戏->簇) / t_cluster_meta(簇画像)
【依赖】pymysql、numpy、scikit-learn；密码从环境变量 TAPTAP_DB_PASSWORD 读取（或运行时输入）

【运行方式】
    $env:TAPTAP_DB_PASSWORD = "你的密码"
    python cluster_games_v2.py
    （把文件顶部 DRY_RUN 改为 1，可只演示不写库；改回 0 为正式写库）
"""
import os, json, time, getpass, sys
import numpy as np
import pymysql
from sklearn.preprocessing import StandardScaler   # 标准化：把不同量纲的特征拉到同一尺度
from sklearn.cluster import KMeans                 # 聚类算法：KMeans
from sklearn.metrics import silhouette_score       # 评估指标：轮廓系数

# ---------------- 数据库连接配置 ----------------
# 说明：敏感信息不写死在代码里，优先读环境变量；本地调试时才交互输入密码
DB = dict(host="192.168.100.101",                   # 虚拟机 MySQL 地址
          port=3306,                                # 端口
          user="root",                              # 用户名
          password=os.getenv("TAPTAP_DB_PASSWORD") or getpass.getpass("MySQL 密码: "),  # 密码：环境变量优先
          database="taptap_course",                 # 数据库名
          charset="utf8mb4",                        # 支持中文/emoji
          autocommit=False)                         # 手动提交，保证批量写入的一致性

# ==================== 运行开关（平时只需要改这里） ====================
# DRY_RUN = 0：正常执行，聚类结果写入数据库（默认）
# DRY_RUN = 1：演示/测试模式，只跑算法并打印结果，不写数据库
# 说明：命令行加 --dry-run 也能临时开启（不改代码，可选用法）
DRY_RUN = 1
if "--dry-run" in sys.argv:        # 命令行参数优先
    DRY_RUN = 1

K = 5   # 最终簇数（下文会先用肘部法/轮廓系数评估 2~8，再结合业务可解释性确定 5）


def main():
    t0 = time.time()                                # 记录开始时间，用于统计耗时

    # ========== 1. 读取数据 ==========
    conn = pymysql.connect(**DB)                    # 建立数据库连接
    cur = conn.cursor()                             # 创建游标（执行 SQL 用）

    # 读取游戏主表：6 个原始字段（评分可能为 NULL，代表未评分/下架游戏）
    cur.execute("SELECT game_id, title, rating_score, review_count, fans_count, reserve_count, wish_count FROM t_game")
    games = cur.fetchall()                          # 10,219 条游戏记录

    # 读取“游戏-标签”关系，用于后续统计每个簇的关键词、代表游戏
    cur.execute("SELECT gt.game_id, t.tag_name FROM t_game_tag gt JOIN t_tag t ON gt.tag_id=t.tag_id")
    tag_by_game = {}                                # {game_id: [标签1, 标签2, ...]}
    for gid, tag in cur.fetchall():                 # 遍历 42,629 条关系
        tag_by_game.setdefault(gid, []).append(tag) # 按游戏聚合成标签列表

    # 拆成两组：有评分的参与聚类；无评分的单独标记为“未评分/下架”
    rated = [g for g in games if g[2] is not None]  # g[2] = rating_score
    unrated = [g for g in games if g[2] is None]

    # ========== 2. 特征工程 ==========
    # 每款游戏 -> 6 维特征向量（“游戏在虚拟空间中的坐标”）
    #   [0] 评分                ：0~10，代表质量
    #   [1] log1p(评论数)       ：讨论热度（log1p 压缩大数值，避免千万级数字喧宾夺主）
    #   [2] log1p(关注数)       ：人气规模
    #   [3] log1p(预约数)       ：未上线游戏的期待值
    #   [4] log1p(愿望单数)     ：想玩程度
    #   [5] log1p(评论数)-log1p(关注数)：评论/关注比，代表“讨论活跃度/粘性”
    X = np.array([[float(rating),                            # 评分
                   np.log1p(rc or 0),                        # 评论数取 log（None/0 归一为 0）
                   np.log1p(fc or 0),                        # 关注数取 log
                   np.log1p(res or 0),                       # 预约数取 log
                   np.log1p(wish or 0),                      # 愿望单取 log
                   np.log1p(rc or 0) - np.log1p(fc or 0)]    # 评论/关注比
                  for _, _, rating, rc, fc, res, wish in rated])  # 只对有评分的游戏造特征

    # 标准化（Z-Score）：每个特征减去均值再除以标准差
    # 目的：让 6 个特征在同一尺度上，计算距离时不会因量纲不同而失真
    X = StandardScaler().fit_transform(X)

    print(f"参与聚类 {len(rated)} 款 | 特征 {X.shape[1]} 维 | K={K}", flush=True)

    # ========== 3. 评估不同 K（肘部法 + 轮廓系数） ==========
    metrics = []                                    # 保存每个 K 的评估结果，最终写入 params_json
    for k in range(2, 9):                           # 测试 K=2~8
        km = KMeans(n_clusters=k, random_state=42, n_init=10).fit(X)   # 训练：random_state 保证可复现
        sil = silhouette_score(X, km.labels_, sample_size=min(5000, len(X)), random_state=42)  # 轮廓系数（抽样 5000 加速）
        metrics.append({"k": k, "sse": round(float(km.inertia_), 1), "silhouette": round(float(sil), 4)})
        print(f"  K={k}: SSE={km.inertia_:.0f} 轮廓={sil:.4f}", flush=True)   # SSE=簇内误差平方和，越小越紧凑

    # ========== 4. 正式聚类（K=5） ==========
    km = KMeans(n_clusters=K, random_state=42, n_init=10).fit(X)   # n_init=10：跑 10 次取最优，降低初始中心随机性影响
    labels = km.labels_                                            # 每款游戏所属簇编号（0~4）
    sil = silhouette_score(X, labels, sample_size=min(5000, len(X)), random_state=42)  # 最终轮廓系数
    sse = float(km.inertia_)                                       # 最终 SSE

    # ========== 5. 计算每个簇的画像并命名 ==========
    # 统计每个簇的规模与特征均值（用于命名和写入 t_cluster_meta）
    stats = []
    for c in range(K):                              # 遍历 0~4 号簇
        idx = np.where(labels == c)[0]              # 属于当前簇的游戏下标
        g = [rated[i] for i in idx]                 # 对应游戏记录
        stats.append({"c": c, "size": len(idx),
                      "rating": float(np.mean([x[2] for x in g])),                  # 簇内平均评分
                      "fans": float(np.mean([np.log1p(x[4] or 0) for x in g])),     # 簇内平均 log 关注
                      "review": float(np.mean([np.log1p(x[3] or 0) for x in g])),   # 簇内平均 log 评论
                      "reserve": float(np.mean([np.log1p(x[5] or 0) for x in g]))}) # 簇内平均 log 预约

    # 把每个指标在 5 个簇之间做排名（0~1），用于判断某簇在哪个维度最突出
    def rank_map(key):
        order = sorted(stats, key=lambda s: s[key])                     # 按指标升序排列
        return {s["c"]: i / max(1, len(stats) - 1) for i, s in enumerate(order)}  # 最小=0，最大=1

    rr, rf, rv, rs = rank_map("rating"), rank_map("fans"), rank_map("review"), rank_map("reserve")
    # rr=评分类排名；rf=关注类排名；rv=评论类排名；rs=预约类排名

    names, used = {}, {}                            # names: 簇->名称；used: 名称去重计数
    for s in sorted(stats, key=lambda x: -x["size"]):     # 按规模从大到小命名
        c = s["c"]
        # ---- 命名规则（按优先级判断，规则简单可解释） ----
        if rf[c] == 1.0:                            # 关注度排名第一 -> 热门大作
            name = "热门大作"
        elif rs[c] == 1.0 and s["reserve"] > 3:     # 预约排名第一且预约量明显 -> 预约爆款
            name = "预约爆款"
        elif rr[c] >= 0.75 and rf[c] >= 0.5:        # 评分靠前且人气不低 -> 口碑神作
            name = "口碑神作"
        elif rr[c] >= 0.5 and rf[c] < 0.5:          # 评分不错但人气偏低 -> 小众佳作
            name = "小众佳作"
        elif rv[c] == 1.0:                          # 评论度排名第一 -> 讨论热门
            name = "讨论热门"
        elif s["rating"] < 6.8:                     # 平均分偏低 -> 争议/待改进
            name = "争议/待改进"
        else:                                       # 其余 -> 普通长尾
            name = "普通长尾"
        used[name] = used.get(name, 0) + 1          # 记录该名称已用次数
        names[c] = name if used[name] == 1 else f"{name}{used[name]}"   # 重名时追加序号，保证唯一

    # ---- 运行开关：DRY_RUN=1 时只演示、不写数据库（见文件顶部）----
    if DRY_RUN == 1:
        print("\n[演示模式] DRY_RUN=1，跳过数据库写入，仅展示聚类结果：")
        for s in sorted(stats, key=lambda x: -x["size"]):
            print(f"  簇{s['c']} {names[s['c']]}: {s['size']}款 | 平均分{s['rating']:.2f}")
        print(f"  未评分/下架: {len(unrated)}款")
        cur.close(); conn.close()
        return

    # ========== 6. 写数据库 ==========

    # 6.1 写入一次聚类运行记录（t_cluster_run），拿到 run_id
    cur.execute("""INSERT INTO t_cluster_run(algorithm,k_value,feature_info,silhouette,sse,params_json)
                   VALUES(%s,%s,%s,%s,%s,%s)""",
                ("KMeans", K, "评分+log(评论/关注/预约/愿望单)+评论关注比（6维数值特征）",
                 float(sil), sse, json.dumps(metrics, ensure_ascii=False)))
    run_id = cur.lastrowid                         # 本次运行的唯一编号（后续表都引用它）

    # 6.2 写入每款游戏 -> 簇 的映射（t_cluster_result）；无评分游戏记为 -1
    rows = [(run_id, rated[i][0], int(labels[i])) for i in range(len(rated))]   # 8,340 款有评分
    rows += [(run_id, g[0], -1) for g in unrated]                               # 1,879 款无评分
    cur.executemany("INSERT INTO t_cluster_result(run_id,game_id,cluster_id) VALUES(%s,%s,%s)", rows)

    # 6.3 写入每个簇的画像（t_cluster_meta）：名称、规模、关键词、簇中心、代表游戏
    for s in stats:
        c = s["c"]                                  # 当前簇编号
        idx = np.where(labels == c)[0]              # 簇内游戏下标
        gids = [rated[i][0] for i in idx]           # 簇内游戏 ID

        # 关键词：统计簇内出现次数最多的 5 个标签
        cnt = {}
        for gid in gids:
            for t in tag_by_game.get(gid, []):
                cnt[t] = cnt.get(t, 0) + 1
        keywords = "、".join([t for t, _ in sorted(cnt.items(), key=lambda x: -x[1])[:5]])[:400]

        # 簇中心（用 expm1 把 log 值还原成原始量级，方便人看）
        centroid = {"评分均值": round(s["rating"], 2),
                    "关注均值": int(np.expm1(s["fans"])),
                    "评论均值": int(np.expm1(s["review"])),
                    "预约均值": int(np.expm1(s["reserve"]))}

        # 代表游戏：取簇内关注数最高的 3 款
        top = sorted(gids, key=lambda x: -next(g[4] for g in rated if g[0] == x))[:3]
        samples = [{"game_id": gid, "title": next(g[1] for g in rated if g[0] == gid)} for gid in top]

        cur.execute("""INSERT INTO t_cluster_meta(run_id,cluster_id,cluster_name,size,keywords,centroid,samples)
                       VALUES(%s,%s,%s,%s,%s,%s,%s)""",
                    (run_id, c, names[c], s["size"], keywords,
                     json.dumps(centroid, ensure_ascii=False), json.dumps(samples, ensure_ascii=False)))

    # 6.4 未评分/下架游戏单独写一条“簇 -1”，保证每款游戏都有归属
    cur.execute("""INSERT INTO t_cluster_meta(run_id,cluster_id,cluster_name,size,keywords,centroid,samples)
                   VALUES(%s,%s,%s,%s,%s,%s,%s)""",
                (run_id, -1, "未评分/下架", len(unrated), "",
                 json.dumps({}, ensure_ascii=False),
                 json.dumps([{"game_id": g[0], "title": g[1]} for g in unrated[:3]], ensure_ascii=False)))
    conn.commit()                                   # 统一提交事务，数据落库

    # ========== 7. 控制台输出结果摘要 ==========
    print(f"\n===== 新聚类结果 run_id={run_id}, K={K}, 轮廓={sil:.4f} =====", flush=True)
    for s in sorted(stats, key=lambda x: -x["size"]):   # 按规模从大到小打印
        c = s["c"]
        # 取该簇关注最高的 3 款作为代表游戏
        top = sorted([rated[i][0] for i in np.where(labels == c)[0]],
                     key=lambda x: -next(g[4] for g in rated if g[0] == x))[:3]
        ex = [next(g[1] for g in rated if g[0] == gid) for gid in top]
        print(f"  簇{c} {names[c]}: {s['size']}款 | 均分{s['rating']:.2f} | 代表: {'、'.join(ex)}", flush=True)
    print(f"  簇-1 未评分/下架: {len(unrated)}款", flush=True)
    print(f"耗时 {time.time()-t0:.1f}s", flush=True)

    cur.close(); conn.close()                       # 释放数据库资源


# 程序入口：只有直接运行本文件时才执行 main()，被 import 时不会执行
main()