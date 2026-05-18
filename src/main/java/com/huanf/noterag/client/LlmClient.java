package com.huanf.noterag.client;

import com.huanf.noterag.prompt.RagPrompt;

public interface LlmClient {

    String chat(RagPrompt prompt);
}
