package com.typedgoose.calc.api;

import com.typedgoose.calc.db.FilesRepository;
import com.typedgoose.calc.db.JobsRepository;
import com.typedgoose.calc.domain.FileStatus;
import com.typedgoose.calc.domain.SummarizationFile;
import com.typedgoose.calc.domain.SummarizationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SummarizeController.class)
class SummarizeControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    SummarizationService service;

    @MockitoBean
    JobsRepository jobs;

    @MockitoBean
    FilesRepository files;

    @Test
    void submitReturnsJobIdAndCorrelationIds() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();
        given(service.submit(eq("summarize each"), anyList()))
                .willReturn(new SummarizeResponse(jobId, List.of(c1, c2)));

        MockMultipartFile file1 = txt("a.txt", "alpha file body");
        MockMultipartFile file2 = txt("b.txt", "beta file body");

        mvc.perform(multipart("/summarize")
                        .file(file1)
                        .file(file2)
                        .param("prompt", "summarize each"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.correlationIds.length()").value(2))
                .andExpect(jsonPath("$.correlationIds[0]").value(c1.toString()));
    }

    @Test
    void missingFilesIsRejectedAs400() throws Exception {
        mvc.perform(multipart("/summarize").param("prompt", "anything"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void blankPromptIsRejectedAs400() throws Exception {
        mvc.perform(multipart("/summarize")
                        .file(txt("a.txt", "body"))
                        .param("prompt", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        verifyNoInteractions(service);
    }

    @Test
    void nonTxtFileIsRejectedAs400() throws Exception {
        mvc.perform(multipart("/summarize")
                        .file(new MockMultipartFile(
                                "files", "doc.pdf", "application/pdf",
                                "pretend pdf".getBytes(StandardCharsets.UTF_8)))
                        .param("prompt", "summarize"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void elevenFilesIsRejectedAs400() throws Exception {
        var request = multipart("/summarize").param("prompt", "summarize");
        for (int i = 0; i < 11; i++) {
            request = request.file(txt("f" + i + ".txt", "body " + i));
        }
        mvc.perform(request).andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void listFilesReturnsPagedView() throws Exception {
        UUID jobId = UUID.randomUUID();
        SummarizationFile row = new SummarizationFile(
                UUID.randomUUID(), 0L, jobId, UUID.randomUUID(),
                "alpha.txt", "body", "do it", FileStatus.DONE,
                "summary", "gpt-4o-mini", 11, 7, null,
                Instant.parse("2026-05-19T09:00:00Z"),
                Instant.parse("2026-05-19T09:01:00Z"));
        Pageable expectedPageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<SummarizationFile> page = new PageImpl<>(List.of(row), expectedPageable, 1);
        given(files.search(isNull(), isNull(), any(Pageable.class))).willReturn(page);

        mvc.perform(get("/summarize/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(row.id().toString()))
                .andExpect(jsonPath("$.content[0].fileName").value("alpha.txt"))
                .andExpect(jsonPath("$.content[0].status").value("DONE"))
                .andExpect(jsonPath("$.content[0].summary").value("summary"))
                .andExpect(jsonPath("$.content[0].model").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.content[0].originalText").doesNotExist());
    }

    @Test
    void listFilesPassesStatusAndNameFiltersThrough() throws Exception {
        given(files.search(eq(FileStatus.DONE), eq("alp"), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mvc.perform(get("/summarize/files")
                        .param("status", "DONE")
                        .param("name", "alp"))
                .andExpect(status().isOk());

        then(files).should().search(eq(FileStatus.DONE), eq("alp"), any(Pageable.class));
    }

    @Test
    void listFilesRejectsUnknownSortField() throws Exception {
        mvc.perform(get("/summarize/files").param("sort", "originalText,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void listFilesAcceptsAllWhitelistedSortFields() throws Exception {
        given(files.search(isNull(), isNull(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        for (String field : List.of("fileName", "status", "createdAt", "updatedAt")) {
            mvc.perform(get("/summarize/files").param("sort", field + ",asc"))
                    .andExpect(status().isOk());
        }
        // Sanity: assertions ran without an unexpected throw
        assertThat(true).isTrue();
    }

    @Test
    void listFilesCapsPageSizeAtHundred() throws Exception {
        given(files.search(isNull(), isNull(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        mvc.perform(get("/summarize/files").param("size", "500"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        then(files).should().search(isNull(), isNull(), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    private static MockMultipartFile txt(String name, String body) {
        return new MockMultipartFile(
                "files", name, "text/plain", body.getBytes(StandardCharsets.UTF_8));
    }
}
