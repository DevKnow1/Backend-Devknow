package com.devknow.chat;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    List<Conversation> findByUserIdOrderByUpdatedAtDesc(Long userId);
    /** RNF-09: toda consulta se filtra por propietario en el servidor. */
    Optional<Conversation> findByIdAndUserId(Long id, Long userId);
}
