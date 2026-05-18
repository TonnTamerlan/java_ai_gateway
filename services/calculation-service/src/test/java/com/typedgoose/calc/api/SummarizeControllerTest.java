package com.typedgoose.calc.api;

import com.typedgoose.calc.db.FilesRepository;
import com.typedgoose.calc.db.JobsRepository;
import com.typedgoose.calc.domain.SummarizationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
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

    private static MockMultipartFile txt(String name, String body) {
        return new MockMultipartFile(
                "files", name, "text/plain", body.getBytes(StandardCharsets.UTF_8));
    }
}
