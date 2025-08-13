package com.example.file_parsing_search.controller;

import com.example.file_parsing_search.domain.History;
import com.example.file_parsing_search.dto.CapabilityDto;
import com.example.file_parsing_search.dto.GetObjectRequestDto;
import com.example.file_parsing_search.dto.GetObjectResponseDto;
import com.example.file_parsing_search.dto.ResponseDto;
import com.example.file_parsing_search.service.FileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api")
@Slf4j
public class FileController {
    private final FileService fileService;
    private final ObjectMapper objectMapper;
    //private static final Logger logger = LoggerFactory.getLogger(FileController.class);

    @Autowired
    public FileController(FileService fileService, ObjectMapper objectMapper) {
        this.fileService = fileService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "test", description = "test return hello")
    @GetMapping("/hello")
    public ResponseEntity<ResponseDto<String>> helloTest() {
        return ResponseEntity.ok(ResponseDto.success(fileService.hellotest()));
    }

    @Operation(summary = "View all Services", description = "모든 파일의 객체 종류 리스트와 BBOX 반환")
    @GetMapping("/capability")
    public ResponseEntity<ResponseDto<List<CapabilityDto>>> capability() {
        LocalDateTime requestTime = LocalDateTime.now();
        String requestBodyJson = null;

        try {
            List<CapabilityDto> capabilities = fileService.capabilityService();
            log.info("capability process complete");

            ResponseDto<List<CapabilityDto>> responseDto = ResponseDto.success(capabilities);
            String responseBodyJson = toJsonSafe(responseDto); //json 변환
            LocalDateTime responseTime = LocalDateTime.now();
            log.info("Success to get service scope");

            saveHistory("/capability","GET",requestTime,responseTime,HttpStatus.OK.value(),-1, requestBodyJson,responseBodyJson);
            return ResponseEntity.ok(responseDto);
        } catch (Exception e) {
            ResponseDto<List<CapabilityDto>> errorDto = ResponseDto.fail("E500", e.getMessage(), Collections.emptyList());
            String responseBodyJson = toJsonSafe(errorDto);
            LocalDateTime errorTime = LocalDateTime.now();
            log.error("error: {}","Fail to get service scope - " + e.getMessage());

            saveHistory("/capability", "GET", requestTime, errorTime, HttpStatus.INTERNAL_SERVER_ERROR.value(), -1, requestBodyJson, responseBodyJson);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorDto);
        }
    }

    @Operation(summary = "Search Object Info", description = "특정 파일의 특정 범위에 있는 객체들 정보를 반환")
    @PostMapping("/getObject")
    public ResponseEntity<ResponseDto<?>> getObject(@RequestBody GetObjectRequestDto requestDto) {
        LocalDateTime requestTime = LocalDateTime.now();
        String requestBodyJson = toJsonSafe(requestDto);
        long serviceStart = System.currentTimeMillis();

        try {
            GetObjectResponseDto getobjectResponse = fileService.getObjectResponseDto(requestDto);
            log.info("getObject process complete");

            ResponseDto<?> responseDto = ResponseDto.success(getobjectResponse);
            String responseBodyJson = toJsonSafe(responseDto);
            LocalDateTime responseTime = LocalDateTime.now();
            log.info("Success to search file");

            saveHistory("/getObject","POST",requestTime,responseTime,HttpStatus.OK.value(),getobjectResponse.getSearchTime(), requestBodyJson,responseBodyJson);
            return ResponseEntity.ok(responseDto);
        } catch (Exception e) {
            long serviceEnd = System.currentTimeMillis();
            long serviceDurationMs = (long) (serviceEnd - serviceStart);

            ResponseDto<?> errorDto = ResponseDto.fail("E500", e.getMessage(), Collections.emptyList());
            String responseBodyJson = toJsonSafe(errorDto);
            LocalDateTime errorTime = LocalDateTime.now();
            log.error("error: {}","Fail to search file - " + e.getMessage());

            saveHistory("/getObject", "POST", requestTime, errorTime, HttpStatus.INTERNAL_SERVER_ERROR.value(), serviceDurationMs, requestBodyJson, responseBodyJson);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorDto);
        }
    }

    @GetMapping("/init")
    @Operation(summary = "Reload Files", description = "파일들 다시 로드하기")
    public ResponseEntity<ResponseDto<?>> uploadfileLoad() {
        try {
            fileService.fileinit();
            log.info("Success to reload files");
            return ResponseEntity.ok(ResponseDto.success("ok"));
        } catch (Exception e) {
            log.error("error:{}", "Fail to reload files - " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseDto.fail("E500", e.getMessage(), Collections.emptyList()));
        }
    }

    private String toJsonSafe(Object obj) {
        try {
            log.info("Success to serialize");
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("error:{}", "Failed to serialize - " + e.getMessage());
            return "{\"error\": \"Failed to serialize\"}";
        }
    }

    private void saveHistory(String endpoint, String method, LocalDateTime requestTime, LocalDateTime responseTime, int statusCode, long duration, String requestBodyJson, String responseBodyJson) {
        try {
            History history = new History();
            history.setEndpoint(endpoint);
            history.setMethod(method);
            history.setRequestTime(requestTime);
            history.setResponseTime(responseTime);
            history.setStatusCode(statusCode);
            history.setDuration(duration);
            history.setRequestBody(requestBodyJson);
            history.setResponseBody(responseBodyJson);

            fileService.saveHistory(history);
            log.info("save success");
        } catch (Exception e) {
            log.error("error:{}", "save fail - " + e.getMessage());
            //System.out.println();
        }
    }
}
