package com.roambupt.web.portal.rating;

import com.roambupt.api.model.dto.RatingCommand;
import com.roambupt.api.model.enums.StatusEnum;
import com.roambupt.api.model.exception.BusinessException;
import com.roambupt.api.model.vo.RatingFeedVO;
import com.roambupt.api.model.vo.RatingSummaryVO;
import com.roambupt.api.model.vo.ResVo;
import com.roambupt.core.context.CurrentUserContext;
import com.roambupt.service.rating.service.RatingService;
import com.roambupt.web.global.OptionalAuth;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 地点评分与评价流。 */
@RestController
@RequestMapping("/api/v1/topics/{topicId}/ratings")
public class RatingController {

    private final RatingService ratingService;

    public RatingController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    @OptionalAuth
    @GetMapping
    public ResVo<RatingFeedVO> feed(@PathVariable long topicId,
                                    @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "10") int pageSize) {
        return ResVo.ok(ratingService.feed(topicId, CurrentUserContext.uid(), page, pageSize));
    }

    @DeleteMapping
    public ResVo<RatingSummaryVO> clear(@PathVariable long topicId) {
        Long uid = CurrentUserContext.uid();
        if (uid == null) {
            throw new BusinessException(StatusEnum.UNAUTHORIZED);
        }
        return ResVo.ok(ratingService.clear(topicId, uid));
    }

    @PutMapping
    public ResVo<RatingSummaryVO> rate(@PathVariable long topicId,
                                       @Valid @RequestBody RatingCommand command) {
        Long uid = CurrentUserContext.uid();
        if (uid == null) {
            throw new BusinessException(StatusEnum.UNAUTHORIZED);
        }
        return ResVo.ok(ratingService.rate(topicId, uid, command));
    }
}
