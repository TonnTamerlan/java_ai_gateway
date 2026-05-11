package com.typedgoose.chat.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/chats")
public class ChatController {

    @PostMapping("/messages")
    public Mono<MessageResponse> postMessage(@RequestBody MessageRequest request) {
        return Mono.just(new MessageResponse("Message got"));
    }
}
