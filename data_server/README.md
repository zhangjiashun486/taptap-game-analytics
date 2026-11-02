# data_server（Python 数据服务）

按《项目规则》：Python 侧统一放在本工程，提供 HTTP 接口供 **base_server** 调用，前端不得直连。
服务端口：**8081**（可用 .env 修改）。

## 目录结构

```
data_server/
├─ config.py              配置中心：只从环境变量/.env 读取（不写死密码）
├─ run.py                 启动入口（uvicorn 运行 FastAPI）
├─ .env.example           配置模板（复制为 .env 后填写，.env 不提交 Git）
├─ requirements.txt       依赖清单
├─ app/
│  ├─ main.py             FastAPI 应用入口：创建 app、注册路由、CORS
│  └─ api/
│     └─ health.py        探活接口 GET /api/health
└─ jobs/                  离线任务（由接口或命令行触发）
   ├─ import_games.py      TapTap JSONL -> MySQL 导入
   ├─ cluster_games.py     KMeans 聚类 v1（对比版）
   ├─ cluster_games_v2.py  KMeans 聚类 v2（正式版，含 DRY_RUN 开关）
   └─ crawler/             爬虫代码（01 列表爬取 / 02 详情补爬 + README）
```

## 快速开始

```powershell
cd <项目目录>/data_server

# 1) 配置（可选）：复制模板并填写数据库密码
copy .env.example .env

# 2) 启动服务
.venv/Scripts/python.exe run.py
```

启动后访问：

| 地址 | 说明 |
| --- | --- |
| http://127.0.0.1:8081/api/health | 健康检查（返回 status/service/time/version） |
| http://127.0.0.1:8081/docs | 自动生成的接口文档（Swagger UI） |
| http://127.0.0.1:8081/ | 服务信息页 |

## 离线任务（不依赖 HTTP 服务）

```powershell
# 导入数据（需要数据库密码，读环境变量 TAPTAP_DB_PASSWORD 或 .env）
.venv/Scripts/python.exe jobs/import_games.py

# 聚类：DRY_RUN=1 只演示不写库；=0 正式写库（见文件顶部）
.venv/Scripts/python.exe jobs/cluster_games_v2.py --dry-run
```

## 约定

1. 只允许 base_server 调用本服务，前端不得直接访问；
2. 所有敏感配置只从环境变量/.env 读取，禁止提交到仓库；
3. 新增接口放在 `app/api/` 下，通过 `app/main.py` 注册。