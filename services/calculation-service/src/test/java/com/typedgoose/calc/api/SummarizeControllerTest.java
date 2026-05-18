package com.typedgoose.calc.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typedgoose.calc.db.FilesRepository;
import com.typedgoose.calc.db.JobsRepository;
import com.typedgoose.calc.domain.SummarizationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SummarizeController.class)
class SummarizeControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

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
        given(service.submit(any())).willReturn(new SummarizeResponse(jobId, List.of(c1, c2)));

        SummarizeRequest request = new SummarizeRequest(List.of(
                new FileInput("summarize", "alpha"),
                new FileInput("summarize", "beta")));

        mvc.perform(post("/summarize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.correlationIds.length()").value(2))
                .andExpect(jsonPath("$.correlationIds[0]").value(c1.toString()));
    }

    @Test
    void emptyFilesIsRejectedAs400() throws Exception {
        mvc.perform(post("/summarize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"files\":[]}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void blankInstructionIsRejectedAs400() throws Exception {
        mvc.perform(post("/summarize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"files\":[{\"instruction\":\"\",\"content\":\"text\"}]}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }
}
