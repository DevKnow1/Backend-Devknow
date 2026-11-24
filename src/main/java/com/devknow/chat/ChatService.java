package com.devknow.chat;

import com.devknow.ai.AiProviderException;
import com.devknow.auth.AuthUser;
import com.devknow.chat.ChatDtos.*;
import com.devknow.common.ApiException;
import com.devknow.config.AppProperties;
import com.devknow.rag.RagResult;
import com.devknow.rag.RagService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Sin @Transactional: no se mantiene una conexión abierta durante la llamada al LLM, y la pregunta del usuario queda
 * guardada aunque el proveedor falle (RF-24: no se pierde la conversación).
 */
@Service
public class ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final TypeReference<List<SourceDto>> SOURCES_TYPE = new TypeReference<>() {};

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final RagService rag;
    private final ObjectMapper mapper;
    private final AppProperties props;

    public ChatService(ConversationRepository conversations, MessageRepository messages, RagService rag,
                       ObjectMapper mapper, AppProperties props) {
        this.conversations = conversations;
        this.messages = messages;
        this.rag = rag;
        this.mapper = mapper;
        this.props = props;
    }

    /** RF-18, RF-19, RF-22, RF-23 */
    public ChatResponse ask(AuthUser user, ChatRequest req) {
        String question = req.question().trim();
        Conversation conv = req.conversationId() == null
            ? newConversation(user.id(), question)
            : findOwned(req.conversationId(), user.id());

        List<Message> history = recentHistory(conv.getId());
        saveMessage(conv.getId(), MessageRole.USER, question, null, false);

        RagResult result;
        try {
            result = rag.answer(question, history);
        } catch (AiProviderException e) {
            HttpStatus s = e.getKind() == AiProviderException.Kind.QUOTA ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.SERVICE_UNAVAILABLE;
            log.warn("Fallo del proveedor de IA ({}) en la conversación {}", e.getKind(), conv.getId());
            throw new ApiException(s, e.getMessage(), conv.getId());
        }

        Message assistant = saveMessage(conv.getId(), MessageRole.ASSISTANT, result.answer(),
            result.sources().isEmpty() ? null : toJson(result.sources()), result.insufficientEvidence());
        conv.setUpdatedAt(java.time.Instant.now());
        conversations.save(conv); // mueve la conversación al inicio del historial
        return new ChatResponse(conv.getId(), assistant.getId(), result.answer(), result.insufficientEvidence(), result.sources());
    }

    public List<ConversationSummary> list(AuthUser user) {
        return conversations.findByUserIdOrderByUpdatedAtDesc(user.id()).stream()
            .map(c -> new ConversationSummary(c.getId(), c.getTitle(), c.getUpdatedAt())).toList();
    }

    public ConversationDetail get(AuthUser user, Long id) {
        Conversation c = findOwned(id, user.id());
        List<MessageDto> dtos = messages.findByConversationIdOrderByIdAsc(c.getId()).stream()
            .map(m -> new MessageDto(m.getId(), m.getRole(), m.getContent(), fromJson(m.getSourcesJson()),
                m.isInsufficientEvidence(), m.getCreatedAt())).toList();
        return new ConversationDetail(c.getId(), c.getTitle(), dtos);
    }

    public void delete(AuthUser user, Long id) {
        conversations.delete(findOwned(id, user.id()));
    }

    private Conversation findOwned(Long id, Long userId) {
        // 404 (no 403) para no revelar la existencia de conversaciones ajenas
        return conversations.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Conversación no encontrada."));
    }

    private Conversation newConversation(Long userId, String question) {
        Conversation c = new Conversation();
        c.setUserId(userId);
        c.setTitle(question.length() > 60 ? question.substring(0, 57) + "..." : question);
        return conversations.save(c);
    }

    private List<Message> recentHistory(Long conversationId) {
        int n = props.rag().historyMessages();
        if (n <= 0) return List.of();
        List<Message> latest = new ArrayList<>(messages.findByConversationIdOrderByIdDesc(conversationId, PageRequest.of(0, n)));
        Collections.reverse(latest);
        return latest;
    }

    private Message saveMessage(Long conversationId, MessageRole role, String content, String sourcesJson, boolean insufficient) {
        Message m = new Message();
        m.setConversationId(conversationId);
        m.setRole(role);
        m.setContent(content);
        m.setSourcesJson(sourcesJson);
        m.setInsufficientEvidence(insufficient);
        return messages.save(m);
    }

    private String toJson(List<SourceDto> sources) {
        try {
            return mapper.writeValueAsString(sources);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudieron serializar las fuentes", e);
        }
    }

    private List<SourceDto> fromJson(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return mapper.readValue(json, SOURCES_TYPE);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
