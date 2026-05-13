package com.typedgoose.chat.api;

import com.typedgoose.contracts.ai.ModelTier;

public record MessageRequest(String conversationId, ModelTier model, String message) {
}
