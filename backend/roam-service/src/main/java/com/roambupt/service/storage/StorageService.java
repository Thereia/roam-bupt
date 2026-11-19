package com.roambupt.service.storage;

import com.roambupt.api.model.vo.StoredFileVO;

/** 文件存储。P1 只有本地实现，切 OSS 时新增实现即可。 */
public interface StorageService {

    StoredFileVO save(byte[] bytes, String originalName, String contentType, long uid);
}
