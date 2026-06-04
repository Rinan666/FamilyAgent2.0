package com.familyagent.common.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@MappedTypes(String[].class)
@MappedJdbcTypes(JdbcType.ARRAY)
public class StringArrayTypeHandler extends BaseTypeHandler<String[]> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String[] parameter, JdbcType jdbcType)
            throws SQLException {
        Array array = ps.getConnection().createArrayOf("text", parameter);
        ps.setArray(i, array);
    }

    @Override
    public String[] getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toStringArray(rs.getArray(columnName));
    }

    @Override
    public String[] getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toStringArray(rs.getArray(columnIndex));
    }

    @Override
    public String[] getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toStringArray(cs.getArray(columnIndex));
    }

    private String[] toStringArray(Array array) throws SQLException {
        if (array == null) {
            return new String[0];
        }
        Object raw = array.getArray();
        if (raw instanceof String[] strings) {
            return strings;
        }
        if (raw instanceof Object[] objects) {
            String[] values = new String[objects.length];
            for (int i = 0; i < objects.length; i++) {
                values[i] = objects[i] == null ? "" : objects[i].toString();
            }
            return values;
        }
        return new String[0];
    }
}
