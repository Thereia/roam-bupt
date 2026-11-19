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

    @Select("SELECT p.* FROM post p JOIN post_topic pt ON pt.post_id = p.id "
            + "WHERE pt.topic_id = #{topicId} AND p.status = 'ONLINE' "
            + "ORDER BY p.created_time DESC LIMIT #{offset}, #{size}")
    List<PostDO> pageByTopic(@Param("topicId") long topicId, @Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM post p JOIN post_topic pt ON pt.post_id = p.id "
            + "WHERE pt.topic_id = #{topicId} AND p.status = 'ONLINE'")
    long countByTopic(@Param("topicId") long topicId);
    @Select("SELECT * FROM post WHERE status = 'ONLINE' ORDER BY heat DESC, created_time DESC LIMIT #{offset}, #{size}")
    List<PostDO> pageOnlineHot(@Param("offset") int offset, @Param("size") int size);

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
}
