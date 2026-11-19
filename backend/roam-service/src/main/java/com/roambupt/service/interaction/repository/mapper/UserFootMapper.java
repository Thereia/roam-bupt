package com.roambupt.service.interaction.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.roambupt.service.interaction.repository.entity.UserFootDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/** 足迹 Mapper，计数与用户状态都在这里做批量查询。 */
@Mapper
public interface UserFootMapper extends BaseMapper<UserFootDO> {

    @Select("<script>SELECT post_id, COUNT(*) AS c FROM user_foot WHERE action = #{action} AND post_id IN "
            + "<foreach collection='postIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "GROUP BY post_id</script>")
    List<FootCountRow> countByPosts(@Param("action") String action,
                                    @Param("postIds") Collection<Long> postIds);

    @Select("SELECT COUNT(*) FROM user_foot WHERE action = #{action} AND post_id = #{postId}")
    long countByPost(@Param("action") String action, @Param("postId") long postId);

    @Select("<script>SELECT post_id FROM user_foot WHERE user_id = #{uid} AND action = #{action} AND post_id IN "
            + "<foreach collection='postIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Long> findPostIds(@Param("uid") long uid,
                           @Param("action") String action,
                           @Param("postIds") Collection<Long> postIds);

    /** 批量计数的行。 */
    class FootCountRow {
        private Long postId;
        private Long c;

        public Long getPostId() {
            return postId;
        }

        public void setPostId(Long postId) {
            this.postId = postId;
        }

        public Long getC() {
            return c;
        }

        public void setC(Long c) {
            this.c = c;
        }
    }
}
