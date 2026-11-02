package org.example.base_server.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.base_server.entity.Game;

import java.util.List;

/**
 * 游戏表访问接口（MyBatis 注解 + 动态 SQL）。
 *
 * 分页原理：LIMIT offset, limit
 *   page=1, limit=20 → offset=0
 *   page=2, limit=20 → offset=20
 *
 * 排序原理：ORDER BY ${orderBy}
 *   注意：${} 是直接拼字符串（有 SQL 注入风险），
 *   所以 Service 层必须先做“白名单”校验，只允许固定几个排序字段（见 GameServiceImpl）。
 */
@Mapper
public interface GameDao {

    /** 分页查询游戏列表（支持按名称模糊搜索 + 白名单排序） */
    @Select("<script>" +
            "SELECT game_id AS gameId, title, rating_score AS ratingScore, latest_score AS latestScore, " +
            "       review_count AS reviewCount, fans_count AS fansCount, hits_total AS hitsTotal, " +
            "       wish_count AS wishCount, reserve_count AS reserveCount, price, platform, " +
            "       released_time AS releasedTime, developer, intro, icon_url AS iconUrl " +
            "FROM t_game " +
            "WHERE 1 = 1 " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND title LIKE CONCAT('%', #{keyword}, '%') " +
            "</if>" +
            "ORDER BY ${orderBy} " +
            "LIMIT #{offset}, #{limit}" +
            "</script>")
    List<Game> selectPage(@Param("keyword") String keyword,
                          @Param("orderBy") String orderBy,
                          @Param("offset") int offset,
                          @Param("limit") int limit);

    /** 统计符合条件的总数（分页需要） */
    @Select("<script>" +
            "SELECT COUNT(*) FROM t_game " +
            "WHERE 1 = 1 " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND title LIKE CONCAT('%', #{keyword}, '%') " +
            "</if>" +
            "</script>")
    long count(@Param("keyword") String keyword);

    /** 按游戏 ID 查询详情 */
    @Select("SELECT game_id AS gameId, title, rating_score AS ratingScore, latest_score AS latestScore, " +
            "       review_count AS reviewCount, fans_count AS fansCount, hits_total AS hitsTotal, " +
            "       wish_count AS wishCount, reserve_count AS reserveCount, price, platform, " +
            "       released_time AS releasedTime, developer, intro, icon_url AS iconUrl " +
            "FROM t_game WHERE game_id = #{gameId}")
//    #{gameId}：MyBatis 的参数占位符，使用这次方法调用传入的编号。
    Game findById(@Param("gameId") Long gameId);

    /** 查询某款游戏的全部标签 */
    @Select("SELECT t.tag_name FROM t_game_tag gt " +
            "JOIN t_tag t ON gt.tag_id = t.tag_id " +
            "WHERE gt.game_id = #{gameId} ORDER BY t.tag_name")
    List<String> findTags(@Param("gameId") Long gameId);
}
