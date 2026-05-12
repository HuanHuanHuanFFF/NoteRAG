package com.huanf.noterag.mapper.typehandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PgVectorFloatArrayTypeHandlerTests {

    @Test
    void toVectorLiteralFormats1024DimensionalEmbedding() {
        float[] embedding = new float[1024];
        embedding[0] = 0.25f;
        embedding[1] = -1.5f;
        embedding[1023] = 2.0f;

        String literal = PgVectorFloatArrayTypeHandler.toVectorLiteral(embedding);

        assertThat(literal).startsWith("[0.25,-1.5,0.0");
        assertThat(literal).endsWith(",2.0]");
        assertThat(literal.chars().filter(ch -> ch == ',').count()).isEqualTo(1023);
    }

    @Test
    void toVectorLiteralRejectsWrongDimension() {
        assertThatThrownBy(() -> PgVectorFloatArrayTypeHandler.toVectorLiteral(new float[1023]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("embedding must be 1024 dimensions");
    }

    @Test
    void toVectorLiteralRejectsNonFiniteValue() {
        float[] embedding = new float[1024];
        embedding[12] = Float.NaN;

        assertThatThrownBy(() -> PgVectorFloatArrayTypeHandler.toVectorLiteral(embedding))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("embedding values must be finite");
    }
}
