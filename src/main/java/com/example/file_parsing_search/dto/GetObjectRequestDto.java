package com.example.file_parsing_search.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class GetObjectRequestDto {
    private String filePath;
    private GeometryInfo geometryInfo;

    public GetObjectRequestDto() { }
}
