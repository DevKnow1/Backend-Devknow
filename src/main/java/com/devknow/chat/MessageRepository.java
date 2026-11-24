package com.devknow.chat;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByConversationIdOrderByIdAsc(Long conversationId);
    List<Message> findByConversationIdOrderByIdDesc(Long conversationId, Pageable pageable);
}
