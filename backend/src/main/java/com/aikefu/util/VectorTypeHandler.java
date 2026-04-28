package com.aikefu.util;

import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeException;
import org.apache.ibatis.type.TypeHandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class VectorTypeHandler implements TypeHandler<float[]> {
    
    @Override
    public void setParameter(PreparedStatement ps, int i, float[] parameter, JdbcType jdbcType) throws SQLException {
        if (parameter == null) {
            ps.setNull(i, JdbcType.OTHER.TYPE_CODE);
        } else {
            try {
                // 使用 PostgreSQL 的 PGobject 来设置 vector 类型
                org.postgresql.util.PGobject pGobject = new org.postgresql.util.PGobject();
                pGobject.setType("vector");
                pGobject.setValue(arrayToString(parameter));
                ps.setObject(i, pGobject);
            } catch (Exception e) {
                throw new SQLException("Failed to set vector parameter", e);
            }
        }
    }
    
    @Override
    public float[] getResult(ResultSet rs, String columnName) throws SQLException {
        Object result = rs.getObject(columnName);
        return parseVector(result);
    }
    
    @Override
    public float[] getResult(ResultSet rs, int columnIndex) throws SQLException {
        Object result = rs.getObject(columnIndex);
        return parseVector(result);
    }
    
    @Override
    public float[] getResult(CallableStatement cs, int columnIndex) throws SQLException {
        Object result = cs.getObject(columnIndex);
        return parseVector(result);
    }
    
    private float[] parseVector(Object result) throws SQLException {
        if (result == null) {
            return null;
        }
        
        String str;
        if (result instanceof org.postgresql.util.PGobject) {
            str = ((org.postgresql.util.PGobject) result).getValue();
        } else {
            str = result.toString();
        }
        
        if (str == null || str.isEmpty()) {
            return null;
        }
        
        String[] parts = str.replace("[", "").replace("]", "").split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i].trim());
        }
        return vector;
    }
    
    private String arrayToString(float[] array) {
        if (array == null || array.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < array.length; i++) {
            sb.append(array[i]);
            if (i < array.length - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
