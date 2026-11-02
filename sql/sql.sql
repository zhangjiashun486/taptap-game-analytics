-- ============================================================
-- TapTap 游戏数据分析与智能推荐平台 - 数据库建表脚本
-- 数据库: taptap_course  编码: utf8mb4  引擎: InnoDB
-- 适用: MySQL 5.6+（JSON 字段以 TEXT 存储，兼容旧版本）
-- ============================================================
CREATE DATABASE IF NOT EXISTS taptap_course
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE taptap_course;

-- 1. 用户表
CREATE TABLE IF NOT EXISTS t_user (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
  username      VARCHAR(50)  NOT NULL COMMENT '用户名(唯一)',
  password_hash VARCHAR(100) NOT NULL COMMENT '密码哈希(BCrypt)',
  nickname      VARCHAR(50)  NULL COMMENT '昵称',
  role          VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色: USER/ADMIN',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0禁用',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB COMMENT='用户表';

-- 2. 游戏主表（来自 TapTap 详情采集）
CREATE TABLE IF NOT EXISTS t_game (
  game_id       BIGINT PRIMARY KEY COMMENT 'TapTap游戏ID',
  title         VARCHAR(255) NOT NULL COMMENT '游戏名称',
  rating_score  DECIMAL(3,1) NULL COMMENT '综合评分(0~10)',
  latest_score  DECIMAL(3,1) NULL COMMENT '最新版评分',
  review_count  INT          NOT NULL DEFAULT 0 COMMENT '评论数',
  fans_count    BIGINT       NOT NULL DEFAULT 0 COMMENT '关注数',
  hits_total    BIGINT       NOT NULL DEFAULT 0 COMMENT '热度值',
  wish_count    INT          NOT NULL DEFAULT 0 COMMENT '愿望单数',
  reserve_count INT          NOT NULL DEFAULT 0 COMMENT '预约数',
  price         VARCHAR(50)  NULL COMMENT '价格/付费模式',
  platform      VARCHAR(20)  NULL COMMENT '平台(安卓/iOS/PC)',
  released_time DATETIME     NULL COMMENT '发行时间',
  developer     VARCHAR(255) NULL COMMENT '厂商/开发者',
  intro         TEXT         NULL COMMENT '游戏简介',
  icon_url      VARCHAR(500) NULL COMMENT '图标URL',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_rating (rating_score),
  KEY idx_fans (fans_count),
  KEY idx_hits (hits_total),
  KEY idx_released (released_time)
) ENGINE=InnoDB COMMENT='游戏主表';

-- 3. 标签表 + 游戏-标签关联表
CREATE TABLE IF NOT EXISTS t_tag (
  tag_id   BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '标签ID',
  tag_name VARCHAR(100) NOT NULL COMMENT '标签名',
  UNIQUE KEY uk_tag_name (tag_name)
) ENGINE=InnoDB COMMENT='标签表';

CREATE TABLE IF NOT EXISTS t_game_tag (
  game_id BIGINT NOT NULL,
  tag_id  BIGINT NOT NULL,
  PRIMARY KEY (game_id, tag_id),
  KEY idx_tag (tag_id),
  CONSTRAINT fk_gt_game FOREIGN KEY (game_id) REFERENCES t_game(game_id) ON DELETE CASCADE,
  CONSTRAINT fk_gt_tag  FOREIGN KEY (tag_id)  REFERENCES t_tag(tag_id)  ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='游戏-标签关联表';

-- 4. 用户行为表
CREATE TABLE IF NOT EXISTS t_behavior (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id       BIGINT      NOT NULL COMMENT '用户ID',
  game_id       BIGINT      NOT NULL COMMENT '游戏ID',
  behavior_type VARCHAR(20) NOT NULL COMMENT 'VIEW/FAVORITE/LIKE/PLAYED/RATE',
  score         TINYINT     NULL COMMENT '评分(1~10, 仅RATE)',
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user (user_id, created_at),
  KEY idx_game (game_id),
  CONSTRAINT fk_bh_user FOREIGN KEY (user_id) REFERENCES t_user(id) ON DELETE CASCADE,
  CONSTRAINT fk_bh_game FOREIGN KEY (game_id) REFERENCES t_game(game_id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='用户行为表';

-- 5. 聚类运行表 / 聚类结果表 / 簇画像表
CREATE TABLE IF NOT EXISTS t_cluster_run (
  run_id       BIGINT PRIMARY KEY AUTO_INCREMENT,
  algorithm    VARCHAR(30)  NOT NULL DEFAULT 'KMeans',
  k_value      INT          NOT NULL COMMENT '簇数K',
  feature_info VARCHAR(500) NULL COMMENT '特征说明',
  silhouette   DECIMAL(5,4) NULL COMMENT '轮廓系数',
  sse          DECIMAL(18,4) NULL COMMENT '误差平方和',
  params_json  TEXT         NULL COMMENT '完整参数',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='聚类运行记录';

CREATE TABLE IF NOT EXISTS t_cluster_result (
  run_id     BIGINT NOT NULL,
  game_id    BIGINT NOT NULL,
  cluster_id INT    NOT NULL COMMENT '簇编号',
  PRIMARY KEY (run_id, game_id),
  KEY idx_cluster (run_id, cluster_id),
  CONSTRAINT fk_cr_run  FOREIGN KEY (run_id)  REFERENCES t_cluster_run(run_id) ON DELETE CASCADE,
  CONSTRAINT fk_cr_game FOREIGN KEY (game_id) REFERENCES t_game(game_id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='聚类结果(游戏->簇)';

CREATE TABLE IF NOT EXISTS t_cluster_meta (
  run_id       BIGINT NOT NULL,
  cluster_id   INT    NOT NULL,
  cluster_name VARCHAR(50) NULL COMMENT '簇命名(如:口碑神作)',
  size         INT    NOT NULL DEFAULT 0 COMMENT '簇内游戏数',
  keywords     VARCHAR(500) NULL COMMENT '主题关键词',
  centroid     TEXT NULL COMMENT '簇中心(归一化特征均值)',
  samples      TEXT NULL COMMENT '代表游戏TopN',
  PRIMARY KEY (run_id, cluster_id),
  CONSTRAINT fk_cm_run FOREIGN KEY (run_id) REFERENCES t_cluster_run(run_id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='簇画像/命名';

-- 6. 采集日志表
CREATE TABLE IF NOT EXISTS t_crawl_log (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_id    VARCHAR(50) NULL COMMENT '批次号',
  task_type   VARCHAR(30) NOT NULL COMMENT '榜单/标签/详情',
  status      VARCHAR(20) NOT NULL COMMENT 'RUNNING/DONE/FAILED/PAUSED',
  total       INT DEFAULT 0, success INT DEFAULT 0, failed INT DEFAULT 0,
  started_at  DATETIME NULL, finished_at DATETIME NULL,
  message     VARCHAR(500) NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='采集日志';

-- 7. 问答日志表
CREATE TABLE IF NOT EXISTS t_qa_log (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id    BIGINT NULL,
  question   VARCHAR(500) NOT NULL,
  intent     VARCHAR(50)  NULL COMMENT '识别出的意图',
  answer     TEXT         NULL,
  mode       VARCHAR(20)  NOT NULL DEFAULT 'RULE' COMMENT 'RULE/LLM',
  hit_count  INT          NOT NULL DEFAULT 0,
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user_time (user_id, created_at)
) ENGINE=InnoDB COMMENT='问答日志';

-- 8. 推荐日志表（可选）
CREATE TABLE IF NOT EXISTS t_recommend_log (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id    BIGINT NOT NULL,
  game_id    BIGINT NOT NULL,
  score      DECIMAL(8,4) NULL COMMENT '推荐得分',
  reason     VARCHAR(300) NULL COMMENT '推荐理由',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user_time (user_id, created_at)
) ENGINE=InnoDB COMMENT='推荐日志';

-- 9. 初始化管理员账号（密码需首次登录后修改；哈希由后端生成后替换）
-- INSERT INTO t_user(username, password_hash, nickname, role)
-- VALUES ('admin', '$2a$10$请替换为BCrypt哈希', '管理员', 'ADMIN');
-- ============================================================
-- 新增表（用户登录日志）2026-09-12
-- ============================================================
CREATE TABLE IF NOT EXISTS t_login_log (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id    BIGINT       NULL COMMENT '用户ID（管理员登录时为空）',
  username   VARCHAR(50)  NOT NULL COMMENT '登录用户名',
  role       VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT 'USER/ADMIN',
  login_ip   VARCHAR(50)  NULL COMMENT '登录IP',
  user_agent VARCHAR(300) NULL COMMENT '浏览器UA',
  success    TINYINT      NOT NULL DEFAULT 1 COMMENT '1成功 0失败',
  message    VARCHAR(200) NULL COMMENT '失败原因',
  login_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  KEY idx_username_time (username, login_time)
) ENGINE=InnoDB COMMENT='用户登录日志';

-- ============================================================
-- 新增表（采集任务控制）2026-09-15
-- ============================================================
CREATE TABLE IF NOT EXISTS t_crawl_task (
  id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
  task_type   VARCHAR(30)  NOT NULL COMMENT 'game-id/detail/archive',
  status      VARCHAR(20)  NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/DONE/FAILED/PAUSED',
  progress    INT          NOT NULL DEFAULT 0 COMMENT '0-100',
  stage       VARCHAR(100) NULL COMMENT '当前执行阶段',
  message     VARCHAR(500) NULL COMMENT '最新状态说明',
  mode        VARCHAR(20)  NOT NULL DEFAULT 'DEMO' COMMENT 'DEMO/PYTHON',
  started_at  DATETIME     NULL,
  finished_at DATETIME     NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_type_status (task_type, status),
  KEY idx_created_at (created_at)
) ENGINE=InnoDB COMMENT='采集任务控制表';

CREATE TABLE IF NOT EXISTS t_crawl_task_log (
  id         BIGINT        PRIMARY KEY AUTO_INCREMENT,
  task_id    BIGINT        NOT NULL,
  level      VARCHAR(10)   NOT NULL DEFAULT 'INFO' COMMENT 'INFO/WARN/ERROR',
  message    VARCHAR(1000) NOT NULL,
  created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_task_id (task_id, id)
) ENGINE=InnoDB COMMENT='采集任务详细日志';