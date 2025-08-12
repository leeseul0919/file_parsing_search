package com.example.file_parsing_search.controller;

import com.example.file_parsing_search.domain.History;
import com.example.file_parsing_search.dto.CapabilityDto;
import com.example.file_parsing_search.dto.GetObjectRequestDto;
import com.example.file_parsing_search.dto.GetObjectResponseDto;
import com.example.file_parsing_search.dto.ResponseDto;
import com.example.file_parsing_search.service.FileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api")
public class FileController {
    private final FileService fileService;
    private final ObjectMapper objectMapper;

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
            System.out.println("service complete");

            ResponseDto<List<CapabilityDto>> responseDto = ResponseDto.success(capabilities);
            System.out.println("convert to ResponseDto");

            String responseBodyJson = toJsonSafe(responseDto); //json 변환
            System.out.println("convert to Json");

            LocalDateTime responseTime = LocalDateTime.now();
            System.out.println("complete response");

            saveHistory("/capability","GET",requestTime,responseTime,HttpStatus.OK.value(),-1, requestBodyJson,responseBodyJson);

            return ResponseEntity.ok(responseDto);
        } catch (Exception e) {
            ResponseDto<List<CapabilityDto>> errorDto = ResponseDto.fail("E500", e.getMessage(), Collections.emptyList());
            String responseBodyJson = toJsonSafe(errorDto);

            LocalDateTime errorTime = LocalDateTime.now();

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
            GetObjectResponseDto responsedto = fileService.getObjectResponseDto(requestDto);
            long serviceEnd = System.currentTimeMillis();
            int serviceDurationMs = (int) (serviceEnd - serviceStart);

            ResponseDto<?> responseDto = ResponseDto.success(responsedto);
            String responseBodyJson = toJsonSafe(responseDto);
            LocalDateTime responseTime = LocalDateTime.now();

            saveHistory("/getObject","POST",requestTime,responseTime,HttpStatus.OK.value(),serviceDurationMs, requestBodyJson,responseBodyJson);

            return ResponseEntity.ok(responseDto);
        } catch (Exception e) {
            long serviceEnd = System.currentTimeMillis();
            int serviceDurationMs = (int) (serviceEnd - serviceStart);

            ResponseDto<?> errorDto = ResponseDto.fail("E500", e.getMessage(), Collections.emptyList());
            String responseBodyJson = toJsonSafe(errorDto);

            LocalDateTime errorTime = LocalDateTime.now();

            saveHistory("/getObject", "POST", requestTime, errorTime, HttpStatus.INTERNAL_SERVER_ERROR.value(), serviceDurationMs, requestBodyJson, responseBodyJson);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorDto);
        }
    }

    @GetMapping("/init")
    @Operation(summary = "Reload Files", description = "파일들 다시 로드하기")
    public ResponseEntity<ResponseDto<?>> uploadfileLoad() {
        try {
            fileService.fileinit();
            return ResponseEntity.ok(ResponseDto.success("ok"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseDto.fail("E500", e.getMessage(), Collections.emptyList()));
        }
    }

    private String toJsonSafe(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{\"error\": \"Failed to serialize object\"}";
        }
    }

    private void saveHistory(String endpoint, String method, LocalDateTime requestTime, LocalDateTime responseTime, int statusCode, int duration, String requestBodyJson, String responseBodyJson) {
        System.out.println("save history start");

        History history = new History();
        history.setEndpoint(endpoint);
        history.setMethod(method);
        history.setRequestTime(requestTime);
        history.setResponseTime(responseTime);
        history.setStatusCode(statusCode);
        history.setDuration(duration);
        history.setRequestBody(requestBodyJson);
        history.setResponseBody(responseBodyJson);

        System.out.println("history complete before save");

        fileService.saveHistory(history);
    }
}
