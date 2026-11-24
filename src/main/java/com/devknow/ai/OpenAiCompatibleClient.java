package com.devknow.ai;

import com.devknow.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Adaptador para cualquier proveedor con API compatible con OpenAI
 * (/embeddings y /chat/completions): OpenAI, Groq, Gemini (endpoint OpenAI-compatible), Ollama, etc.
 * RNF-16/RNF-27: timeouts, errores controlados y reintentos limitados (nunca reintenta 4xx/429).
 */
@Component
@ConditionalOnProperty(prefix = "devknow.ai", name = "provider", havingValue = "openai-compatible")
public class OpenAiCompatibleClient implements EmbeddingClient, LlmClient {
    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);

    private final AppProperties.Ai cfg;
    private final RestClient rest;

    public OpenAiCompatibleClient(AppProperties props) {
        this.cfg = props.ai();
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout((int) Math.min(cfg.timeout().toMillis(), 10_000));
        f.setReadTimeout((int) cfg.timeout().toMillis());
        RestClient.Builder b = RestClient.builder().baseUrl(trimSlash(cfg.baseUrl())).requestFactory(f);
        if (cfg.apiKey() != null && !cfg.apiKey().isBlank()) {
            b.defaultHeader("Authorization", "Bearer " + cfg.apiKey());
        }
        this.rest = b.build();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", cfg.embeddingModel());
        body.put("input", texts);
        JsonNode json = post("/embeddings", body);
        JsonNode data = json == null ? null : json.get("data");
        if (data == null || !data.isArray() || data.size() != texts.size()) {
            throw new AiProviderException(AiProviderException.Kind.INVALID_RESPONSE,
                "El servicio de IA devolvió una respuesta de embeddings inválida.");
        }
        List<float[]> out = new ArrayList<>(texts.size());
        for (JsonNode item : data) {
            JsonNode emb = item.get("embedding");
            if (emb == null || !emb.isArray() || emb.size() != cfg.embeddingDimension()) {
                throw new AiProviderException(AiProviderException.Kind.INVALID_RESPONSE,
                    "La dimensión del embedding no coincide con la configurada (" + cfg.embeddingDimension() + ").");
            }
            float[] v = new float[emb.size()];
            for (int i = 0; i < v.length; i++) v[i] = (float) emb.get(i).asDouble();
            out.add(v);
        }
        return out;
    }

    @Override
    public String chat(List<ChatMsg> messages) {
        List<Map<String, String>> msgs = new ArrayList<>();
        for (ChatMsg m : messages) msgs.add(Map.of("role", m.role(), "content", m.content()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", cfg.chatModel());
        body.put("messages", msgs);
        body.put("temperature", cfg.temperature());
        JsonNode json = post("/chat/completions", body);
        JsonNode content = json == null ? null : json.path("choices").path(0).path("message").path("content");
        if (content == null || content.isMissingNode() || content.isNull()) {
            throw new AiProviderException(AiProviderException.Kind.INVALID_RESPONSE,
                "El servicio de IA devolvió una respuesta inválida.");
        }
        return content.asText();
    }

    private JsonNode post(String path, Object body) {
        int attempt = 0;
        while (true) {
            try {
                return rest.post().uri(path).body(body).retrieve().body(JsonNode.class);
            } catch (HttpClientErrorException e) {
                int code = e.getStatusCode().value();
                log.warn("Proveedor de IA respondió HTTP {} en {}", code, path);
                if (code == 429) {
                    throw new AiProviderException(AiProviderException.Kind.QUOTA,
                        "Se alcanzó el límite de uso del servicio de IA. Inténtalo más tarde.");
                }
                throw new AiProviderException(AiProviderException.Kind.UNAVAILABLE,
                    "El servicio de IA rechazó la solicitud. Revisa la configuración del proveedor.");
            } catch (HttpServerErrorException | ResourceAccessException e) {
                attempt++;
                log.warn("Fallo transitorio del proveedor de IA en {} (intento {}): {}", path, attempt, e.getClass().getSimpleName());
                if (attempt > cfg.maxRetries()) {
                    throw new AiProviderException(AiProviderException.Kind.UNAVAILABLE,
                        "El servicio de IA no está disponible en este momento. Inténtalo nuevamente.");
                }
                try {
                    Thread.sleep(500L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new AiProviderException(AiProviderException.Kind.UNAVAILABLE, "Operación interrumpida.");
                }
            } catch (RestClientException e) {
                log.warn("Error al llamar al proveedor de IA en {}: {}", path, e.getClass().getSimpleName());
                throw new AiProviderException(AiProviderException.Kind.INVALID_RESPONSE,
                    "No se pudo interpretar la respuesta del servicio de IA.");
            }
        }
    }

    private static String trimSlash(String s) {
        return s != null && s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
