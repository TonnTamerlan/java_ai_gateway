package com.typedgoose.calc.api;

import com.typedgoose.calc.db.FilesRepository;
import com.typedgoose.calc.db.JobsRepository;
import com.typedgoose.calc.domain.SummarizationFile;
import com.typedgoose.calc.domain.SummarizationJob;
import com.typedgoose.calc.domain.SummarizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/summarize")
@RequiredArgsConstructor
public class SummarizeController {

    private final SummarizationService service;
    private final JobsRepository jobs;
    private final FilesRepository files;

    @PostMapping
    public SummarizeResponse submit(@Valid @RequestBody SummarizeRequest request) {
        return service.submit(request);
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<JobView> get(@PathVariable UUID jobId) {
        return jobs.findById(jobId)
                .map(job -> ResponseEntity.ok(new JobView(job, files.findByJobId(job.id()))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record JobView(SummarizationJob job, List<SummarizationFile> files) {
    }
}
