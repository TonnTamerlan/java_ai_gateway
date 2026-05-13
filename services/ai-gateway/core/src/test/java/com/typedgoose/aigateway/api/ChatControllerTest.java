package com.typedgoose.aigateway.api;

import com.typedgoose.contracts.ai.ChatChunk;
import com.typedgoose.contracts.ai.ChatMessage;
import com.typedgoose.contracts.ai.ChatProvider;
import com.typedgoose.contracts.ai.ChatStreamRequest;
import com.typedgoose.contracts.ai.MessageRole;
import com.typedgoose.contracts.ai.ModelTier;
import com.typedgoose.contracts.ai.Usage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    WebTestClient client;

    @MockitoBean
    ChatProvider chatProvider;

    @Test
    void streamsDeltasThenDoneAsSse() {
        when(chatProvider.stream(any(ChatStreamRequest.class))).thenReturn(Flux.just(
                new ChatChunk.Delta("Hello"),
                new ChatChunk.Delta(" "),
                new ChatChunk.Delta("world"),
                new ChatChunk.Done(new Usage(12, 3))
        ));

        byte[] raw = client.post()
                .uri("/v1/chat/stream")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ChatStreamRequest(
                        ModelTier.FAST,
                        "be helpful",
                        List.of(new ChatMessage(MessageRole.USER, "hi"))))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(byte[].class)
                .getResponseBodyContent();

        String body = new String(raw);
        assertThat(body).contains("event:delta");
        assertThat(body).contains("\"text\":\"Hello\"");
        assertThat(body).contains("\"text\":\"world\"");
        assertThat(body).contains("event:done");
        assertThat(body).contains("\"promptTokens\":12");
        assertThat(body).contains("\"completionTokens\":3");
    }

    @Test
    void streamsErrorEventOnProviderError() {
        when(chatProvider.stream(any(ChatStreamRequest.class))).thenReturn(Flux.just(
                new ChatChunk.Error("provider_error", "boom")
        ));

        byte[] raw = client.post()
                .uri("/v1/chat/stream")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ChatStreamRequest(
                        ModelTier.MEDIUM,
                        null,
                        List.of(new ChatMessage(MessageRole.USER, "x"))))
                .exchange()
                .expectStatus().isOk()
                .returnResult(byte[].class)
                .getResponseBodyContent();

        String body = new String(raw);
        assertThat(body).contains("event:error");
        assertThat(body).contains("\"code\":\"provider_error\"");
        assertThat(body).contains("\"message\":\"boom\"");
    }
}
