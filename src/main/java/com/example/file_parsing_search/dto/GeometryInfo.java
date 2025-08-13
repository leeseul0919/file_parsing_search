package com.example.file_parsing_search.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class GeometryInfo {
    private String type;
    private List<?> coordinates;

    public GeometryInfo() { }
}
