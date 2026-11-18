package com.roambupt.service.post.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 帖子媒体。 */
@Data
@TableName("post_media")
public class PostMediaDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private String mediaType;
    private String url;
    private Integer sortNo;
}
