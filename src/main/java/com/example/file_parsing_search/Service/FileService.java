package com.example.file_parsing_search.Service;

import com.example.file_parsing_search.Dto.CapabilityDto;
import com.example.file_parsing_search.Dto.GetObjectRequestDto;
import com.example.file_parsing_search.Dto.GetObjectResponseDto;
import com.example.file_parsing_search.Dto.SearchObject;
import com.example.file_parsing_search.Parser.ObjectParser;
import com.example.file_parsing_search.Util.FileManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class FileService {
    private final FileManager fileManager;

    @Autowired
    public FileService(FileManager fileManager) {
        this.fileManager = fileManager;
    }

    public String hellotest() {
        return "Hello Spring Boot";
    }

    public void fileinit() {
        fileManager.init();
    }

    public List<CapabilityDto> capabilityService() {
        return fileManager.capabilityResponse();
    }

    public GetObjectResponseDto getObjectResponseDto(GetObjectRequestDto request) {
        GetObjectResponseDto responsedto = new GetObjectResponseDto();

        long startTime = System.currentTimeMillis();

        //fileManager.getFilePath()
        //1. 파일명 받은걸로 파일 정보 불러오고
        CapabilityDto fileInfo = fileManager.getFileInfo(request.getFileName())
                .orElseThrow(() -> new RuntimeException("파일을 찾을 수 없습니다: " + request.getFileName()));

        //2. 파일 타입에 따른 파서 불러와서 작업 (파일 정보, request)
        ObjectParser parser = fileManager.getParserByFileType(fileInfo.getFileType());
        List<SearchObject> objectsList = parser.parse(fileInfo,request);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        //3. responsedto 값 설정
        responsedto.setSearchTime(duration);
        responsedto.setFeatures(objectsList);

        return responsedto;
    }
}
