package com.huanf.noterag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.huanf.noterag.dto.ImportTextRequest;
import com.huanf.noterag.dto.ImportTextResponse;
import com.huanf.noterag.mapper.NoteChunkMapper;
import com.huanf.noterag.mapper.NoteMapper;
import com.huanf.noterag.model.Note;
import com.huanf.noterag.model.NoteChunk;
import com.huanf.noterag.util.EstimatedTokenCounter;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=",
        "spring.datasource.url=jdbc:h2:mem:noterag;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-h2.sql",
        "spring.ai.model.chat=none",
        "spring.ai.model.embedding=none",
        "spring.ai.model.image=none",
        "spring.ai.model.audio.speech=none",
        "spring.ai.model.audio.transcription=none",
        "spring.ai.model.moderation=none"
})
class NoteImportServiceIntegrationTests {

    @MockitoBean
    private EmbeddingModel embeddingModel;

    @Autowired
    private NoteImportService noteImportService;

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private NoteChunkMapper noteChunkMapper;

    @Test
    void importTextPersistsDocumentAndChunks() {
        String rawContent = """
                # Java\r
                \r
                Java notes.\r
                \r
                ## Collections\r
                \r
                HashMap notes.\r
                """;
        String normalizedContent = rawContent.replace("\r\n", "\n").replace('\r', '\n');

        ImportTextResponse response = noteImportService.importText(new ImportTextRequest("  Java Guide  ", rawContent));

        assertThat(response.getDocumentId()).isNotNull();
        assertThat(response.getChunkCount()).isEqualTo(2);
        assertThat(response.getCharCount()).isEqualTo(normalizedContent.length());
        assertThat(response.getTokenCount()).isEqualTo(EstimatedTokenCounter.estimate(normalizedContent));

        Note savedNote = noteMapper.findById(response.getDocumentId());
        assertThat(savedNote).isNotNull();
        assertThat(savedNote.getTitle()).isEqualTo("Java Guide");
        assertThat(savedNote.getContent()).isEqualTo(normalizedContent);
        assertThat(savedNote.getCharCount()).isEqualTo(normalizedContent.length());
        assertThat(savedNote.getTokenCount()).isEqualTo(EstimatedTokenCounter.estimate(normalizedContent));

        List<NoteChunk> savedChunks = noteChunkMapper.findByDocumentId(response.getDocumentId());
        assertThat(savedChunks).hasSize(2);
        assertThat(savedChunks)
                .extracting(NoteChunk::getNoteId)
                .containsOnly(response.getDocumentId());
        assertThat(savedChunks)
                .extracting(NoteChunk::getChunkIndex)
                .containsExactly(0, 1);
        assertThat(savedChunks)
                .extracting(NoteChunk::getHeadingPath)
                .containsExactly("Java", "Java > Collections");
        assertThat(savedChunks.get(0).getContent()).isEqualTo("Java notes.");
        assertThat(savedChunks.get(0).getCharCount()).isEqualTo("Java notes.".length());
        assertThat(savedChunks.get(0).getTokenCount()).isEqualTo(EstimatedTokenCounter.estimate("Java notes."));
        assertThat(savedChunks.get(1).getContent()).isEqualTo("HashMap notes.");
        assertThat(savedChunks.get(1).getCharCount()).isEqualTo("HashMap notes.".length());
        assertThat(savedChunks.get(1).getTokenCount()).isEqualTo(EstimatedTokenCounter.estimate("HashMap notes."));
    }

    @Test
    void importTextRejectsBlankTitleAfterNormalization() {
        assertThatThrownBy(() -> noteImportService.importText(new ImportTextRequest("   ", "# Java\n\nnotes")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("title must not be blank");
    }
}
