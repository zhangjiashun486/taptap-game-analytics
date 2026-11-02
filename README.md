# HunuProjec2 - TapTap 游戏数据分析与智能推荐平台

大数据课程设计项目。数据流：admin_server / client_server → base_server → data_server → MySQL；原始数据本地 + HDFS 两段式归档。

## 工程结构

| 目录 | 说明 | 端口 |
| --- | --- | --- |
| base_server | Spring Boot 后端（唯一对外出口：JWT/游戏/行为/推荐/问答/管理统计） | 8080 |
| data_server | Python 数据服务（爬虫/入库/聚类/HDFS/FastAPI） | 8081 |
| client_server | 用户端前端（Vue3 + TS + Vite） | 8001 |
| admin_server | 管理端前端（Vue3 + TS + Vite + ECharts） | 8002 |
| docs | 全部交付文档（需求/设计/测试/部署/演示） | — |
| prototype | 可点击 HTML 原型 | — |
| sql | sql.sql（全部建表脚本） | — |
| data | 数据文件：ID 清单、详情 JSONL、聚类结果、测试报告、前端截图 | — |

## 快速启动

```powershell
# 0) 启动虚拟机（MySQL 5.6 + HDFS）；退出 Steam（8080）
# 1) Python 服务
cd data_server; $env:TAPTAP_DB_PASSWORD="你的密码"; .venv\Scripts\python.exe run.py
# 2) Java 服务
cd ../base_server; $env:TEMP="C:\temp"; $env:TMP="C:\temp"; $env:TAPTAP_DB_PASSWORD="你的密码"; mvn spring-boot:run
# 3) 两个前端
cd ../client_server; npm run dev     # http://localhost:8001
cd ../admin_server;  npm run dev     # http://localhost:8002
```

详细说明见 `docs/部署与启动说明.docx`。

## 演示账号

| 角色 | 账号 | 密码 |
| --- | --- | --- |
| 管理员 | admin | 123456 |
| 用户 | demo_user_01 ~ demo_user_20 | 123456 |

## 当前进度（系统工程路径）

1. 需求采集 ✅  2. 需求分析 ✅（老师通过）  3. 原型设计 ✅
4. 需求分解 ✅（数据库 + 10,219 款游戏入库）
5. 项目开发 ✅（Python + Java + 两个 Vue 前端全部完成）
6. 项目测试 ✅（系统测试 32 项全部通过，其中接口集成 20 项；另有 34 个只读 SQL 校验用例）
7. 交付 ✅（文档 + PPT + 演示脚本齐全）

## 重要参考

- 开发全过程：`docs/项目开发日志.docx`（含 Java 复刻顺序清单）
- Java 后端开发笔记：本地维护（未随仓库发布）
- 答辩材料：`docs/答辩演示-图文版.pptx`（图为主 · 33 页 · 含 8 处截图位标注）、`docs/答辩演示-图文版.pdf`、`docs/答辩演示-截图清单.md`、`docs/答辩演示脚本.docx`

