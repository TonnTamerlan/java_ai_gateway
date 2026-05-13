package com.typedgoose.aigateway.openai;

import com.typedgoose.contracts.ai.ChatChunk;
import com.typedgoose.contracts.ai.ChatMessage;
import com.typedgoose.contracts.ai.ChatProvider;
import com.typedgoose.contracts.ai.ChatStreamRequest;
import com.typedgoose.contracts.ai.Usage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class OpenAiChatProvider implements ChatProvider {

    private final ChatModel chatModel;
    private final ModelTierMapper modelTierMapper;

    @Override
    public Flux<ChatChunk> stream(ChatStreamRequest request) {
        String modelId = modelTierMapper.resolve(request.modelTier());
        Prompt prompt = buildPrompt(request, modelId);

        return chatModel.stream(prompt)
                .concatMap(this::toChunks)
                .onErrorResume(ex -> {
                    log.warn("openai stream failed: {}", ex.toString());
                    return Flux.just(new ChatChunk.Error("provider_error", ex.getMessage()));
                });
    }

    private Prompt buildPrompt(ChatStreamRequest request, String modelId) {
        List<Message> instructions = new ArrayList<>();
        if (request.systemMessage() != null && !request.systemMessage().isBlank()) {
            instructions.add(new SystemMessage(request.systemMessage()));
        }
        for (ChatMessage msg : request.messages()) {
            instructions.add(toSpringAiMessage(msg));
        }

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(modelId)
                .streamUsage(true)
                .build();

        return new Prompt(instructions, options);
    }

    private Message toSpringAiMessage(ChatMessage msg) {
        return switch (msg.role()) {
            case USER -> new UserMessage(msg.content());
            case ASSISTANT -> new AssistantMessage(msg.content());
        };
    }

    private Flux<ChatChunk> toChunks(ChatResponse response) {
        List<ChatChunk> emit = new ArrayList<>(2);

        Generation result = response.getResult();
        if (result != null) {
            AssistantMessage out = result.getOutput();
            if (out != null) {
                String text = out.getText();
                if (text != null && !text.isEmpty()) {
                    emit.add(new ChatChunk.Delta(text));
                }
            }
        }

        if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
            org.springframework.ai.chat.metadata.Usage u = response.getMetadata().getUsage();
            int prompt = u.getPromptTokens() == null ? 0 : u.getPromptTokens();
            int completion = u.getCompletionTokens() == null ? 0 : u.getCompletionTokens();
            if (prompt > 0 || completion > 0) {
                emit.add(new ChatChunk.Done(new Usage(prompt, completion)));
            }
        }

        return Flux.fromIterable(emit);
    }
}
