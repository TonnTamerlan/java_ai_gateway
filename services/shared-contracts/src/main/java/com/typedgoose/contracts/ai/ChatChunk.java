package com.typedgoose.contracts.ai;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ChatChunk.Delta.class, name = "delta"),
        @JsonSubTypes.Type(value = ChatChunk.Done.class, name = "done"),
        @JsonSubTypes.Type(value = ChatChunk.Error.class, name = "error")
})
public sealed interface ChatChunk {

    record Delta(String text) implements ChatChunk {
    }

    record Done(Usage usage) implements ChatChunk {
    }

    record Error(String code, String message) implements ChatChunk {
    }
}
