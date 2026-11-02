package org.example.base_server.service;

import org.example.base_server.dao.AdminStatsDao;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/** 管理端统计实现：把 DAO 查询结果组装成前端需要的 JSON 结构 */
@Service
public class AdminStatsServiceImpl implements AdminStatsService {

    private final AdminStatsDao dao;

    public AdminStatsServiceImpl(AdminStatsDao dao) {
        this.dao = dao;
    }

    @Override
    public Map<String, Object> overview() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("games", dao.countGames());
        data.put("tags", dao.countTags());
        data.put("gameTags", dao.countGameTags());
        data.put("users", dao.countUsers());
        data.put("behaviors", dao.countBehaviors());
        data.put("qaLogs", dao.countQaLogs());
        data.put("crawlLogs", dao.countCrawlLogs());
        data.put("latestClusterRun", dao.latestRunId());
        return data;
    }

    @Override
    public Map<String, Object> crawl() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recent", dao.recentCrawlLogs());
        data.put("statusCounts", dao.crawlStatusCounts());
        return data;
    }

    @Override
    public Map<String, Object> cluster() {
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> run = dao.latestClusterRun();
        data.put("run", run);
        if (run != null && run.get("runId") != null) {
            long runId = ((Number) run.get("runId")).longValue();
            data.put("clusters", dao.clusterMeta(runId));
        } else {
            data.put("clusters", java.util.List.of());
        }
        return data;
    }

    @Override
    public Map<String, Object> behavior() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("typeCounts", dao.behaviorTypeCounts());
        data.put("topGames", dao.topGamesByBehavior());
        data.put("activeUsers", dao.activeUsers());
        data.put("daily", dao.behaviorDaily());
        return data;
    }

    @Override
    public Map<String, Object> qa() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("intentCounts", dao.qaIntentCounts());
        data.put("recent", dao.recentQaLogs());
        return data;
    }
}
