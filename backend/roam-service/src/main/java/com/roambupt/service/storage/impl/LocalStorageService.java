package com.roambupt.service.storage.impl;

import com.roambupt.api.model.enums.StatusEnum;
import com.roambupt.api.model.exception.BusinessException;
import com.roambupt.api.model.vo.StoredFileVO;
import com.roambupt.service.storage.StorageService;
import com.roambupt.service.storage.UploadProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 本地磁盘存储。
 *
 * <p>校验顺序：大小 → Content-Type 白名单 → 魔数；三者都过才落盘。</p>
 */
@Service
public class LocalStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private static final String TYPE_JPEG = "image/jpeg";
    private static final String TYPE_PNG = "image/png";
    private static final String TYPE_WEBP = "image/webp";

    private final UploadProperties properties;

    public LocalStorageService(UploadProperties properties) {
        this.properties = properties;
    }

    @Override
    public StoredFileVO save(byte[] bytes, String originalName, String contentType, long uid) {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException(StatusEnum.PARAM_ERROR, "文件为空");
        }
        long maxBytes = (long) properties.maxSizeMb() * 1024 * 1024;
        if (bytes.length > maxBytes) {
            throw new BusinessException(StatusEnum.PARAM_ERROR, "文件不能超过 " + properties.maxSizeMb() + "MB");
        }

        String type = contentType == null ? "" : contentType.toLowerCase();
        String extension = switch (type) {
            case TYPE_JPEG -> "jpg";
            case TYPE_PNG -> "png";
            case TYPE_WEBP -> "webp";
            default -> throw new BusinessException(StatusEnum.PARAM_ERROR, "只支持 jpg / png / webp");
        };
        if (!magicMatches(bytes, type)) {
            throw new BusinessException(StatusEnum.PARAM_ERROR, "文件内容与类型不符");
        }

        String month = LocalDate.now().format(MONTH);
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path dir = Paths.get(properties.dir(), month).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(fileName), bytes);
        } catch (Exception e) {
            log.error("upload.write.failed name={} uid={}", originalName, uid, e);
            throw new BusinessException(StatusEnum.INTERNAL_ERROR, "上传失败，请重试");
        }

        int[] size = readSize(bytes);
        String url = properties.allowedPrefix() + month + "/" + fileName;
        return new StoredFileVO(url, size == null ? null : size[0], size == null ? null : size[1]);
    }

    private boolean magicMatches(byte[] bytes, String type) {
        if (bytes.length < 12) {
            return false;
        }
        return switch (type) {
            case TYPE_JPEG -> (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
            case TYPE_PNG -> (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
            case TYPE_WEBP -> bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            default -> false;
        };
    }

    private int[] readSize(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            return image == null ? null : new int[]{image.getWidth(), image.getHeight()};
        } catch (Exception e) {
            return null;
        }
    }
}
