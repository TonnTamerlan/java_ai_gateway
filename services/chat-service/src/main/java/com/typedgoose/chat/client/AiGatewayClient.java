package com.typedgoose.chat.client;

import com.typedgoose.contracts.ai.ChatChunk;
import com.typedgoose.contracts.ai.ChatStreamRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.PostExchange;
import reactor.core.publisher.Flux;

public interface AiGatewayClient {

    @PostExchange(url = "/v1/chat/stream", accept = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<ChatChunk> streamChat(@RequestBody ChatStreamRequest request);
}
