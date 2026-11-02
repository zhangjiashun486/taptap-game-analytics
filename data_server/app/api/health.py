# -*- coding: utf-8 -*-
"""
【文件作用】
    系统探活接口：GET /api/health
    base_server（Java）会定时调用它，判断 Python 数据服务是否存活。

【为什么需要】
    项目规则要求 data_server 必须提供 /api/health；
    服务“活着但内部出问题”时，Java 侧可以快速发现并记录 traceId。
"""
from datetime import datetime

from fastapi import APIRouter
import config

# APIRouter：路由分组器，相当于“这一组接口都挂在这个 router 上”
router = APIRouter(tags=["系统"])


@router.get("/health")          # 装饰器：声明“当访问 GET /health 时执行下面的函数”
def health() -> dict:
    """返回服务状态；FastAPI 会自动把 dict 转成 JSON 响应"""
    return {
        "status": "ok",                                              # 固定 ok，表示服务正常
        "service": config.SERVICE_NAME,                              # 服务名：data_server
        "time": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),        # 服务器当前时间
        "version": "0.1.0",                                          # 服务版本
    }