package org.example.base_server.service;

import java.util.Map;

/** 个性化推荐业务接口 */
public interface RecommendService {

    /** 为用户生成推荐（有行为→同簇/同标签；无行为→冷启动热门高分） */
    Map<String, Object> recommend(Long userId, int limit);
}
