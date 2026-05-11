package com.huanf.noterag.client;

import java.util.List;

public interface EmbeddingClient {

    float[] embed(String text);

    List<float[]> embedAll(List<String> texts);
}
