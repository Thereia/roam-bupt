package com.roambupt.service.post.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.roambupt.service.post.repository.entity.PostDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 帖子 Mapper。串表查询写在这里，不散到别的域。 */
@Mapper
public interface PostMapper extends BaseMapper<PostDO> {

    @Select("SELECT * FROM post WHERE status = 'ONLINE' ORDER BY created_time DESC LIMIT #{offset}, #{size}")
    List<PostDO> pageOnline(@Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM post WHERE status = 'ONLINE'")
    long countOnline();

    /** 某个主题下的推荐流：互动越多越靠前。 */
    @Select("SELECT p.* FROM post p JOIN post_topic pt ON pt.post_id = p.id "
            + "LEFT JOIN (SELECT post_id, COUNT(*) c FROM comment WHERE deleted = 0 GROUP BY post_id) cm ON cm.post_id = p.id "
            + "LEFT JOIN (SELECT post_id, COUNT(*) c FROM user_foot WHERE action = 'LIKE' GROUP BY post_id) lk ON lk.post_id = p.id "
            + "LEFT JOIN (SELECT post_id, COUNT(*) c FROM user_foot WHERE action = 'COLLECT' GROUP BY post_id) cl ON cl.post_id = p.id "
            + "WHERE pt.topic_id = #{topicId} AND p.status = 'ONLINE' "
            + "ORDER BY (COALESCE(cm.c,0)*3 + COALESCE(lk.c,0)*2 + COALESCE(cl.c,0)*2 + p.heat) DESC, "
            + "p.created_time DESC LIMIT #{offset}, #{size}")
    List<PostDO> pageByTopicRecommended(@Param("topicId") long topicId,
                                        @Param("offset") int offset, @Param("size") int size);

    @Select("SELECT p.* FROM post p JOIN post_topic pt ON pt.post_id = p.id "
            + "WHERE pt.topic_id = #{topicId} AND p.status = 'ONLINE' "
            + "ORDER BY p.created_time DESC LIMIT #{offset}, #{size}")
    List<PostDO> pageByTopic(@Param("topicId") long topicId, @Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM post p JOIN post_topic pt ON pt.post_id = p.id "
            + "WHERE pt.topic_id = #{topicId} AND p.status = 'ONLINE'")
    long countByTopic(@Param("topicId") long topicId);
    @Select("SELECT * FROM post WHERE status = 'ONLINE' ORDER BY heat DESC, created_time DESC LIMIT #{offset}, #{size}")
    List<PostDO> pageOnlineHot(@Param("offset") int offset, @Param("size") int size);

    /**
     * 推荐流：互动越多越靠前（评论 3 分、点赞 2 分、收藏 2 分、heat 1 分），同分按时间倒序。
     * 与「最新」不同，避免两个 tab 内容一模一样。
     */
    @Select("SELECT p.* FROM post p "
            + "LEFT JOIN (SELECT post_id, COUNT(*) c FROM comment WHERE deleted = 0 GROUP BY post_id) cm ON cm.post_id = p.id "
            + "LEFT JOIN (SELECT post_id, COUNT(*) c FROM user_foot WHERE action = 'LIKE' GROUP BY post_id) lk ON lk.post_id = p.id "
            + "LEFT JOIN (SELECT post_id, COUNT(*) c FROM user_foot WHERE action = 'COLLECT' GROUP BY post_id) cl ON cl.post_id = p.id "
            + "WHERE p.status = 'ONLINE' "
            + "ORDER BY (COALESCE(cm.c,0)*3 + COALESCE(lk.c,0)*2 + COALESCE(cl.c,0)*2 + p.heat) DESC, "
            + "p.created_time DESC LIMIT #{offset}, #{size}")
    List<PostDO> pageOnlineRecommended(@Param("offset") int offset, @Param("size") int size);

    @Select("SELECT p.* FROM post p JOIN post_topic pt ON pt.post_id = p.id "
            + "WHERE pt.topic_id = #{topicId} AND p.status = 'ONLINE' "
            + "ORDER BY p.heat DESC, p.created_time DESC LIMIT #{offset}, #{size}")
    List<PostDO> pageByTopicHot(@Param("topicId") long topicId, @Param("offset") int offset, @Param("size") int size);

    @Select("<script>SELECT id FROM post WHERE status = 'ONLINE' "
            + "AND (title LIKE CONCAT('%', #{keyword}, '%') OR content LIKE CONCAT('%', #{keyword}, '%')) "
            + "<if test='topicId != null'>AND id IN (SELECT post_id FROM post_topic WHERE topic_id = #{topicId}) </if>"
            + "ORDER BY created_time DESC LIMIT #{offset}, #{size}</script>")
    List<Long> searchIds(@Param("keyword") String keyword, @Param("topicId") Long topicId,
                         @Param("offset") int offset, @Param("size") int size);

    @Select("<script>SELECT COUNT(*) FROM post WHERE status = 'ONLINE' "
            + "AND (title LIKE CONCAT('%', #{keyword}, '%') OR content LIKE CONCAT('%', #{keyword}, '%')) "
            + "<if test='topicId != null'>AND id IN (SELECT post_id FROM post_topic WHERE topic_id = #{topicId}) </if>"
            + "</script>")
    long countSearch(@Param("keyword") String keyword, @Param("topicId") Long topicId);

    @Select("<script>SELECT * FROM post WHERE user_id = #{uid} "
            + "<if test='!includeAll'>AND status = 'ONLINE' </if>"
            + "ORDER BY created_time DESC LIMIT #{offset}, #{size}</script>")
    List<PostDO> pageByUser(@Param("uid") long uid, @Param("includeAll") boolean includeAll,
                            @Param("offset") int offset, @Param("size") int size);

    @Select("<script>SELECT COUNT(*) FROM post WHERE user_id = #{uid} "
            + "<if test='!includeAll'>AND status = 'ONLINE' </if></script>")
    long countByUser(@Param("uid") long uid, @Param("includeAll") boolean includeAll);

    @Select("<script>SELECT * FROM post WHERE status = 'ONLINE' AND user_id IN "
            + "<foreach collection='uids' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "ORDER BY created_time DESC LIMIT #{offset}, #{size}</script>")
    List<PostDO> pageByUserIds(@Param("uids") java.util.Collection<Long> uids,
                               @Param("offset") int offset, @Param("size") int size);

    @Select("<script>SELECT COUNT(*) FROM post WHERE status = 'ONLINE' AND user_id IN "
            + "<foreach collection='uids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    long countByUserIds(@Param("uids") java.util.Collection<Long> uids);

    @Select("SELECT * FROM post WHERE status = #{status} ORDER BY created_time DESC LIMIT #{offset}, #{size}")
    List<PostDO> pageByStatus(@Param("status") String status, @Param("offset") int offset,
                              @Param("size") int size);

    @Select("SELECT COUNT(*) FROM post WHERE status = #{status}")
    long countByStatus(@Param("status") String status);
}
