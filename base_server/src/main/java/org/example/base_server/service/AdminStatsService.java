package org.example.base_server.service;

import java.util.Map;

/** 管理端统计业务接口 */
public interface AdminStatsService {

    Map<String, Object> overview();   // 总览
    Map<String, Object> crawl();      // 采集（含 HDFS 归档日志）
    Map<String, Object> cluster();    // 聚类
    Map<String, Object> behavior();   // 用户行为
    Map<String, Object> qa();         // 问答
}
