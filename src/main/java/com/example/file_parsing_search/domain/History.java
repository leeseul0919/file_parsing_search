package com.example.file_parsing_search.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class History {
    private Long id;
    private String endpoint;
    private String method;
    private LocalDateTime requestTime;
    private LocalDateTime responseTime;
    private Integer statusCode;
    private Integer duration;
    private String requestBody;   // JSON 문자열로 저장
    private String responseBody;
}
