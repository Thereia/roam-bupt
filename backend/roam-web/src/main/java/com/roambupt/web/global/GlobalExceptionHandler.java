package com.roambupt.web.global;

import com.roambupt.api.model.exception.BusinessException;
import com.roambupt.api.model.vo.ResVo;
import com.roambupt.core.jwt.InvalidTokenException;
import com.roambupt.core.session.StoreUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常翻译：业务异常按错误码映射 HTTP 状态，非预期异常记日志后返回 500。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ResVo<Void>> handleBusiness(BusinessException e) {
        return ResponseEntity.status(toHttpStatus(e.getCode()))
                .body(ResVo.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResVo<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("参数错误");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ResVo.fail(4001, message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ResVo<Void>> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ResVo.fail(4001, "请求体格式错误"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ResVo<Void>> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ResVo.fail(4001, "文件超过上限"));
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ResVo<Void>> handleInvalidToken(InvalidTokenException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ResVo.fail(401, "登录已失效，请重新登录"));
    }

    @ExceptionHandler(StoreUnavailableException.class)
    public ResponseEntity<ResVo<Void>> handleStoreUnavailable(StoreUnavailableException e) {
        log.warn("session store unavailable: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ResVo.fail(503, "依赖服务不可用，请稍后重试"));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ResVo<Void>> handleDuplicate(DuplicateKeyException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ResVo.fail(409, "资源已存在"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResVo<Void>> handleUnexpected(Exception e) {
        log.error("unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ResVo.fail(500, "服务器开小差了"));
    }

    private HttpStatus toHttpStatus(Integer code) {
        if (code == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return switch (code) {
            case 4001 -> HttpStatus.BAD_REQUEST;
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 409 -> HttpStatus.CONFLICT;
            case 429 -> HttpStatus.TOO_MANY_REQUESTS;
            case 503 -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
