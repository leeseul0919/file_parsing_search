package com.example.file_parsing_search.parser;

import com.example.file_parsing_search.dto.CapabilityDto;
import com.example.file_parsing_search.dto.GetObjectRequestDto;
import com.example.file_parsing_search.dto.SearchObject;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.List;

public interface ObjectParser {
    String getSupportedFileType();
    CapabilityDto getcapability(String filePath, String fileType) throws ParserConfigurationException, IOException, SAXException;
    List<SearchObject> parse(CapabilityDto fileInfo, GetObjectRequestDto request);  //GetObject 작업
}
