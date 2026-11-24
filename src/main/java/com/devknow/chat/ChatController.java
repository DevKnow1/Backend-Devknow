package com.devknow.chat;

import com.devknow.auth.AuthUser;
import com.devknow.chat.ChatDtos.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ChatController {
    private final ChatService chat;

    public ChatController(ChatService chat) { this.chat = chat; }

    @PostMapping("/chat")
    public ChatResponse ask(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody ChatRequest req) {
        return chat.ask(user, req);
    }

    @GetMapping("/conversations")
    public List<ConversationSummary> list(@AuthenticationPrincipal AuthUser user) {
        return chat.list(user);
    }

    @GetMapping("/conversations/{id}")
    public ConversationDetail get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return chat.get(user, id);
    }

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        chat.delete(user, id);
        return ResponseEntity.noContent().build();
    }
}
