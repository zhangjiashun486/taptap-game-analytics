# -*- coding: utf-8 -*-
"""
【文件作用】
    FastAPI 应用入口：创建 app 对象、注册路由、配置跨域。

【调用关系】
    run.py 启动后，uvicorn 会加载本文件中的 app：
        浏览器/Java -> http://127.0.0.1:8081/api/health
    本文件只负责“把接口组装起来”，具体接口逻辑放在 app/api/ 下的模块中。

【接口文档】
    FastAPI 自带交互式文档：启动后访问 http://127.0.0.1:8081/docs
"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

import config
from app.api import health, stats

# 创建 FastAPI 应用对象：标题/描述会显示在 /docs 文档页上
app = FastAPI(
    title="TapTap 数据服务（data_server）",
    description="负责数据采集、清洗、聚类分析等服务，仅供 base_server 调用",
    version="0.1.0",
)

# 跨域配置（CORS）：允许本地前端调试访问。
# 正式环境应把 allow_origins 收紧为具体域名（当前课程演示环境先放开）。
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# 注册路由：health.py 中的 /health，加上前缀 /api 后就是 /api/health
app.include_router(health.router, prefix="/api")   # /api/health
app.include_router(stats.router, prefix="/api")    # /api/stats/overview


@app.get("/", tags=["系统"])
def root() -> dict:
    """根路径：访问 http://127.0.0.1:8081/ 时给出提示，方便确认服务已启动"""
    return {
        "service": config.SERVICE_NAME,
        "message": "data_server 已启动",
        "health": "/api/health",
        "docs": "/docs",
    }