package com.devknow.ai;

import com.devknow.config.AppProperties;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Proveedor simulado para desarrollo y pruebas sin costo (RNF-26): embeddings por bolsa de palabras con hashing
 * y respuestas extractivas. NO es semánticamente equivalente a un modelo real.
 */
@Component
@ConditionalOnProperty(prefix = "devknow.ai", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockAiClient implements EmbeddingClient, LlmClient {
    private final int dimension;

    public MockAiClient(AppProperties props) {
        this.dimension = props.ai().embeddingDimension();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> out = new ArrayList<>(texts.size());
        for (String t : texts) out.add(vector(t));
        return out;
    }

    private float[] vector(String text) {
        float[] v = new float[dimension];
        String norm = Normalizer.normalize(text.toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        for (String tok : norm.split("[^a-z0-9]+")) {
            if (tok.length() < 3) continue;
            v[Math.floorMod(tok.hashCode(), dimension)] += 1f;
        }
        double sum = 0;
        for (float x : v) sum += x * x;
        if (sum == 0) {
            v[0] = 1f;
            return v;
        }
        float inv = (float) (1.0 / Math.sqrt(sum));
        for (int i = 0; i < v.length; i++) v[i] *= inv;
        return v;
    }

    @Override
    public String chat(List<ChatMsg> messages) {
        String last = messages.get(messages.size() - 1).content();
        int s = last.indexOf("CONTEXTO:");
        int q = last.indexOf("PREGUNTA:");
        String ctx = (s >= 0 && q > s) ? last.substring(s + 9, q).trim() : last;
        if (ctx.length() > 400) ctx = ctx.substring(0, 400) + "…";
        return "(Respuesta simulada del proveedor mock) Según la documentación: " + ctx + " [1]";
    }
}
