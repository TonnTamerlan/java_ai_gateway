package com.typedgoose.chat.api;

import com.typedgoose.chat.client.AiGatewayClient;
import com.typedgoose.chat.conversation.ConversationStore;
import com.typedgoose.contracts.ai.ChatChunk;
import com.typedgoose.contracts.ai.MessageRole;
import com.typedgoose.contracts.ai.ModelTier;
import com.typedgoose.contracts.ai.Usage;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

import static org.assertj.core.api.Assertions.assertThat;

@WebFluxTest(controllers = ChatController.class)
@Import({ConversationStore.class, ChatControllerTest.TestConfig.class})
@TestPropertySource(properties = {
        "app.chat.system-prompt=You are a test assistant."
})
class ChatControllerTest {

    static final Deque<UpstreamStub> upstreamStubs = new ArrayDeque<>();

    record UpstreamStub(Flux<ChatChunk> chunks) {
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        Tracer tracer() {
            return Tracer.NOOP;
        }

        @Bean
        AiGatewayClient aiGatewayClient() {
            return request -> {
                UpstreamStub stub = upstreamStubs.pollFirst();
                if (stub == null) {
                    return Flux.error(new IllegalStateException("no stub enqueued"));
                }
                return stub.chunks();
            };
        }
    }

    @Autowired
    WebTestClient client;

    @Autowired
    ConversationStore store;

    @BeforeEach
    void resetStubs() {
        upstreamStubs.clear();
    }

    @Test
    void singleTurnRelaysSseAndAppendsAssistantToHistory() {
        upstreamStubs.add(chunks(
                new ChatChunk.Delta("Hello"),
                new ChatChunk.Delta(" world"),
                new ChatChunk.Done(new Usage(5, 2))));

        String body = postTurn("conv-1", ModelTier.FAST, "hi");

        assertThat(body).contains("event:delta");
        assertThat(body).contains("\"text\":\"Hello\"");
        assertThat(body).contains("\"text\":\" world\"");
        assertThat(body).contains("event:done");

        // Both user and assistant turns now in history.
        assertThat(store.turnCount("conv-1")).isEqualTo(2);
    }

    @Test
    void twoTurnsGrowHistory() {
        upstreamStubs.add(chunks(
                new ChatChunk.Delta("first reply"),
                new ChatChunk.Done(new Usage(1, 1))));
        upstreamStubs.add(chunks(
                new ChatChunk.Delta("second reply"),
                new ChatChunk.Done(new Usage(2, 2))));

        postTurn("conv-2", ModelTier.MEDIUM, "first user");
        postTurn("conv-2", ModelTier.MEDIUM, "second user");

        assertThat(store.turnCount("conv-2")).isEqualTo(4); // 2 user + 2 assistant
    }

    @Test
    void upstreamHttpErrorMeansNoPartialAssistantTurnAppended() {
        upstreamStubs.add(upstreamError());

        String body = postTurn("conv-err", ModelTier.SLOW, "hi");

        assertThat(body).contains("event:error");
        assertThat(store.turnCount("conv-err")).isEqualTo(1);
    }

    @Test
    void upstreamErrorChunkAlsoSkipsAssistantAppend() {
        upstreamStubs.add(chunks(
                new ChatChunk.Delta("partial"),
                new ChatChunk.Error("provider_error", "upstream went sideways")));

        String body = postTurn("conv-err2", ModelTier.MEDIUM, "ping");

        assertThat(body).contains("event:error");
        assertThat(body).contains("provider_error");
        assertThat(store.turnCount("conv-err2")).isEqualTo(1);
    }

    @Test
    void rejectsConcurrentTurnsOnSameConversation() {
        assertThat(store.tryLock("conv-locked")).isTrue();
        try {
            client.post()
                    .uri("/chats/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .bodyValue(new MessageRequest("conv-locked", ModelTier.FAST, "hi"))
                    .exchange()
                    .expectStatus().isEqualTo(409);
        } finally {
            store.release("conv-locked");
        }
    }

    @Test
    void missingFieldsAreRejectedAs400() {
        client.post()
                .uri("/chats/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(new MessageRequest(null, ModelTier.FAST, "hi"))
                .exchange()
                .expectStatus().isBadRequest();
    }

    private String postTurn(String conversationId, ModelTier tier, String message) {
        byte[] raw = client.post()
                .uri("/chats/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(new MessageRequest(conversationId, tier, message))
                .exchange()
                .expectStatus().isOk()
                .returnResult(byte[].class)
                .getResponseBodyContent();
        return new String(raw == null ? new byte[0] : raw, StandardCharsets.UTF_8);
    }

    private static UpstreamStub chunks(ChatChunk... cs) {
        return new UpstreamStub(Flux.just(cs));
    }

    private static UpstreamStub upstreamError() {
        return new UpstreamStub(Flux.error(WebClientResponseException.create(
                500, "Internal Server Error", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8)));
    }
}
