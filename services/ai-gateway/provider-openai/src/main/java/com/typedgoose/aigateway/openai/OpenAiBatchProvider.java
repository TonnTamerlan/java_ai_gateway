package com.typedgoose.aigateway.openai;

import com.openai.client.OpenAIClient;
import com.openai.core.MultipartField;
import com.openai.core.http.HttpResponse;
import com.openai.models.batches.Batch;
import com.openai.models.batches.BatchCreateParams;
import com.openai.models.files.FileCreateParams;
import com.openai.models.files.FileObject;
import com.openai.models.files.FilePurpose;
import com.typedgoose.contracts.ai.BatchHandle;
import com.typedgoose.contracts.ai.BatchProvider;
import com.typedgoose.contracts.ai.BatchResult;
import com.typedgoose.contracts.ai.BatchStatus;
import com.typedgoose.contracts.ai.BatchSubmission;
import com.typedgoose.contracts.ai.ProviderException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Slf4j
public class OpenAiBatchProvider implements BatchProvider {

    private final OpenAIClient client;
    private final ModelTierMapper modelTierMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public BatchHandle submit(BatchSubmission submission) {
        if (submission.items().isEmpty()) {
            throw new ProviderException("cannot submit empty batch");
        }

        byte[] jsonl = renderJsonl(submission);

        // OpenAI's edge rejects multipart uploads with no filename; wrap the
        // raw bytes in MultipartField so the SDK sets `filename=...` on the
        // part. Without this the Files API returns a 400 from an upstream
        // proxy ("The browser (or proxy) sent a request...").
        FileCreateParams fileParams = FileCreateParams.builder()
                .purpose(FilePurpose.BATCH)
                .file(MultipartField.<InputStream>builder()
                        .value(new ByteArrayInputStream(jsonl))
                        .filename("requests.jsonl")
                        .build())
                .build();
        FileObject input = client.files().create(fileParams);
        log.info("openai batch upload: inputFileId={} bytes={}", input.id(), jsonl.length);

        BatchCreateParams batchParams = BatchCreateParams.builder()
                .inputFileId(input.id())
                .endpoint(BatchCreateParams.Endpoint.V1_CHAT_COMPLETIONS)
                .completionWindow(BatchCreateParams.CompletionWindow._24H)
                .build();
        Batch created = client.batches().create(batchParams);
        log.info("openai batch created: batchId={} status={} itemCount={}",
                created.id(), statusOf(created), submission.items().size());
        return new BatchHandle(created.id(), clock.instant());
    }

    @Override
    public BatchStatus poll(BatchHandle handle) {
        Batch batch = client.batches().retrieve(handle.providerBatchId());
        String status = statusOf(batch);
        return switch (status) {
            case "validating", "in_progress", "finalizing", "cancelling" -> new BatchStatus.InProgress();
            case "completed" -> new BatchStatus.Completed();
            case "failed", "expired", "cancelled" -> new BatchStatus.Failed(status);
            default -> {
                log.warn("openai batch unknown status: batchId={} status={}", handle.providerBatchId(), status);
                yield new BatchStatus.Pending();
            }
        };
    }

    @Override
    public List<BatchResult> fetchResults(BatchHandle handle) {
        Batch batch = client.batches().retrieve(handle.providerBatchId());
        String outputFileId = batch.outputFileId().orElseThrow(() ->
                new ProviderException("completed batch has no output file: " + handle.providerBatchId()));

        try (HttpResponse response = client.files().content(outputFileId);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            List<BatchResult> results = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                results.add(parseResultLine(line));
            }
            log.info("openai batch fetched: batchId={} results={}",
                    handle.providerBatchId(), results.size());
            return results;
        } catch (IOException e) {
            throw new ProviderException("failed to download batch output", e);
        }
    }

    private byte[] renderJsonl(BatchSubmission submission) {
        StringBuilder sb = new StringBuilder();
        for (BatchSubmission.BatchItem item : submission.items()) {
            String modelId = modelTierMapper.resolve(item.modelTier());

            ObjectNode line = objectMapper.createObjectNode();
            line.put("custom_id", item.correlationId().toString());
            line.put("method", "POST");
            line.put("url", "/v1/chat/completions");

            ObjectNode body = line.putObject("body");
            body.put("model", modelId);
            ArrayNode messages = body.putArray("messages");

            ObjectNode systemMsg = messages.addObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", item.instruction());

            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", item.content());

            try {
                sb.append(objectMapper.writeValueAsString(line));
            } catch (JacksonException e) {
                throw new ProviderException("failed to serialize batch item", e);
            }
            sb.append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private BatchResult parseResultLine(String line) {
        try {
            JsonNode root = objectMapper.readTree(line);
            UUID correlationId = UUID.fromString(root.path("custom_id").asString());

            JsonNode errorNode = root.get("error");
            if (errorNode != null && !errorNode.isNull()) {
                String msg = errorNode.path("message").asString("provider error");
                return new BatchResult(correlationId, "", "", 0, 0, Optional.of(msg));
            }

            JsonNode body = root.path("response").path("body");
            String model = body.path("model").asString("");
            String content = body.path("choices").path(0).path("message").path("content").asString("");
            int promptTokens = body.path("usage").path("prompt_tokens").asInt(0);
            int completionTokens = body.path("usage").path("completion_tokens").asInt(0);

            return new BatchResult(correlationId, content, model,
                    promptTokens, completionTokens, Optional.empty());
        } catch (JacksonException e) {
            throw new ProviderException("failed to parse batch output line: " + line, e);
        }
    }

    // Batch.Status is an enum wrapper; toString() yields the canonical lowercase
    // form ("completed", "in_progress"). Normalize defensively for any quirk.
    private String statusOf(Batch batch) {
        return batch.status().toString().toLowerCase();
    }
}
