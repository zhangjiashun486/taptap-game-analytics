# -*- coding: utf-8 -*-
"""
【文件作用】
    data_server 的启动入口：使用 uvicorn 运行 FastAPI 应用。

【运行方式】
    cd <项目目录>/data_server
    .venv/Scripts/python.exe run.py
    启动后访问：
        http://127.0.0.1:8081/api/health   健康检查
        http://127.0.0.1:8081/docs        接口文档（Swagger UI）
"""
import uvicorn

import config

if __name__ == "__main__":
    # app.main:app -> 加载 app/main.py 里的 app 对象
    uvicorn.run(
        "app.main:app",
        host=config.HOST,          # 监听地址（默认 0.0.0.0）
        port=config.PORT,          # 端口（项目规则约定 8081）
        reload=config.DEBUG,       # 调试模式才热重载；正式运行关闭
        access_log=False,          # 关闭逐条访问日志，避免刷屏（项目规则要求）
    )