package org.example.base_server.service.impl;

import org.example.base_server.dao.BehaviorDao;
import org.example.base_server.dao.GameDao;
import org.example.base_server.dto.BehaviorRequest;
import org.example.base_server.entity.Behavior;
import org.example.base_server.service.BehaviorService;
import org.example.base_server.vo.BehaviorItem;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BehaviorServiceImpl implements BehaviorService {

    /** 允许的行为类型 */
    private static final Set<String> VALID_TYPES = Set.of("VIEW", "FAVORITE", "LIKE", "PLAYED", "RATE");

    private final BehaviorDao behaviorDao;
    private final GameDao gameDao;

    public BehaviorServiceImpl(BehaviorDao behaviorDao, GameDao gameDao) {
        this.behaviorDao = behaviorDao;
        this.gameDao = gameDao;
    }

    @Override
    public Map<String, Object> record(Long userId, BehaviorRequest request) {
        if (userId == null) {
            throw new IllegalArgumentException("未登录");
        }
        if (request.getGameId() == null) {
            throw new IllegalArgumentException("gameId 不能为空");
        }
        String type = request.getBehaviorType() == null ? "" : request.getBehaviorType().trim().toUpperCase();
        if (!VALID_TYPES.contains(type)) {
            throw new IllegalArgumentException("行为类型不合法（VIEW/FAVORITE/LIKE/PLAYED/RATE）");
        }
        if (gameDao.findById(request.getGameId()) == null) {
            throw new IllegalArgumentException("游戏不存在");
        }

        Integer score = request.getScore();
        if ("RATE".equals(type)) {
            if (score == null || score < 1 || score > 10) {
                throw new IllegalArgumentException("评分必须在 1~10 之间");
            }
        } else {
            score = null;   // 非评分行为不保存分数
        }

        // 除浏览外，同一用户对同一游戏的同类行为只保留最新一条（幂等）
        if (!"VIEW".equals(type)) {
            behaviorDao.delete(userId, request.getGameId(), type);
        }

        Behavior behavior = new Behavior();
        behavior.setUserId(userId);
        behavior.setGameId(request.getGameId());
        behavior.setBehaviorType(type);
        behavior.setScore(score);
        behaviorDao.insert(behavior);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", behavior.getId());
        data.put("gameId", behavior.getGameId());
        data.put("behaviorType", behavior.getBehaviorType());
        data.put("score", behavior.getScore());
        return data;
    }

    @Override
    public List<BehaviorItem> listMine(Long userId) {
        return behaviorDao.listByUser(userId);
    }
}
