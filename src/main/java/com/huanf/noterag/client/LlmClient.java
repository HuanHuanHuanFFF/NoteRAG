package com.huanf.noterag.client;


import com.huanf.noterag.rag.RagPrompt;

public interface LlmClient {

    String chat(RagPrompt prompt);
}
