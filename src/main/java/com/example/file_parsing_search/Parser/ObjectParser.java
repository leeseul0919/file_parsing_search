package com.example.file_parsing_search.Parser;

import com.example.file_parsing_search.Dto.CapabilityDto;
import com.example.file_parsing_search.Dto.GeometryInfo;
import com.example.file_parsing_search.Dto.GetObjectRequestDto;
import com.example.file_parsing_search.Dto.SearchObject;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public interface ObjectParser {
    String getSupportedFileType();
    CapabilityDto getcapability(String filePath, String fileType);
    List<SearchObject> parse(CapabilityDto fileInfo, GetObjectRequestDto request);  //GetObject 작업
}
