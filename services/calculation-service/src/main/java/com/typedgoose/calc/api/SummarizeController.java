package com.typedgoose.calc.api;

import com.typedgoose.calc.db.FilesRepository;
import com.typedgoose.calc.db.JobsRepository;
import com.typedgoose.calc.domain.SummarizationFile;
import com.typedgoose.calc.domain.SummarizationJob;
import com.typedgoose.calc.domain.SummarizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/summarize")
@RequiredArgsConstructor
public class SummarizeController {

    private static final int MAX_FILES = 10;

    private final SummarizationService service;
    private final JobsRepository jobs;
    private final FilesRepository files;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SummarizeResponse submit(
            @RequestParam("prompt") String prompt,
            @RequestParam("files") MultipartFile[] uploads) {

        String trimmedPrompt = prompt == null ? "" : prompt.trim();
        if (trimmedPrompt.isEmpty()) {
            throw new IllegalArgumentException("prompt is required");
        }
        if (uploads == null || uploads.length == 0) {
            throw new IllegalArgumentException("at least one file is required");
        }
        if (uploads.length > MAX_FILES) {
            throw new IllegalArgumentException("at most " + MAX_FILES + " files are allowed");
        }

        List<FileInput> inputs = new ArrayList<>(uploads.length);
        for (MultipartFile upload : uploads) {
            String filename = upload.getOriginalFilename();
            if (filename == null || !filename.toLowerCase().endsWith(".txt")) {
                throw new IllegalArgumentException(
                        "only .txt files are accepted (got: " + filename + ")");
            }
            if (upload.isEmpty()) {
                throw new IllegalArgumentException("file is empty: " + filename);
            }
            String content;
            try {
                content = new String(upload.getBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalArgumentException("could not read " + filename + ": " + e.getMessage());
            }
            if (content.isBlank()) {
                throw new IllegalArgumentException("file has no readable text: " + filename);
            }
            inputs.add(new FileInput(filename, content));
        }

        return service.submit(trimmedPrompt, inputs);
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<JobView> get(@PathVariable UUID jobId) {
        return jobs.findById(jobId)
                .map(job -> ResponseEntity.ok(new JobView(job, files.findByJobId(job.id()))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        log.info("summarize upload rejected: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    public record JobView(SummarizationJob job, List<SummarizationFile> files) {
    }
}
