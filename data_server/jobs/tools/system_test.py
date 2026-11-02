# -*- coding: utf-8 -*-
r"""
【文件作用】
    HunuProjec2 系统接口测试脚本（可重复运行），对应《系统测试报告》里的 20 项接口用例。

【覆盖范围】
    1) data_server（Python / FastAPI，默认 8081）：探活、统计接口；
    2) base_server（Java / Spring Boot，默认 8080）：探活、数据库连通、登录、鉴权、业务接口、管理端统计；
    3) 权限用例：未登录访问受保护接口应返回 401；普通用户访问管理接口应返回 403。

【运行前提】
    ① 虚拟机 MySQL（192.168.100.101）已启动；
    ② data_server 已启动：cd data_server; python run.py           （8081）
    ③ base_server 已启动：cd base_server; mvn spring-boot:run      （8080，端口被占用时可换 18080）

【运行方式】
    cd data_server
    .venv\Scripts\python.exe jobs\tools\system_test.py
    # 端口不是默认值时可指定：
    #   .venv\Scripts\python.exe jobs\tools\system_test.py --java-base http://localhost:18080
    #   .venv\Scripts\python.exe jobs\tools\system_test.py --data-base http://localhost:8081
    # 只想看结果、不覆盖已有报告：
    #   .venv\Scripts\python.exe jobs\tools\system_test.py --no-save

【输出】
    终端逐条 PASS/FAIL 表格 + 汇总行；
    data/output/system_test_report.json（与报告文档中的结构一致，可用 --no-save 关闭）
"""
import argparse
import json
import os
import sys
import time
from datetime import datetime
from pathlib import Path

import requests

# 项目根目录：本文件位于 <项目>/data_server/jobs/tools/system_test.py
BASE = Path(__file__).resolve().parents[3]
REPORT_FILE = BASE / "data" / "output" / "system_test_report.json"

USERNAME = "demo_user_01"
PASSWORD = "123456"
ADMIN_USER = "admin"
ADMIN_PASSWORD = "123456"

TIMEOUT = 8  # 单次请求超时（秒）


