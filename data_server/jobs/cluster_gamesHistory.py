# -*- coding: utf-8 -*-
"""
TapTap 游戏 KMeans 聚类分析
输入: MySQL t_game / t_game_tag / t_tag
输出: t_cluster_run / t_cluster_result / t_cluster_meta
用法: python cluster_gamesHistory.py
密码: 环境变量 TAPTAP_DB_PASSWORD 或交互输入
"""
import os, sys, json, time, getpass
import numpy as np
import pymysql
from sklearn.preprocessing import StandardScaler
from sklearn.cluster import KMeans
from sklearn.metrics import silhouette_score

DB = dict(host=os.getenv("TAPTAP_DB_HOST", "192.168.100.101"),
          port=int(os.getenv("TAPTAP_DB_PORT", "3306")),
          user=os.getenv("TAPTAP_DB_USER", "root"),
          password=os.getenv("TAPTAP_DB_PASSWORD") or getpass.getpass("MySQL 密码: "),
          database="taptap_course", charset="utf8mb4", autocommit=False)

TOP_TAGS = 30
TAG_WEIGHT = 0.6
K_RANGE = range(2, 11)

def connect():
    return pymysql.connect(**DB)

def main():
    t0 = time.time()
    conn = connect(); cur = conn.cursor()

    # 1) 读数据
    cur.execute("SELECT game_id, title, rating_score, review_count, fans_count, reserve_count, wish_count FROM t_game")
    games = cur.fetchall()
    cur.execute("SELECT gt.game_id, t.tag_name FROM t_game_tag gt JOIN t_tag t ON gt.tag_id = t.tag_id")
    rows = cur.fetchall()
    tag_by_game = {}
    tag_freq = {}
    for gid, tag in rows:
        tag_by_game.setdefault(gid, []).append(tag)
        tag_freq[tag] = tag_freq.get(tag, 0) + 1
    top_tags = [t for t, _ in sorted(tag_freq.items(), key=lambda x: -x[1])[:TOP_TAGS]]
    tag_index = {t: i for i, t in enumerate(top_tags)}
    print(f"游戏 {len(games)} 款 | 标签关系 {len(rows)} 条 | Top{TOP_TAGS} 标签参与聚类", flush=True)

    # 2) 特征工程
    rated, unrated = [], []
    for gid, title, rating, rc, fc, res, wish in games:
        (rated if rating is not None else unrated).append((gid, title, rating, rc, fc, res, wish))
    print(f"有评分参与聚类: {len(rated)} 款；无评分(单独标记): {len(unrated)} 款", flush=True)

    def tag_vec(gid):
        v = np.zeros(len(top_tags))
        for t in tag_by_game.get(gid, []):
            if t in tag_index:
                v[tag_index[t]] = 1.0
        return v

    num = np.array([[float(rating), np.log1p(rc or 0), np.log1p(fc or 0), np.log1p(res or 0),
                     np.log1p(wish or 0), np.log1p(rc or 0) - np.log1p(fc or 0)] for _, _, rating, rc, fc, res, wish in rated])
    num_scaled = StandardScaler().fit_transform(num)
    tag_mat = np.array([tag_vec(g[0]) for g in rated]) * TAG_WEIGHT
    X = np.hstack([num_scaled, tag_mat])
    print(f"特征矩阵: {X.shape[0]} x {X.shape[1]}（6 数值特征 + {TOP_TAGS} 标签特征）", flush=True)

    # 3) 选择 K（肘部 + 轮廓）
    metrics = []
    for k in K_RANGE:
        km = KMeans(n_clusters=k, random_state=42, n_init=10)
        labels = km.fit_predict(X)
        sil = silhouette_score(X, labels, sample_size=min(5000, len(X)), random_state=42)
        metrics.append({"k": k, "sse": float(km.inertia_), "silhouette": float(sil)})
        print(f"  K={k}: SSE={km.inertia_:.1f} 轮廓系数={sil:.4f}", flush=True)
    best = max(metrics, key=lambda m: m["silhouette"])
    K = best["k"]
    print(f"\n选择 K={K}（轮廓系数最高 {best['silhouette']:.4f}）", flush=True)

    # 4) 正式聚类
    km = KMeans(n_clusters=K, random_state=42, n_init=10)
    labels = km.fit_predict(X)
    sil = best["silhouette"]

    # 5) 簇命名
    stats = []
    for c in range(K):
        idx = np.where(labels == c)[0]
        g = [rated[i] for i in idx]
        stats.append({
            "c": c, "size": len(idx),
            "rating": float(np.mean([x[2] for x in g])),
            "fans": float(np.mean([np.log1p(x[4] or 0) for x in g])),
            "review": float(np.mean([np.log1p(x[3] or 0) for x in g])),
            "reserve": float(np.mean([np.log1p(x[5] or 0) for x in g])),
        })
    def norm(key):
        vals = [s[key] for s in stats]
        lo, hi = min(vals), max(vals)
        return {s["c"]: (0.5 if hi == lo else (s[key] - lo) / (hi - lo)) for s in stats}
    nr, nf, nv, ns = norm("rating"), norm("fans"), norm("review"), norm("reserve")
    names = {}
    used = {}
    for s in stats:
        c = s["c"]
        if ns[c] >= 0.85 and ns[c] == max(ns.values()) and s["reserve"] > 1.0:
            name = "预约爆款"
        elif nf[c] == max(nf.values()):
            name = "热门大作"
        elif nr[c] == max(nr.values()):
            name = "口碑神作" if nf[c] >= 0.5 else "小众佳作"
        elif nv[c] == max(nv.values()):
            name = "讨论热门"
        elif s["rating"] < 6.5:
            name = "争议/待改进"
        else:
            name = "普通长尾"
        used[name] = used.get(name, 0) + 1
        names[c] = name if used[name] == 1 else f"{name}{used[name]}"

    # 6) 写库
    cur.execute("""INSERT INTO t_cluster_run(algorithm,k_value,feature_info,silhouette,sse,params_json)
                   VALUES(%s,%s,%s,%s,%s,%s)""",
                ("KMeans", K, f"评分+log评论/关注/预约/愿望单+评论关注比+Top{TOP_TAGS}标签(权重{TAG_WEIGHT})",
                 sil, best["sse"], json.dumps(metrics, ensure_ascii=False)))
    run_id = cur.lastrowid

    result_rows = [(run_id, rated[i][0], int(labels[i])) for i in range(len(rated))]
    result_rows += [(run_id, g[0], -1) for g in unrated]
    cur.executemany("INSERT INTO t_cluster_result(run_id, game_id, cluster_id) VALUES(%s,%s,%s)", result_rows)

    for s in stats:
        c = s["c"]
        idx = np.where(labels == c)[0]
        gids = [rated[i][0] for i in idx]
        # 关键词：簇内高频标签
        cnt = {}
        for gid in gids:
            for t in tag_by_game.get(gid, []):
                cnt[t] = cnt.get(t, 0) + 1
        keywords = "、".join([t for t, _ in sorted(cnt.items(), key=lambda x: -x[1])[:5]])
        centroid = {"评分均值": round(s["rating"], 2), "关注均值": round(float(np.expm1(s["fans"])), 0),
                    "评论均值": round(float(np.expm1(s["review"])), 0), "预约均值": round(float(np.expm1(s["reserve"])), 0)}
        samples = [{"game_id": gid, "title": next(x[1] for x in rated if x[0] == gid)}
                   for gid in sorted(gids, key=lambda x: -next(g[4] for g in rated if g[0] == x))[:3]]
        cur.execute("""INSERT INTO t_cluster_meta(run_id,cluster_id,cluster_name,size,keywords,centroid,samples)
                       VALUES(%s,%s,%s,%s,%s,%s,%s)""",
                    (run_id, c, names[c], s["size"], keywords, json.dumps(centroid, ensure_ascii=False),
                     json.dumps(samples, ensure_ascii=False)))
    cur.execute("""INSERT INTO t_cluster_meta(run_id,cluster_id,cluster_name,size,keywords,centroid,samples)
                   VALUES(%s,%s,%s,%s,%s,%s,%s)""",
                (run_id, -1, "未评分/下架", len(unrated), "", json.dumps({}, ensure_ascii=False),
                 json.dumps([{"game_id": g[0], "title": g[1]} for g in unrated[:3]], ensure_ascii=False)))
    conn.commit()

    print(f"\n===== 聚类结果（run_id={run_id}）=====", flush=True)
    for s in sorted(stats, key=lambda x: -x["size"]):
        c = s["c"]
        examples = [next(x[1] for x in rated if x[0] == gid) for gid in
                    sorted([rated[i][0] for i in np.where(labels == c)[0]], key=lambda x: -next(g[4] for g in rated if g[0] == x))[:3]]
        print(f"  簇{c} {names[c]}: {s['size']}款 | 均分{s['rating']:.2f} | 代表: {'、'.join(examples)}", flush=True)
    print(f"  簇-1 未评分/下架: {len(unrated)}款", flush=True)
    print(f"\n耗时 {time.time()-t0:.1f}s | 结果已写入 t_cluster_run/result/meta", flush=True)
    cur.close(); conn.close()

if __name__ == "__main__":
    main()