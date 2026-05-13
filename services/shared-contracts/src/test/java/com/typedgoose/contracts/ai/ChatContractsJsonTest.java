package com.typedgoose.contracts.ai;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatContractsJsonTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Test
    void chatStreamRequestRoundTrip() {
        ChatStreamRequest original = new ChatStreamRequest(
                ModelTier.FAST,
                "You are a helpful assistant.",
                List.of(
                        new ChatMessage(MessageRole.USER, "hi"),
                        new ChatMessage(MessageRole.ASSISTANT, "hello"),
                        new ChatMessage(MessageRole.USER, "what's the weather?")
                )
        );

        String json = mapper.writeValueAsString(original);
        ChatStreamRequest restored = mapper.readValue(json, ChatStreamRequest.class);

        assertThat(restored).isEqualTo(original);
        assertThat(json).contains("\"modelTier\":\"FAST\"");
        assertThat(json).contains("\"role\":\"USER\"");
    }

    @Test
    void deltaChunkSerialisesWithTypeTag() {
        ChatChunk delta = new ChatChunk.Delta("Hello");

        String json = mapper.writeValueAsString(delta);

        assertThat(json).contains("\"type\":\"delta\"");
        assertThat(json).contains("\"text\":\"Hello\"");
    }

    @Test
    void doneChunkRoundTripWithUsage() {
        ChatChunk done = new ChatChunk.Done(new Usage(12, 34));

        String json = mapper.writeValueAsString(done);
        ChatChunk restored = mapper.readValue(json, ChatChunk.class);

        assertThat(json).contains("\"type\":\"done\"");
        assertThat(restored).isEqualTo(done);
    }

    @Test
    void errorChunkRoundTrip() {
        ChatChunk error = new ChatChunk.Error("provider_error", "upstream timed out");

        String json = mapper.writeValueAsString(error);
        ChatChunk restored = mapper.readValue(json, ChatChunk.class);

        assertThat(json).contains("\"type\":\"error\"");
        assertThat(restored).isEqualTo(error);
    }

    @Test
    void sealedInterfaceDeserialisesEachVariantByTypeTag() {
        String deltaJson = """
                {"type":"delta","text":"part"}
                """;
        String doneJson = """
                {"type":"done","usage":{"promptTokens":1,"completionTokens":2}}
                """;
        String errorJson = """
                {"type":"error","code":"x","message":"y"}
                """;

        assertThat(mapper.readValue(deltaJson, ChatChunk.class))
                .isInstanceOf(ChatChunk.Delta.class);
        assertThat(mapper.readValue(doneJson, ChatChunk.class))
                .isInstanceOf(ChatChunk.Done.class);
        assertThat(mapper.readValue(errorJson, ChatChunk.class))
                .isInstanceOf(ChatChunk.Error.class);
    }
}
