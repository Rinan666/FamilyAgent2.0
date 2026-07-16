package com.familyagent.common.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

public abstract class TypedPgJsonbTypeHandler<T> extends BaseTypeHandler<T> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JavaType targetType;

    protected TypedPgJsonbTypeHandler(Class<T> targetType) {
        this(MAPPER.getTypeFactory().constructType(targetType));
    }

    protected TypedPgJsonbTypeHandler(JavaType targetType) {
        this.targetType = targetType;
    }

    protected static JavaType listType(Class<?> elementType) {
        return MAPPER.getTypeFactory().constructCollectionType(java.util.List.class, elementType);
    }

    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, T parameter, JdbcType jdbcType)
            throws SQLException {
        try {
            statement.setObject(index, MAPPER.writeValueAsString(parameter), Types.OTHER);
        } catch (JsonProcessingException error) {
            throw new SQLException("Failed to serialize typed JSON parameter", error);
        }
    }

    @Override
    public T getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return deserialize(resultSet.getString(columnName));
    }

    @Override
    public T getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return deserialize(resultSet.getString(columnIndex));
    }

    @Override
    public T getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return deserialize(statement.getString(columnIndex));
    }

    private T deserialize(String json) throws SQLException {
        if (json == null) {
            return null;
        }
        try {
            return MAPPER.readValue(json, targetType);
        } catch (JsonProcessingException error) {
            throw new SQLException("Failed to deserialize typed JSON value", error);
        }
    }
}
