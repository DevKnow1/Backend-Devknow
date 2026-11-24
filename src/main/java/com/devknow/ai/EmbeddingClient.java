package com.devknow.ai;

import java.util.List;

/** RNF-22: el flujo principal solo depende de esta interfaz. */
public interface EmbeddingClient {
    List<float[]> embed(List<String> texts);

    default float[] embedOne(String text) {
        return embed(List.of(text)).get(0);
    }
}
