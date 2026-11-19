package com.roambupt.service.post.converter;

import com.roambupt.api.model.vo.CountsVO;
import com.roambupt.api.model.vo.MediaVO;
import com.roambupt.api.model.vo.PostVO;
import com.roambupt.api.model.vo.TopicBriefVO;
import com.roambupt.api.model.vo.UserBriefVO;
import com.roambupt.service.post.repository.entity.PostDO;

import java.util.List;

/** 帖子转换。 */
public final class PostConverter {

    private static final int EXCERPT_LENGTH = 80;
    private PostConverter() {
    }

    public static PostVO toVO(PostDO post, UserBriefVO author, List<TopicBriefVO> topics,
                              List<MediaVO> media, CountsVO counts, boolean withContent,
                              Boolean myLiked, Boolean myCollected) {
        return new PostVO(post.getId(), post.getTitle(), excerpt(post.getContent()),
                withContent ? post.getContent() : null, post.getStatus(),
                author, topics, media, counts, myLiked, myCollected,
                post.getCreatedTime(), post.getUpdatedTime());
    }

    static String excerpt(String content) {
        if (content == null) {
            return "";
        }
        String flat = content.replaceAll("\\s+", " ").trim();
        return flat.length() <= EXCERPT_LENGTH ? flat : flat.substring(0, EXCERPT_LENGTH) + "…";
    }
}
