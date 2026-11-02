package org.example.base_server.service;

import java.util.Map;

/** 游戏业务接口 */
public interface GameService {

    /** 分页查询：page 页码，limit 每页条数，keyword 搜索词，sort 排序方式 */
    Map<String, Object> page(int page, int limit, String keyword, String sort);

    /** 游戏详情（含标签列表）；不存在返回 null */
    Map<String, Object> detail(Long gameId);
}
