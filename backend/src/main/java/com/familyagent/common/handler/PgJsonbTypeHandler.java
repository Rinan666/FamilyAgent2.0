package com.familyagent.common.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * PostgreSQL-compatible JSONB type handler.
 *
 * Replaces MyBatis-Plus {@code JacksonTypeHandler} which uses
 * {@code ps.setString()} (mapped to VARCHAR in PostgreSQL) and
 * fails on jsonb columns with:
 *   "column is of type jsonb but expression is of type character varying"
 *
 * This handler writes via {@code ps.setObject(i, jsonStr, Types.OTHER)}
 * so the PostgreSQL JDBC driver treats the value as jsonb-compatible.
 */
@MappedTypes({Object.class})
@MappedJdbcTypes(JdbcType.OTHER)
public class PgJsonbTypeHandler extends BaseTypeHandler<Object> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType)
            throws SQLException {
        try {
            String json = MAPPER.writeValueAsString(parameter);
            ps.setObject(i, json, Types.OTHER);
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to serialize parameter as JSON", e);
        }
    }

    @Override
    public Object getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return deserialize(rs.getString(columnName));
    }

    @Override
    public Object getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return deserialize(rs.getString(columnIndex));
    }

    @Override
    public Object getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return deserialize(cs.getString(columnIndex));
    }

    private Object deserialize(String json) throws SQLException {
        if (json == null) {
            return null;
        }
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to deserialize JSON from database", e);
        }
    }
}
