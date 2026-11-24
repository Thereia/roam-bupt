package com.roambupt.service.topic.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.roambupt.service.topic.repository.entity.TopicDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/** 主题 Mapper。 */
@Mapper
public interface TopicMapper extends BaseMapper<TopicDO> {

    /** 每个分类标签下、直接挂了这个标签的在线帖数。 */
    @Select("SELECT t.name AS name, COUNT(DISTINCT p.id) AS cnt FROM topic t "
            + "LEFT JOIN post_topic pt ON pt.topic_id = t.id "
            + "LEFT JOIN post p ON p.id = pt.post_id AND p.status = 'ONLINE' "
            + "WHERE t.type = 'CATEGORY' AND t.enabled = 1 "
            + "GROUP BY t.id, t.name ORDER BY cnt DESC")
    List<Map<String, Object>> countPostsByCategory();

    @Select("SELECT COUNT(*) FROM post WHERE status = 'ONLINE'")
    long countOnlinePosts();
}
