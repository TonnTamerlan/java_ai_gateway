package com.typedgoose.calc.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UploadExceptionHandlerTest {

    private final UploadExceptionHandler handler = new UploadExceptionHandler();

    @Test
    void maxUploadSizeMapsTo413WithErrorBody() {
        ResponseEntity<Map<String, String>> response =
                handler.handlePayloadTooLarge(new MaxUploadSizeExceededException(12_000_000L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(response.getBody())
                .isNotNull()
                .containsKey("error");
        assertThat(response.getBody().get("error")).contains("size limit");
    }

    @Test
    void genericMultipartMapsTo400WithErrorBody() {
        ResponseEntity<Map<String, String>> response =
                handler.handleMultipart(new MultipartException("file count exceeded: attachment"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
                .isNotNull()
                .containsKey("error");
        assertThat(response.getBody().get("error"))
                .contains("invalid multipart")
                .contains("file count exceeded");
    }
}
