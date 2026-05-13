package com.typedgoose.aigateway.openai;

import com.typedgoose.contracts.ai.ChatChunk;
import com.typedgoose.contracts.ai.ChatMessage;
import com.typedgoose.contracts.ai.ChatStreamRequest;
import com.typedgoose.contracts.ai.MessageRole;
import com.typedgoose.contracts.ai.ModelTier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAiChatProviderTest {

    private final ChatModel chatModel = mock(ChatModel.class);
    private final ModelTierMapper mapper = tier -> switch (tier) {
        case FAST -> "gpt-4o-mini";
        case MEDIUM -> "gpt-4o";
        case SLOW -> "gpt-4-turbo";
    };
    private final OpenAiChatProvider provider = new OpenAiChatProvider(chatModel, mapper);

    @Test
    void emitsDeltasInOrderAndFinalDoneWithUsage() {
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                chunkWithText("Hello"),
                chunkWithText(" "),
                chunkWithText("world"),
                terminalChunkWithUsage(12, 5)
        ));

        StepVerifier.create(provider.stream(request(ModelTier.FAST, "be helpful", "say hi")))
                .expectNext(new ChatChunk.Delta("Hello"))
                .expectNext(new ChatChunk.Delta(" "))
                .expectNext(new ChatChunk.Delta("world"))
                .expectNextMatches(chunk ->
                        chunk instanceof ChatChunk.Done done
                                && done.usage().promptTokens() == 12
                                && done.usage().completionTokens() == 5)
                .verifyComplete();
    }

    @Test
    void filtersEmptyDeltaChunks() {
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                chunkWithText(""),
                chunkWithText("hi"),
                chunkWithText(null)
        ));

        StepVerifier.create(provider.stream(request(ModelTier.MEDIUM, null, "ping")))
                .expectNext(new ChatChunk.Delta("hi"))
                .verifyComplete();
    }

    @Test
    void mapsUpstreamErrorToErrorChunk() {
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.error(new RuntimeException("boom")));

        StepVerifier.create(provider.stream(request(ModelTier.SLOW, null, "x")))
                .expectNextMatches(chunk ->
                        chunk instanceof ChatChunk.Error err
                                && err.code().equals("provider_error")
                                && err.message().equals("boom"))
                .verifyComplete();
    }

    @Test
    void resolvesModelTierAndEnablesStreamUsage() {
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.empty());

        provider.stream(request(ModelTier.SLOW, "sys", "hi")).blockLast();

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).stream(captor.capture());

        Prompt sent = captor.getValue();
        OpenAiChatOptions opts = (OpenAiChatOptions) sent.getOptions();
        assertThat(opts.getModel()).isEqualTo("gpt-4-turbo");
        assertThat(opts.getStreamOptions()).isNotNull();
        assertThat(opts.getStreamOptions().includeUsage()).isTrue();
    }

    @Test
    void includesSystemMessageWhenPresent() {
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.empty());

        provider.stream(request(ModelTier.FAST, "be terse", "hello there")).blockLast();

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).stream(captor.capture());

        assertThat(captor.getValue().getInstructions()).hasSize(2);
        assertThat(captor.getValue().getInstructions().get(0))
                .isInstanceOf(SystemMessage.class);
        assertThat(captor.getValue().getInstructions().get(1))
                .isInstanceOf(UserMessage.class);
    }

    @Test
    void skipsSystemMessageWhenBlank() {
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.empty());

        provider.stream(request(ModelTier.FAST, "   ", "hi")).blockLast();

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).stream(captor.capture());

        assertThat(captor.getValue().getInstructions()).hasSize(1);
        assertThat(captor.getValue().getInstructions().get(0))
                .isInstanceOf(UserMessage.class);
    }

    private ChatResponse chunkWithText(String text) {
        AssistantMessage msg = new AssistantMessage(text == null ? "" : text);
        return new ChatResponse(List.of(new Generation(msg)));
    }

    private ChatResponse terminalChunkWithUsage(int prompt, int completion) {
        AssistantMessage empty = new AssistantMessage("");
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .usage(new DefaultUsage(prompt, completion))
                .build();
        return new ChatResponse(List.of(new Generation(empty)), metadata);
    }

    private ChatStreamRequest request(ModelTier tier, String system, String userText) {
        return new ChatStreamRequest(
                tier,
                system,
                List.of(new ChatMessage(MessageRole.USER, userText))
        );
    }
}
