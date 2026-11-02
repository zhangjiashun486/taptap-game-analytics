# TapTap 爬虫说明（data_server/jobs/crawler）

本目录是项目的数据采集代码，采用 **公开接口 + 低频访问 + 断点续跑** 的方式，
只采集游戏元数据（标题/评分/评论数/关注数/标签/简介等），不下载图片、游戏包，不采集正文内容，仅用于课程教学。

## 爬取流程（按顺序执行）

```
01_crawl_games.py    →  得到全部游戏 ID（data/raw/closure/all_app_ids.json，本次 10,221 个）
        ↓
02_crawl_details.py  →  按 ID 抓完整详情（data/raw/taptap_details.jsonl，本次 10,219 条 / 163MB）
        ↓
../import_games.py   →  解析 JSONL 入库 MySQL（t_game / t_tag / t_game_tag）
```

## 运行方式

```powershell
# 1) 榜单 + 标签闭合爬取（可中断续跑；本次实测约 29 分钟 / 10,221 个 ID）
python 01_crawl_games.py

# 2) 详情补爬（可中断续跑；本次实测约 195 分钟 / 10,219 条）
python 02_crawl_details.py

# 3) 导入 MySQL（需要密码；只读 JSONL，不访问网络）
$env:TAPTAP_DB_PASSWORD = "你的密码"
python ../import_games.py
```

## 用到的接口（均为 TapTap Web 端公开接口）

| 用途 | 接口 | 说明 |
| --- | --- | --- |
| 榜单 | `/webapiv2/app-top/v2/hits` | 参数 `type_name`（hot/new/reserve/pop/sell/in_app_event_reserve）、`from`、`limit=10` |
| 标签页 | `/tag/{标签}?page=N` | 每页约 18 款游戏，页面中可继续发现新标签 |
| 游戏详情 | `/webapiv2/app/v6/detail?id={ID}` | 返回完整元数据（本次入库使用的数据源） |

请求头中携带 `X-UA`（客户端标识参数，非签名）与正常 User-Agent / Referer。

## 合规与稳定性设计

1. **只公开数据**：不登录、不绕过验证码、不逆向签名；
2. **低频**：请求间隔随机 0.6~1.3 秒，连续失败会退避（最高 60 秒）；
3. **断点续跑**：`state.json`（标签阶段）、`progress.json`（详情阶段）记录进度，随时可续；
4. **可观测**：结束时写 `status.txt` 并弹窗提示（DONE/PAUSED/TIMEOUT/ERROR + 数量）；
5. **数据仅本地使用**：大文件（JSONL）已在 `.gitignore` 中排除，不推送仓库。