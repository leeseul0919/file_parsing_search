package com.example.file_parsing_search.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class GetObjectResponseDto {
    private long searchTime;
    private List<SearchObject> features;

    public GetObjectResponseDto() { }

}
