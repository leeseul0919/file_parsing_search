package com.example.file_parsing_search.parser;

import com.example.file_parsing_search.dto.CapabilityDto;
import com.example.file_parsing_search.dto.GetObjectRequestDto;
import com.example.file_parsing_search.dto.SearchObject;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public interface ObjectParser {
    String getSupportedFileType();
    CapabilityDto getcapability(String filePath, String fileType);
    List<SearchObject> parse(CapabilityDto fileInfo, GetObjectRequestDto request);  //GetObject 작업
}
