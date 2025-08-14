package com.example.file_parsing_search.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    EMPTY_UPLOAD_FILES (HttpStatus.NOT_FOUND,"S500","업로드 폴더에 파일이 없습니다.");

    private final HttpStatus httpStatus;
    private final String errorCode;
    private final String message;
}
