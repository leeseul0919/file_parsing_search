package com.example.file_parsing_search.service;

import com.example.file_parsing_search.domain.History;
import com.example.file_parsing_search.dto.CapabilityDto;
import com.example.file_parsing_search.dto.GetObjectRequestDto;
import com.example.file_parsing_search.dto.GetObjectResponseDto;
import com.example.file_parsing_search.dto.SearchObject;
import com.example.file_parsing_search.mapper.HistoryMapper;
import com.example.file_parsing_search.parser.ObjectParser;
import com.example.file_parsing_search.util.FileManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FileService {
    private final FileManager fileManager;
    private final HistoryMapper historyMapper;

    @Autowired
    public FileService(FileManager fileManager, HistoryMapper historyMapper) {
        this.fileManager = fileManager;
        this.historyMapper = historyMapper;
        System.out.println("service start");
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

    public void saveHistory(History history) {
        System.out.println("file service insert method");
        historyMapper.insert(history);
    }
}
