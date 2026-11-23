package com.roambupt.service.user.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.roambupt.service.user.repository.entity.FollowDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 关注 Mapper。 */
@Mapper
public interface FollowMapper extends BaseMapper<FollowDO> {

    @Select("SELECT target_user_id FROM follow WHERE user_id = #{uid}")
    List<Long> findFollowingIds(@Param("uid") long uid);

    @Select("SELECT target_user_id FROM follow WHERE user_id = #{uid} "
            + "ORDER BY created_time DESC LIMIT #{offset}, #{size}")
    List<Long> findFollowingIdsPaged(@Param("uid") long uid, @Param("offset") int offset,
                                     @Param("size") int size);

    @Select("SELECT user_id FROM follow WHERE target_user_id = #{uid} "
            + "ORDER BY created_time DESC LIMIT #{offset}, #{size}")
    List<Long> findFollowerIdsPaged(@Param("uid") long uid, @Param("offset") int offset,
                                    @Param("size") int size);

    @Select("SELECT COUNT(*) FROM follow WHERE target_user_id = #{uid}")
    long countFollowers(@Param("uid") long uid);

    @Select("SELECT COUNT(*) FROM follow WHERE user_id = #{uid}")
    long countFollowing(@Param("uid") long uid);
}
