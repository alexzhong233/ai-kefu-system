package com.aikefu.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeException;
import org.apache.ibatis.type.TypeHandler;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

public class MapTypeHandler implements TypeHandler<Map<String, Object>> {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public void setParameter(PreparedStatement ps, int i, Map<String, Object> parameter, JdbcType jdbcType) throws SQLException {
        if (parameter == null) {
            ps.setNull(i, JdbcType.OTHER.TYPE_CODE);
        } else {
            try {
                String jsonString = objectMapper.writeValueAsString(parameter);
                PGobject jsonObject = new PGobject();
                jsonObject.setType("jsonb");
                jsonObject.setValue(jsonString);
                ps.setObject(i, jsonObject);
            } catch (JsonProcessingException e) {
                throw new TypeException("Error converting Map to JSON", e);
            }
        }
    }
    
    @Override
    public Map<String, Object> getResult(ResultSet rs, String columnName) throws SQLException {
        Object result = rs.getObject(columnName);
        return parseMap(result);
    }
    
    @Override
    public Map<String, Object> getResult(ResultSet rs, int columnIndex) throws SQLException {
        Object result = rs.getObject(columnIndex);
        return parseMap(result);
    }
    
    @Override
    public Map<String, Object> getResult(CallableStatement cs, int columnIndex) throws SQLException {
        Object result = cs.getObject(columnIndex);
        return parseMap(result);
    }
    
    private Map<String, Object> parseMap(Object result) throws SQLException {
        if (result == null) {
            return null;
        }
        
        try {
            if (result instanceof String) {
                return objectMapper.readValue((String) result, Map.class);
            } else {
                return objectMapper.convertValue(result, Map.class);
            }
        } catch (Exception e) {
            throw new TypeException("Error parsing JSON to Map", e);
        }
    }
}
