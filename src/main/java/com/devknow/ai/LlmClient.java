package com.devknow.ai;

import java.util.List;

public interface LlmClient {
    String chat(List<ChatMsg> messages);

    record ChatMsg(String role, String content) {}
}
