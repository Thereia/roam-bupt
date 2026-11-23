package com.roambupt.service.notify.listener;

import com.roambupt.api.model.event.CommentCreatedEvent;
import com.roambupt.api.model.event.PostLikedEvent;
import com.roambupt.service.comment.service.CommentQueryService;
import com.roambupt.service.notify.mq.NotifyDispatcher;
import com.roambupt.service.notify.mq.NotifyMessage;
import com.roambupt.service.post.service.PostQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 站内通知监听器。
 *
 * <p>同步监听，交给 {@link NotifyDispatcher}：开 MQ 时投递（发布器会等事务提交），
 * 关闭时同步写库，跟主事务一起提交/回滚。</p>
 */
@Component
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    private static final String TYPE_COMMENT = "COMMENT";
    private static final String TYPE_REPLY = "REPLY";
    private static final String TYPE_LIKE = "LIKE";
    private static final String TYPE_COLLECT = "COLLECT";
    private static final String SOURCE_POST = "POST";
    private static final String SOURCE_COMMENT = "COMMENT";

    private final NotifyDispatcher notifyDispatcher;
    private final PostQueryService postQueryService;
    private final CommentQueryService commentQueryService;

    public NotificationListener(NotifyDispatcher notifyDispatcher,
                                PostQueryService postQueryService,
                                CommentQueryService commentQueryService) {
        this.notifyDispatcher = notifyDispatcher;
        this.postQueryService = postQueryService;
        this.commentQueryService = commentQueryService;
    }

    @EventListener
    public void onCommentCreated(CommentCreatedEvent event) {
        try {
            if (event.parentId() == null) {
                Long author = postQueryService.findAuthorId(event.postId());
                if (author != null) {
                    // source_id 用评论 id：同一个人对同一帖子的多条评论各算一条通知
                    notifyDispatcher.dispatch(new NotifyMessage(author, event.uid(), TYPE_COMMENT,
                            SOURCE_COMMENT, event.commentId(), event.postId(), "评论了你的帖子"));
                }
                return;
            }
            Long author = commentQueryService.findAuthorId(event.parentId());
            if (author != null) {
                notifyDispatcher.dispatch(new NotifyMessage(author, event.uid(), TYPE_REPLY,
                        SOURCE_COMMENT, event.commentId(), event.postId(), "回复了你的评论"));
            }
        } catch (Exception e) {
            // 通知失败不影响评论主流程
            log.warn("notify.comment.failed commentId={} reason={}", event.commentId(), e.getMessage());
        }
    }

    @EventListener
    public void onPostLiked(PostLikedEvent event) {
        try {
            Long author = postQueryService.findAuthorId(event.postId());
            if (author == null) {
                return;
            }
            boolean collect = "COLLECT".equals(event.action());
            notifyDispatcher.dispatch(new NotifyMessage(author, event.uid(),
                    collect ? TYPE_COLLECT : TYPE_LIKE,
                    SOURCE_POST, event.postId(), event.postId(),
                    collect ? "收藏了你的帖子" : "赞了你的帖子"));
        } catch (Exception e) {
            log.warn("notify.like.failed postId={} reason={}", event.postId(), e.getMessage());
        }
    }
}