class Runner:
    def __init__(self, java_base: str, data_base: str):
        self.java = java_base.rstrip("/")
        self.data = data_base.rstrip("/")
        self.results = []
        self.user_token = None
        self.admin_token = None

    # ---------------- 基础设施 ----------------
    def call(self, name, method, url, expect=200, headers=None, json_body=None, params=None,
             token=None, save_token=None):
        """发一次请求并记录结果；expect 为期望的 HTTP 状态码"""
        h = dict(headers or {})
        if token:
            h["Authorization"] = f"Bearer {token}"
        full = url
        try:
            resp = requests.request(method, url, headers=h, json=json_body, params=params, timeout=TIMEOUT)
            code = resp.status_code
            ok = (code == expect)
            info = ""
            try:
                body = resp.json()
                info = body.get("message") or ""
                data = body.get("data")
                if ok and save_token:
                    token_value = (data or {}).get("token") if isinstance(data, dict) else None
                    if token_value:
                        setattr(self, save_token, token_value)
                        info = info or "token 已获取"
                    else:
                        ok = False
                        info = "返回体里没有 token"
            except ValueError:
                info = (resp.text or "")[:60]
            if not ok and not info:
                info = f"期望 HTTP {expect}，实际 {code}"
        except requests.exceptions.ConnectionError:
            code, ok, info = 0, False, "连接失败：服务没启动？"
        except requests.exceptions.Timeout:
            code, ok, info = 0, False, f"请求超时（>{TIMEOUT}s）"
        except Exception as e:                                     # 任何异常都记录，不中断整体测试
            code, ok, info = 0, False, f"异常：{e!r}"

        row = {"name": name, "method": method,
               "url": url.replace(self.java, "").replace(self.data, ""),
               "http": code, "pass": bool(ok), "info": info}
        self.results.append(row)
        mark = "PASS" if ok else "FAIL"
        print(f"[{mark}] {len(self.results):>2}. {name:<28} {row['method']:<4} {row['url']:<38} "
              f"HTTP {code:<4} {info}", flush=True)
        return row

    def section(self, title):
        print(f"\n--- {title} ---", flush=True)

    # ---------------- 20 项用例 ----------------
    def run(self):
        print("=" * 108)
        print(f"TapTap 游戏数据分析与智能推荐平台 · 系统接口测试")
        print(f"data_server = {self.data}    base_server = {self.java}")
        print("=" * 108, flush=True)

        self.section("一、Python 数据服务（data_server）")
        self.call("data_server 探活", "GET", f"{self.data}/api/health")
        self.call("data_server 统计接口", "GET", f"{self.data}/api/stats/overview")

        self.section("二、Java 基础与登录（base_server）")
        self.call("base_server 探活", "GET", f"{self.java}/api/health")
        self.call("数据库连通", "GET", f"{self.java}/api/health/db")
        self.call("用户登录(demo_user_01)", "POST", f"{self.java}/api/user/login",
                  json_body={"username": USERNAME, "password": PASSWORD}, save_token="user_token")
        self.call("管理员登录(admin)", "POST", f"{self.java}/api/admin/login",
                  json_body={"username": ADMIN_USER, "password": ADMIN_PASSWORD}, save_token="admin_token")

        if not self.user_token:
            print("\n[警告] 没有拿到用户 token，后续需要登录的用例会失败（先启动 base_server 再跑本脚本）", flush=True)

        self.section("三、业务接口（需要登录）")
        self.call("查看个人信息", "GET", f"{self.java}/api/user/me", token=self.user_token)
        self.call("游戏列表(第1页)", "GET", f"{self.java}/api/game/page",
                  params={"page": 1, "limit": 5}, token=self.user_token)
        self.call("游戏搜索(原神)", "GET", f"{self.java}/api/game/page",
                  params={"keyword": "原神"}, token=self.user_token)
        self.call("游戏详情(蛋仔派对)", "GET", f"{self.java}/api/game/206776", token=self.user_token)
        self.call("我的行为记录", "GET", f"{self.java}/api/behavior/mine", token=self.user_token)
        self.call("个性化推荐", "GET", f"{self.java}/api/recommend",
                  params={"limit": 5}, token=self.user_token)
        self.call("数据问答", "POST", f"{self.java}/api/qa/ask",
                  json_body={"question": "预约最高的 5 款游戏"}, token=self.user_token)

        self.section("四、管理端统计（需要 ADMIN）")
        for path, name in [("overview", "管理端统计-overview"), ("crawl", "管理端统计-crawl"),
                           ("cluster", "管理端统计-cluster"), ("behavior", "管理端统计-behavior"),
                           ("qa", "管理端统计-qa")]:
            self.call(name, "GET", f"{self.java}/api/admin/stats/{path}", token=self.admin_token)

        self.section("五、权限测试（401 / 403）")
        self.call("未登录访问受保护接口(应401)", "GET", f"{self.java}/api/game/page", expect=401)
        self.call("普通用户访问管理接口(应403)", "GET", f"{self.java}/api/admin/stats/overview",
                  expect=403, token=self.user_token)
        return self.results


def main():
    try:
        sys.stdout.reconfigure(errors="replace")      # 防止个别字符在 GBK 终端里报编码错误
    except Exception:
        pass
    ap = argparse.ArgumentParser(description="HunuProjec2 系统接口测试")
    ap.add_argument("--java-base", default=os.getenv("BASE_SERVER_URL", "http://localhost:8080"))
    ap.add_argument("--data-base", default=os.getenv("DATA_SERVER_URL", "http://localhost:8081"))
    ap.add_argument("--no-save", action="store_true", help="只打印结果，不覆盖 data/output/system_test_report.json")
    args = ap.parse_args()

    t0 = time.time()
    results = Runner(args.java_base, args.data_base).run()
    passed = sum(1 for r in results if r["pass"])
    failed = len(results) - passed

    print("\n" + "=" * 108)
    print(f"测试完成：共 {len(results)} 项 | 通过 {passed} | 失败 {failed} | 耗时 {time.time() - t0:.1f} 秒")
    if failed == 0:
        print("结论：全部通过，系统主流程可用")
    else:
        print("结论：存在失败用例，请先确认虚拟机 MySQL、data_server(8081)、base_server(8080) 是否已启动")
    print("=" * 108, flush=True)

    if not args.no_save:
        REPORT_FILE.parent.mkdir(parents=True, exist_ok=True)
        report = {"time": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
                  "total": len(results), "passed": passed, "failed": failed, "results": results}
        with open(REPORT_FILE, "w", encoding="utf-8") as f:
            json.dump(report, f, ensure_ascii=False, indent=2)
        print(f"报告已写入：{REPORT_FILE}", flush=True)

    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
