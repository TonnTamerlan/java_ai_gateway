package com.typedgoose.aigateway.api;

import com.typedgoose.contracts.ai.ChatChunk;
import com.typedgoose.contracts.ai.ChatProvider;
import com.typedgoose.contracts.ai.ChatStreamRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/v1/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatProvider chatProvider;

    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatChunk>> stream(@RequestBody ChatStreamRequest request) {
        long startNanos = System.nanoTime();
        AtomicInteger chunkCount = new AtomicInteger();
        AtomicLong promptTokens = new AtomicLong(-1);
        AtomicLong completionTokens = new AtomicLong(-1);

        log.info("ai-gateway request received tier={} messages={} systemLen={}",
                request.modelTier(),
                request.messages() == null ? 0 : request.messages().size(),
                request.systemMessage() == null ? 0 : request.systemMessage().length());

        return chatProvider.stream(request)
                .doOnNext(chunk -> {
                    chunkCount.incrementAndGet();
                    if (chunk instanceof ChatChunk.Done done && done.usage() != null) {
                        promptTokens.set(done.usage().promptTokens());
                        completionTokens.set(done.usage().completionTokens());
                    }
                })
                .doOnComplete(() -> log.info(
                        "ai-gateway response complete tier={} chunks={} latencyMs={} promptTokens={} completionTokens={}",
                        request.modelTier(), chunkCount.get(), millisSince(startNanos),
                        promptTokens.get(), completionTokens.get()))
                .doOnError(ex -> log.warn(
                        "ai-gateway response failed tier={} chunks={} latencyMs={} error={}",
                        request.modelTier(), chunkCount.get(), millisSince(startNanos), ex.toString()))
                .map(this::toSse);
    }

    private ServerSentEvent<ChatChunk> toSse(ChatChunk chunk) {
        String event = switch (chunk) {
            case ChatChunk.Delta ignored -> "delta";
            case ChatChunk.Done ignored -> "done";
            case ChatChunk.Error ignored -> "error";
        };
        return ServerSentEvent.<ChatChunk>builder(chunk).event(event).build();
    }

    private long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
