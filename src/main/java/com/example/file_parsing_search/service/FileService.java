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
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.List;

@Service
public class FileService {
    private final FileManager fileManager;
    private final HistoryMapper historyMapper;

    @Autowired
    public FileService(FileManager fileManager, HistoryMapper historyMapper) {
        this.fileManager = fileManager;
        this.historyMapper = historyMapper;
    }

    public String hellotest() {
        return "Hello Spring Boot";
    }

    public void fileinit() throws ParserConfigurationException, IOException, SAXException {
        fileManager.init();
    }

    public List<CapabilityDto> capabilityService() {
        return fileManager.capabilityResponse();
    }

    public GetObjectResponseDto getObjectResponseDto(GetObjectRequestDto request) throws Exception {
        GetObjectResponseDto responsedto = new GetObjectResponseDto();

        //1. 파일명 받은걸로 파일 정보 불러오고
        // Todo: 파일 못 찾았을 때 예외 발생
        CapabilityDto fileInfo = fileManager.getFileInfo(request.getFilePath())
                .orElseThrow(() -> new RuntimeException("파일을 찾을 수 없습니다: " + request.getFilePath()));

        //2. 파일 타입에 따른 파서 불러와서 작업 (파일 정보, request)
        // Todo: 해당 파일에 해당하는 파서 불러올 때 예외 발생?
        ObjectParser parser = fileManager.getParserByFileType(fileInfo.getFileType());
        if (parser == null) {
            throw new UnsupportedOperationException("Unsupported file type: " + fileInfo.getFileType());
        }
        // Todo: 파싱할 때 예외 발생? -- 이건 추후 파싱 로직 짜고 생각하기
        List<SearchObject> objectsList = parser.parse(request);

        responsedto.setFeatures(objectsList);

        return responsedto;
    }

    public void saveHistory(History history) {
        historyMapper.insert(history);
    }
}
