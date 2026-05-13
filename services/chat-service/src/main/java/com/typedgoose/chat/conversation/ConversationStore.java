package com.typedgoose.chat.conversation;

import com.typedgoose.contracts.ai.ChatMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;

@Component
public class ConversationStore {

    private final ConcurrentMap<String, List<ChatMessage>> histories = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Semaphore> locks = new ConcurrentHashMap<>();

    public boolean tryLock(String conversationId) {
        Semaphore s = locks.computeIfAbsent(conversationId, k -> new Semaphore(1));
        return s.tryAcquire();
    }

    public void release(String conversationId) {
        Semaphore s = locks.get(conversationId);
        if (s != null) {
            s.release();
        }
    }

    public List<ChatMessage> appendAndSnapshot(String conversationId, ChatMessage message) {
        return histories.compute(conversationId, (k, previous) -> {
            if (previous == null) {
                return List.of(message);
            }
            List<ChatMessage> next = new ArrayList<>(previous.size() + 1);
            next.addAll(previous);
            next.add(message);
            return List.copyOf(next);
        });
    }

    public int turnCount(String conversationId) {
        List<ChatMessage> h = histories.get(conversationId);
        return h == null ? 0 : h.size();
    }
}
