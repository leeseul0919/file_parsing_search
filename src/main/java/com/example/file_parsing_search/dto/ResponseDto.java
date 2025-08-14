package com.example.file_parsing_search.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ResponseDto<T> {
    private boolean success;
    private String message;
    private T data;

    public static <T> ResponseDto<T> success(T data) {
        return new ResponseDto<>(true, "요청이 성공적으로 처리되었습니다.", data);
    }

    public static <T> ResponseDto<T> fail(String message, T errorDetail) {
        return new ResponseDto<>(false, message, errorDetail);
    }
}