package com.example.file_parsing_search.Controller;

import com.example.file_parsing_search.Dto.CapabilityDto;
import com.example.file_parsing_search.Dto.GetObjectRequestDto;
import com.example.file_parsing_search.Dto.GetObjectResponseDto;
import com.example.file_parsing_search.Service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api")
public class FileController {
    private final FileService fileService;

    @Autowired
    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @Operation(summary = "test", description = "test return hello")
    @GetMapping("/hello")
    public String helloTest() {
        return fileService.hellotest();
    }

    @Operation(summary = "View all Services", description = "모든 파일의 객체 종류 리스트와 BBOX 반환")
    @GetMapping("/capability")
    public ResponseEntity<List<CapabilityDto>> capability() {
        try {
            List<CapabilityDto> capabilities = fileService.capabilityService();
            return ResponseEntity.ok(capabilities);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.emptyList());
        }
    }

    @Operation(summary = "Search Object Info", description = "특정 파일의 특정 범위에 있는 객체들 정보를 반환")
    @PostMapping("/getObject")
    public ResponseEntity<?> getObject(@RequestBody GetObjectRequestDto requestDto) {
        try {
            GetObjectResponseDto responsedto = fileService.getObjectResponseDto(requestDto);
            return ResponseEntity.ok(responsedto);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @GetMapping("/init")
    @Operation(summary = "Reload Files", description = "파일들 다시 로드하기")
    public ResponseEntity<?> uploadfileLoad() {
        try {
            fileService.fileinit();
            return ResponseEntity.ok("ok");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }
}
