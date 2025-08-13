package com.example.file_parsing_search.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class GetObjectRequestDto {
    private String fileName;
    private GeometryInfo geometryInfo;

    public GetObjectRequestDto() { }
}
