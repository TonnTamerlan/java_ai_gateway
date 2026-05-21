package com.typedgoose.chat.api;

import com.typedgoose.chat.client.AiGatewayClient;
import com.typedgoose.chat.conversation.ConversationStore;
import com.typedgoose.contracts.ai.ChatChunk;
import com.typedgoose.contracts.ai.ChatMessage;
import com.typedgoose.contracts.ai.ChatStreamRequest;
import com.typedgoose.contracts.ai.MessageRole;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/chats")
@Slf4j
public class ChatController {

    private final AiGatewayClient aiGatewayClient;
    private final ConversationStore store;
    private final String systemPrompt;
    private final Tracer tracer;

    public ChatController(AiGatewayClient aiGatewayClient,
                          ConversationStore store,
                          Tracer tracer,
                          @Value("${app.chat.system-prompt}") String systemPrompt) {
        this.aiGatewayClient = aiGatewayClient;
        this.store = store;
        this.tracer = tracer;
        this.systemPrompt = systemPrompt;
    }

    @PostMapping(path = "/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatChunk>> postMessage(@RequestBody MessageRequest request) {
        String conversationId = request.conversationId();
        if (conversationId == null || conversationId.isBlank() || request.model() == null
                || request.message() == null) {
            return Flux.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "conversationId, model, and message are required"));
        }

        if (!store.tryLock(conversationId)) {
            log.info("concurrent_rejected conversation={} ", logId(conversationId));
            return Flux.error(new ResponseStatusException(HttpStatus.CONFLICT,
                    "conversation has an in-flight turn"));
        }

        var history = store.appendAndSnapshot(conversationId,
                new ChatMessage(MessageRole.USER, request.message()));
        long startNanos = System.nanoTime();
        int turnIndex = history.size();

        log.info("turn_started conversation={} turn={} model={}",
                logId(conversationId), turnIndex, request.model());

        ChatStreamRequest upstream = new ChatStreamRequest(request.model(), systemPrompt, history);
        StringBuilder assistantBuf = new StringBuilder();
        AtomicBoolean errored = new AtomicBoolean(false);

        // Open chatId baggage synchronously while subscribing. With
        // `spring.reactor.context-propagation: auto`, Reactor captures the active
        // observation into the Context at subscription time so the outbound call
        // (and the downstream service) sees `chatId` in MDC via the baggage→MDC bridge.
        Flux<ServerSentEvent<ChatChunk>> chain;
        try (var ignored = tracer.createBaggageInScope("chatId", conversationId)) {
            chain = aiGatewayClient.streamChat(upstream)
                .doOnNext(chunk -> {
                    if (chunk instanceof ChatChunk.Delta d) {
                        assistantBuf.append(d.text());
                    } else if (chunk instanceof ChatChunk.Error) {
                        errored.set(true);
                    }
                })
                .onErrorResume(ex -> {
                    log.warn("upstream_error conversation={} error={}",
                            logId(conversationId), ex.toString());
                    errored.set(true);
                    return Flux.just(new ChatChunk.Error("upstream_error",
                            ex.getMessage() == null ? "ai-gateway call failed" : ex.getMessage()));
                })
                .map(ChatController::toSse)
                .doOnComplete(() -> {
                    if (!errored.get() && !assistantBuf.isEmpty()) {
                        store.appendAndSnapshot(conversationId,
                                new ChatMessage(MessageRole.ASSISTANT, assistantBuf.toString()));
                    }
                    long latencyMs = (System.nanoTime() - startNanos) / 1_000_000L;
                    log.info("turn_completed conversation={} turn={} latencyMs={} errored={}",
                            logId(conversationId), turnIndex, latencyMs, errored.get());
                    store.release(conversationId);
                })
                .doOnCancel(() -> {
                    log.info("client_disconnected conversation={} turn={}",
                            logId(conversationId), turnIndex);
                    store.release(conversationId);
                });
        }
        return chain;
    }

    private static ServerSentEvent<ChatChunk> toSse(ChatChunk chunk) {
        String event = switch (chunk) {
            case ChatChunk.Delta ignored -> "delta";
            case ChatChunk.Done ignored -> "done";
            case ChatChunk.Error ignored -> "error";
        };
        return ServerSentEvent.<ChatChunk>builder(chunk).event(event).build();
    }

    private static String logId(String conversationId) {
        return Integer.toHexString(conversationId.hashCode());
    }
}
