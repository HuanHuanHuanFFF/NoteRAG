package com.huanf.noterag.client;

import java.util.List;

public interface RerankClient {

    List<RerankResult> rerank(String query, List<String> documents, int topK, String instruct);
}
