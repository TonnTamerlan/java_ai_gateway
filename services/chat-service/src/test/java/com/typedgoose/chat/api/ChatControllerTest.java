package com.typedgoose.chat.api;

import com.typedgoose.chat.conversation.ConversationStore;
import com.typedgoose.contracts.ai.MessageRole;
import com.typedgoose.contracts.ai.ModelTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

    private static final DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();

    static final Deque<UpstreamStub> upstreamStubs = new ArrayDeque<>();

    record UpstreamStub(HttpStatus status, String sseBody) {
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        WebClient aiGatewayClient() {
            return WebClient.builder()
                    .exchangeFunction(request -> {
                        UpstreamStub stub = upstreamStubs.pollFirst();
                        if (stub == null) {
                            return Mono.error(new IllegalStateException("no stub enqueued"));
                        }
                        ClientResponse.Builder builder = ClientResponse.create(stub.status())
                                .header("Content-Type", MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8");
                        if (!stub.sseBody().isEmpty()) {
                            DataBuffer buf = bufferFactory.wrap(stub.sseBody().getBytes(StandardCharsets.UTF_8));
                            builder = builder.body(Flux.just(buf));
                        }
                        return Mono.just(builder.build());
                    })
                    .build();
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
        upstreamStubs.add(ok("""
                event:delta
                data:{"type":"delta","text":"Hello"}

                event:delta
                data:{"type":"delta","text":" world"}

                event:done
                data:{"type":"done","usage":{"promptTokens":5,"completionTokens":2}}

                """));

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
        upstreamStubs.add(ok("""
                event:delta
                data:{"type":"delta","text":"first reply"}

                event:done
                data:{"type":"done","usage":{"promptTokens":1,"completionTokens":1}}

                """));
        upstreamStubs.add(ok("""
                event:delta
                data:{"type":"delta","text":"second reply"}

                event:done
                data:{"type":"done","usage":{"promptTokens":2,"completionTokens":2}}

                """));

        postTurn("conv-2", ModelTier.MEDIUM, "first user");
        postTurn("conv-2", ModelTier.MEDIUM, "second user");

        assertThat(store.turnCount("conv-2")).isEqualTo(4); // 2 user + 2 assistant
    }

    @Test
    void upstreamHttpErrorMeansNoPartialAssistantTurnAppended() {
        upstreamStubs.add(new UpstreamStub(HttpStatus.INTERNAL_SERVER_ERROR, ""));

        String body = postTurn("conv-err", ModelTier.SLOW, "hi");

        assertThat(body).contains("event:error");
        assertThat(store.turnCount("conv-err")).isEqualTo(1);
    }

    @Test
    void upstreamErrorChunkAlsoSkipsAssistantAppend() {
        upstreamStubs.add(ok("""
                event:delta
                data:{"type":"delta","text":"partial"}

                event:error
                data:{"type":"error","code":"provider_error","message":"upstream went sideways"}

                """));

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

    private static UpstreamStub ok(String body) {
        return new UpstreamStub(HttpStatus.OK, body);
    }
}
