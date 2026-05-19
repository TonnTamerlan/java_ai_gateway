package com.typedgoose.contracts.ai;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = BatchStatus.Pending.class, name = "pending"),
        @JsonSubTypes.Type(value = BatchStatus.InProgress.class, name = "in_progress"),
        @JsonSubTypes.Type(value = BatchStatus.Completed.class, name = "completed"),
        @JsonSubTypes.Type(value = BatchStatus.Failed.class, name = "failed")
})
public sealed interface BatchStatus {

    record Pending() implements BatchStatus {
    }

    record InProgress() implements BatchStatus {
    }

    record Completed() implements BatchStatus {
    }

    record Failed(String reason) implements BatchStatus {
    }
}
