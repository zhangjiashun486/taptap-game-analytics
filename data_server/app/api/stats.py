# -*- coding: utf-8 -*-
"""
【文件作用】
    统计接口：GET /api/stats/overview
    返回数据库中的真实统计：游戏数、标签数、标签关系数、最近一次聚类信息。

【谁来调用】
    base_server（Java）调用本接口，再包装成 {code,message,data} 返回给前端；
    前端（Vue）不允许直接访问本服务（项目规则）。

【返回示例】
    {
      "games": 10219,
      "tags": 607,
      "game_tags": 42629,
      "latest_cluster_run": 2,
      "clusters": [{"cluster_id": 0, "name": "热门大作", "size": 1581}, ...]
    }
"""
import json

import pymysql
from fastapi import APIRouter, HTTPException

import config

router = APIRouter(tags=["统计"])          # 路由组：统计类接口


@router.get("/stats/overview")             # 访问 GET /api/stats/overview 时执行下面的函数
def stats_overview() -> dict:
    """查询数据库统计信息；数据库不可用时返回 500 错误说明"""
    try:
        conn = pymysql.connect(**config.db_config())   # 用配置中心的参数连接 MySQL
        cur = conn.cursor()

        cur.execute("SELECT COUNT(*) FROM t_game")            # 游戏总数
        games = cur.fetchone()[0]
        cur.execute("SELECT COUNT(*) FROM t_tag")             # 标签总数
        tags = cur.fetchone()[0]
        cur.execute("SELECT COUNT(*) FROM t_game_tag")        # 游戏-标签关系数
        game_tags = cur.fetchone()[0]

        cur.execute("SELECT run_id FROM t_cluster_run ORDER BY run_id DESC LIMIT 1")
        row = cur.fetchone()
        latest_run = row[0] if row else None                  # 最近一次聚类 run_id

        clusters = []
        if latest_run is not None:
            cur.execute("""SELECT cluster_id, cluster_name, size
                           FROM t_cluster_meta WHERE run_id=%s ORDER BY size DESC""", (latest_run,))
            clusters = [{"cluster_id": cid, "name": name, "size": size} for cid, name, size in cur.fetchall()]

        cur.close(); conn.close()
        return {"games": games, "tags": tags, "game_tags": game_tags,
                "latest_cluster_run": latest_run, "clusters": clusters}
    except Exception as e:
        # 数据库连不上/查询失败：返回 500，并把原因告诉调用方（Java 侧会记录日志）
        raise HTTPException(status_code=500, detail=f"数据库查询失败: {e}")