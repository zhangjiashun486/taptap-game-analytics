package org.example.base_server.dao;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.base_server.vo.RecommendItem;

import java.math.BigDecimal;
import java.util.List;

/** 数据问答相关查询（规则版：把问题转成 SQL） */
@Mapper
public interface QaDao {

    /** 查询评分不低于 minRating 的游戏 */
    @Select("SELECT g.game_id AS gameId, g.title, g.rating_score AS ratingScore, " +
            "       g.fans_count AS fansCount, g.icon_url AS iconUrl, g.developer " +
            "FROM t_game g WHERE g.rating_score >= #{minRating} " +
            "ORDER BY g.rating_score DESC, g.fans_count DESC LIMIT #{limit}")
    List<RecommendItem> findByRatingAtLeast(@Param("minRating") BigDecimal minRating,
                                            @Param("limit") int limit);

    /** 按某个指标取 TopN；orderBy 由 Service 白名单控制（防注入） */
    @Select("SELECT g.game_id AS gameId, g.title, g.rating_score AS ratingScore, " +
            "       g.fans_count AS fansCount, g.icon_url AS iconUrl, g.developer " +
            "FROM t_game g WHERE g.rating_score IS NOT NULL " +
            "ORDER BY ${orderBy} DESC, g.fans_count DESC LIMIT #{limit}")
    List<RecommendItem> findTopBy(@Param("orderBy") String orderBy, @Param("limit") int limit);

    /** 按标签查询游戏 */
    @Select("SELECT g.game_id AS gameId, g.title, g.rating_score AS ratingScore, " +
            "       g.fans_count AS fansCount, g.icon_url AS iconUrl, g.developer " +
            "FROM t_game_tag gt JOIN t_tag t ON gt.tag_id = t.tag_id " +
            "JOIN t_game g ON gt.game_id = g.game_id " +
            "WHERE t.tag_name = #{tag} ORDER BY g.fans_count DESC LIMIT #{limit}")
    List<RecommendItem> findByTag(@Param("tag") String tag, @Param("limit") int limit);

    /** 按名称找一款游戏（用于“类似XX的游戏”） */
    @Select("SELECT g.game_id AS gameId, g.title, g.rating_score AS ratingScore, " +
            "       g.fans_count AS fansCount, g.icon_url AS iconUrl, g.developer " +
            "FROM t_game g WHERE g.title LIKE CONCAT('%', #{keyword}, '%') " +
            "ORDER BY g.rating_score DESC, g.fans_count DESC LIMIT 1")
    RecommendItem findGameByTitle(@Param("keyword") String keyword);

    /** 游戏总数 */
    @Select("SELECT COUNT(*) FROM t_game")
    long countGames();

    /** 全部标签名（用于识别问题里提到的标签） */
    @Select("SELECT tag_name FROM t_tag")
    List<String> findAllTagNames();

    /** 写问答日志：mode 区分 RULE / LLM */
    @Insert("INSERT INTO t_qa_log(user_id, question, intent, answer, mode, hit_count, created_at) " +
            "VALUES(#{userId}, #{question}, #{intent}, #{answer}, #{mode}, #{hitCount}, NOW())")
    int insertLog(@Param("userId") Long userId,
                  @Param("question") String question,
                  @Param("intent") String intent,
                  @Param("answer") String answer,
                  @Param("mode") String mode,
                  @Param("hitCount") int hitCount);
}