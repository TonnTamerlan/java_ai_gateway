package com.typedgoose.contracts.ai;

import reactor.core.publisher.Flux;

public interface ChatProvider {

    Flux<ChatChunk> stream(ChatStreamRequest request);
}
