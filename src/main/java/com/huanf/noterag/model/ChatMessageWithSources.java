package com.huanf.noterag.model;

import java.util.List;

import com.huanf.noterag.entity.ChatMessage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageWithSources {

    private ChatMessage message;
    private List<RetrievedChunk> sources;
}
