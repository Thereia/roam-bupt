package com.roambupt.service.user.service;

import com.roambupt.api.model.vo.UserProfileVO;

/** 公开主页。 */
public interface UserProfileService {

    UserProfileVO profile(long targetUid, Long viewerUid);
}
