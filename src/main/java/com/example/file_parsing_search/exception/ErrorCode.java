package com.example.file_parsing_search.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.json.HTTP;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    NOT_FOUND_FILE(HttpStatus.NOT_FOUND,"E001","요청한 파일을 찾을 수 없습니다."),
    INVALID_FILE_TYPE(HttpStatus.NOT_FOUND,"E002","지원하지 않는 파일 형식입니다."),
    INVALID_PARAMETERS(HttpStatus.NOT_FOUND,"E003","요청하신 coordinates가 double형이 아닙니다."),
    INVALID_RANGE_TYPE(HttpStatus.NOT_FOUND,"E004","요청하신 범위 객체 타입을 지원하지 않습니다."),
    NULL_FILE_PATH(HttpStatus.NOT_FOUND,"E003","요청 파라미터가 잘못되었습니다."),
    FAIL_OPEN_FILE(HttpStatus.NOT_FOUND,"E004","요청하신 파일을 여는데 실패했습니다."),
    NO_RESULTS_FOUND(HttpStatus.NOT_FOUND,"E005","요청하신 범위 내에 객체가 존재하지 않습니다."),
    FAIL_READ_GROUP_F(HttpStatus.NOT_FOUND,"H001","HDF5: 빈 파일 입니다."),
    UNKNOWN_CODING_FORMAT(HttpStatus.NOT_FOUND,"H001","HDF5: 알 수 없는 인코딩 포맷입니다."),
    NOT_SUPPORTED_CODING_FORMAT(HttpStatus.NOT_FOUND,"H001","HDF5: 지원하지 않는 인코딩 포맷입니다."),
    FAIL_READ_XML(HttpStatus.NOT_FOUND,"G001","GML: XML로 파싱이 불가능한 파일입니다.");
    //EMPTY_UPLOAD_FILES(HttpStatus.NOT_FOUND,"S500","업로드 폴더에 파일이 없습니다.");

    private final HttpStatus httpStatus;
    private final String errorCode;
    private final String message;
}
