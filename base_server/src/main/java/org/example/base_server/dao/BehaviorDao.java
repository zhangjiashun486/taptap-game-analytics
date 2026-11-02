package org.example.base_server.dao;

import org.apache.ibatis.annotations.*;
import org.example.base_server.entity.Behavior;
import org.example.base_server.vo.BehaviorItem;

import java.util.List;

/** 用户行为表访问接口 */
@Mapper
public interface BehaviorDao {

    /** 新增一条行为记录，并回写自增主键 */
    @Insert("INSERT INTO t_behavior(user_id, game_id, behavior_type, score, created_at) " +
            "VALUES(#{userId}, #{gameId}, #{behaviorType}, #{score}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Behavior behavior);

    /** 删除同一用户对同一游戏的某类行为（用于“收藏/玩过/评分”的幂等更新） */
    @Delete("DELETE FROM t_behavior WHERE user_id = #{userId} AND game_id = #{gameId} " +
            "AND behavior_type = #{behaviorType}")
    int delete(@Param("userId") Long userId,
               @Param("gameId") Long gameId,
               @Param("behaviorType") String behaviorType);

    /** 查询某用户的行为记录（关联游戏名称，最多 200 条） */
    @Select("SELECT b.id, b.game_id AS gameId, g.title AS gameTitle, " +
            "       b.behavior_type AS behaviorType, b.score, b.created_at AS createdAt " +
            "FROM t_behavior b JOIN t_game g ON b.game_id = g.game_id " +
            "WHERE b.user_id = #{userId} ORDER BY b.id DESC LIMIT 200")
    List<BehaviorItem> listByUser(@Param("userId") Long userId);

    /** 统计某用户的行为数量（可选，用于后续统计接口） */
    @Select("SELECT COUNT(*) FROM t_behavior WHERE user_id = #{userId}")
    int countByUser(@Param("userId") Long userId);
}
