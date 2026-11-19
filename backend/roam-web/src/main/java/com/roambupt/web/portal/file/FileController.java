package com.roambupt.web.portal.file;

import com.roambupt.api.model.enums.StatusEnum;
import com.roambupt.api.model.exception.BusinessException;
import com.roambupt.api.model.vo.ResVo;
import com.roambupt.api.model.vo.StoredFileVO;
import com.roambupt.core.context.CurrentUserContext;
import com.roambupt.service.storage.StorageService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** 图片上传。 */
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final StorageService storageService;

    public FileController(StorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping("/images")
    public ResVo<StoredFileVO> uploadImage(@RequestParam("file") MultipartFile file) {
        Long uid = CurrentUserContext.uid();
        if (uid == null) {
            throw new BusinessException(StatusEnum.UNAUTHORIZED);
        }
        try {
            return ResVo.ok(storageService.save(file.getBytes(), file.getOriginalFilename(),
                    file.getContentType(), uid));
        } catch (IOException e) {
            throw new BusinessException(StatusEnum.INTERNAL_ERROR, "读取文件失败");
        }
    }
}
