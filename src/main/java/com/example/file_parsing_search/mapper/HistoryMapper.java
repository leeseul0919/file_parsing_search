package com.example.file_parsing_search.mapper;

import com.example.file_parsing_search.domain.History;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;

@Mapper
public interface HistoryMapper {
    @Insert("INSERT INTO history (endpoint, method, request_time, response_Time, status_code, duration, request_body, response_body) VALUES (#{endpoint}, #{method}, #{requestTime}, #{responseTime}, #{statusCode}, #{duration}, #{requestBody}::jsonb, #{responseBody}::jsonb)")
    int insert(History history);
}
