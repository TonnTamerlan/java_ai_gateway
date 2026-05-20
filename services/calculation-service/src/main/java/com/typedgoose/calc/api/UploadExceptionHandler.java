package com.typedgoose.calc.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.util.Map;

// Tomcat's multipart parser throws before DispatcherServlet invokes the
// handler method, so a @Controller-local @ExceptionHandler can never catch it.
// This advice maps parser-time failures to clean JSON 4xx instead of opaque 500.
@Slf4j
@RestControllerAdvice
public class UploadExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handlePayloadTooLarge(MaxUploadSizeExceededException ex) {
        log.info("multipart upload rejected (size): {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(Map.of("error", "upload exceeds size limit: " + ex.getMessage()));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, String>> handleMultipart(MultipartException ex) {
        log.info("multipart upload rejected: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(Map.of("error", "invalid multipart request: " + ex.getMessage()));
    }
}
