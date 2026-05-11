package com.typedgoose.chat.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    WebTestClient client;

    @Test
    void postMessageReturnsHardcodedReply() {
        client.post()
                .uri("/chats/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new MessageRequest("hello"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.response").isEqualTo("Message got");
    }
}
