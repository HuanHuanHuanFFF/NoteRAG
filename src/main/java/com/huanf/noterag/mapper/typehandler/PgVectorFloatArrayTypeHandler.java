package com.huanf.noterag.mapper.typehandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

public class PgVectorFloatArrayTypeHandler extends BaseTypeHandler<float[]> {

    public static final int VECTOR_DIMENSION = 1024;

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, float[] parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, toVectorLiteral(parameter));
    }

    @Override
    public float[] getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parseVectorLiteral(rs.getString(columnName));
    }

    @Override
    public float[] getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parseVectorLiteral(rs.getString(columnIndex));
    }

    @Override
    public float[] getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parseVectorLiteral(cs.getString(columnIndex));
    }

    static String toVectorLiteral(float[] embedding) {
        if (embedding == null) {
            throw new IllegalArgumentException("embedding must not be null");
        }
        if (embedding.length != VECTOR_DIMENSION) {
            throw new IllegalArgumentException("embedding must be 1024 dimensions");
        }

        StringBuilder literal = new StringBuilder(embedding.length * 8);
        literal.append('[');
        for (int i = 0; i < embedding.length; i++) {
            float value = embedding[i];
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("embedding values must be finite");
            }
            if (i > 0) {
                literal.append(',');
            }
            literal.append(Float.toString(value));
        }
        literal.append(']');
        return literal.toString();
    }

    private static float[] parseVectorLiteral(String literal) {
        if (literal == null) {
            return null;
        }

        String trimmed = literal.trim();
        if (trimmed.length() < 2 || trimmed.charAt(0) != '[' || trimmed.charAt(trimmed.length() - 1) != ']') {
            throw new IllegalArgumentException("vector literal must be bracketed");
        }

        String body = trimmed.substring(1, trimmed.length() - 1).trim();
        if (body.isEmpty()) {
            throw new IllegalArgumentException("embedding must be 1024 dimensions");
        }

        String[] parts = body.split(",");
        List<Float> values = new ArrayList<>(parts.length);
        for (String part : parts) {
            float value = Float.parseFloat(part.trim());
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("embedding values must be finite");
            }
            values.add(value);
        }

        float[] embedding = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            embedding[i] = values.get(i);
        }
        if (embedding.length != VECTOR_DIMENSION) {
            throw new IllegalArgumentException("embedding must be 1024 dimensions");
        }
        return embedding;
    }
}
