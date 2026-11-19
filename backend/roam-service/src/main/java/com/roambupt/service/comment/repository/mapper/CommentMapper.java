package com.roambupt.service.comment.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.roambupt.service.comment.repository.entity.CommentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/** 评论 Mapper。 */
@Mapper
public interface CommentMapper extends BaseMapper<CommentDO> {

    @Select("SELECT COUNT(*) FROM comment WHERE post_id = #{postId} AND top_id = 0 AND deleted = 0")
    long countTopByPost(@Param("postId") long postId);

    @Select("SELECT COUNT(*) FROM comment WHERE post_id = #{postId} AND deleted = 0")
    long countByPost(@Param("postId") long postId);

    @Select("<script>SELECT post_id, COUNT(*) AS c FROM comment WHERE deleted = 0 AND post_id IN "
            + "<foreach collection='postIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "GROUP BY post_id</script>")
    List<PostCountRow> countByPosts(@Param("postIds") Collection<Long> postIds);

    /** 批量计数的行。 */
    class PostCountRow {
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
