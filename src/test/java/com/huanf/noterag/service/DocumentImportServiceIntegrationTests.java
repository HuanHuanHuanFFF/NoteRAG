package com.huanf.noterag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.huanf.noterag.dto.ImportTextRequest;
import com.huanf.noterag.dto.ImportTextResponse;
import com.huanf.noterag.mapper.DocumentChunkMapper;
import com.huanf.noterag.mapper.DocumentMapper;
import com.huanf.noterag.model.Document;
import com.huanf.noterag.model.DocumentChunk;
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
    class DocumentImportServiceIntegrationTests {

    @Autowired
    private DocumentImportService documentImportService;

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private DocumentChunkMapper documentChunkMapper;

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

        ImportTextResponse response = documentImportService.importText(new ImportTextRequest("  Java Guide  ", rawContent));

        assertThat(response.getDocumentId()).isNotNull();
        assertThat(response.getChunkCount()).isEqualTo(2);
        assertThat(response.getCharCount()).isEqualTo(normalizedContent.length());
        assertThat(response.getTokenCount()).isEqualTo(EstimatedTokenCounter.estimate(normalizedContent));

        Document savedDocument = documentMapper.findById(response.getDocumentId());
        assertThat(savedDocument).isNotNull();
        assertThat(savedDocument.getTitle()).isEqualTo("Java Guide");
        assertThat(savedDocument.getContent()).isEqualTo(normalizedContent);
        assertThat(savedDocument.getCharCount()).isEqualTo(normalizedContent.length());
        assertThat(savedDocument.getTokenCount()).isEqualTo(EstimatedTokenCounter.estimate(normalizedContent));

        List<DocumentChunk> savedChunks = documentChunkMapper.findByDocumentId(response.getDocumentId());
        assertThat(savedChunks).hasSize(2);
        assertThat(savedChunks)
                .extracting(DocumentChunk::getDocumentId)
                .containsOnly(response.getDocumentId());
        assertThat(savedChunks)
                .extracting(DocumentChunk::getChunkIndex)
                .containsExactly(0, 1);
        assertThat(savedChunks)
                .extracting(DocumentChunk::getHeadingPath)
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
        assertThatThrownBy(() -> documentImportService.importText(new ImportTextRequest("   ", "# Java\n\nnotes")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("title must not be blank");
    }
}
