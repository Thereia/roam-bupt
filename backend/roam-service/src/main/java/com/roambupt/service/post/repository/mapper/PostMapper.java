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
}
