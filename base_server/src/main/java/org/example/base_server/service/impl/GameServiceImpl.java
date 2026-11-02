package org.example.base_server.service.impl;

import org.example.base_server.dao.GameDao;
import org.example.base_server.entity.Game;
import org.example.base_server.service.GameService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GameServiceImpl implements GameService {

    /**
     * 排序白名单：前端只能传这些 key，服务端翻译成固定的 SQL 排序语句。
     * 这样可以防止前端传入恶意 SQL（SQL 注入）。
     */
    private static final Map<String, String> SORT_WHITELIST = Map.of(
            "default", "fans_count DESC",
            "fans", "fans_count DESC",
            "rating", "rating_score DESC",
            "review", "review_count DESC",
            "release", "released_time DESC"
    );

    private final GameDao gameDao;

    public GameServiceImpl(GameDao gameDao) {
        this.gameDao = gameDao;
    }

    @Override
    public Map<String, Object> page(int page, int limit, String keyword, String sort) {
        // 1) 参数保护：页码至少 1，每页数量限制在 1~100
        if (page < 1) page = 1;
        if (limit < 1) limit = 20;
        if (limit > 100) limit = 100;

        // 2) 搜索词：空字符串统一转成 null，SQL 里就不用拼 LIKE
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

        // 3) 排序：不在白名单里的一律用默认排序（防止 SQL 注入）
        String orderBy = SORT_WHITELIST.getOrDefault(sort == null ? "default" : sort, SORT_WHITELIST.get("default"));

        // 4) 先查总数，再查当前页数据
        long total = gameDao.count(kw);
        int offset = (page - 1) * limit;
        List<Game> list = gameDao.selectPage(kw, orderBy, offset, limit);

        // 5) 组装分页结构：{ list, total, page, limit }（与《项目规则》一致）
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        data.put("page", page);
        data.put("limit", limit);
        return data;
    }

    @Override
    public Map<String, Object> detail(Long gameId) {
// Game 是项目中表示一款游戏的 Java 类型，里面有编号、名称、评分等字段；  game 保存游戏信息
        Game game = gameDao.findById(gameId);
        if (game == null) {
            return null;
        }
//  List<String> 表示一个装字符串的列表，它可以保存多个标签：["射击", "多人联机"]       tags 保存标签列表。
        List<String> tags = gameDao.findTags(gameId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("game", game);
        data.put("tags", tags);
        return data;
    }
}
