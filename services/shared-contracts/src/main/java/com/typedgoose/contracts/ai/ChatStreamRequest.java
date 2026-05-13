package com.typedgoose.contracts.ai;

import java.util.List;

public record ChatStreamRequest(
        ModelTier modelTier,
        String systemMessage,
        List<ChatMessage> messages
) {
}
