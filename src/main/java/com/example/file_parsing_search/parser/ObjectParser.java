package com.example.file_parsing_search.parser;

import com.example.file_parsing_search.dto.CapabilityDto;
import com.example.file_parsing_search.dto.GetObjectRequestDto;
import com.example.file_parsing_search.dto.SearchObject;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;
import java.util.List;

public interface ObjectParser {
    String getSupportedFileType();
    CapabilityDto getcapability(String filePath, String fileType) throws ParserConfigurationException, IOException, SAXException;
    List<SearchObject> parse(GetObjectRequestDto request) throws Exception;  //GetObject 작업
}
