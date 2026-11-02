# -*- coding: utf-8 -*-
"""
【文件作用】
    data_server 的配置中心：所有配置只从「环境变量 / .env 文件」读取。
    项目规则要求：禁止把数据库密码等敏感信息写死在代码里。

【使用方式】
    1) 复制 .env.example 为 .env（.env 不会提交到 Git）；
    2) 在 .env 中填写自己的数据库密码等；
    3) 代码里统一通过本文件读取（import config）。
"""
from pathlib import Path
import os

from dotenv import load_dotenv

# 当前文件所在目录（即 data_server/）
BASE_DIR = Path(__file__).resolve().parent

# 读取同目录下的 .env 文件（如果不存在就跳过，不报错）
load_dotenv(BASE_DIR / ".env")

# ---------------- 服务配置 ----------------
SERVICE_NAME = "data_server"                                  # 服务名（探活接口会返回）
HOST = os.getenv("DATA_SERVER_HOST", "0.0.0.0")               # 监听地址：0.0.0.0 表示本机可访问
PORT = int(os.getenv("DATA_SERVER_PORT", "8081"))             # 端口：项目规则约定 8081
DEBUG = os.getenv("DATA_SERVER_DEBUG", "0") == "1"            # 调试模式：1 开启热重载，0 关闭

# ---------------- 数据库配置（供 jobs/后续接口使用）----------------
DB_HOST = os.getenv("TAPTAP_DB_HOST", "192.168.100.101")      # 虚拟机 MySQL 地址
DB_PORT = int(os.getenv("TAPTAP_DB_PORT", "3306"))            # 端口
DB_USER = os.getenv("TAPTAP_DB_USER", "root")                 # 用户名
DB_PASSWORD = os.getenv("TAPTAP_DB_PASSWORD", "")             # 密码：只从环境变量/.env 读取
DB_NAME = os.getenv("TAPTAP_DB_NAME", "taptap_course")        # 数据库名

# ---------------- HDFS 配置（本地 + HDFS 两段式存储的第二段）----------------
HDFS_HOST = os.getenv("HDFS_HOST", "192.168.100.101")          # NameNode 地址
HDFS_WEB_PORT = int(os.getenv("HDFS_WEB_PORT", "50070"))       # WebHDFS HTTP 端口（Hadoop 2.x 为 50070）
HDFS_USER = os.getenv("HDFS_USER", "root")                     # HDFS 用户（本机 HDFS 根目录属主为 root）
HDFS_BASE = os.getenv("HDFS_BASE", "/taptap")                  # 项目在 HDFS 上的根目录


def db_config() -> dict:
    """
    返回数据库连接参数字典，供 pymysql.connect(**db_config()) 使用。
    好处：密码不散落在各个脚本里，只在这里统一读取。
    """
    return {
        "host": DB_HOST,
        "port": DB_PORT,
        "user": DB_USER,
        "password": DB_PASSWORD,
        "database": DB_NAME,
        "charset": "utf8mb4",
    }