package com.example.file_parsing_search.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ObjInfo {
    private String geoType;
    private List<Object> coordinates;
    private List<Object> values;
    public ObjInfo() { }
    public ObjInfo(String geoType,List<Object> coordinates, List<Object> values) {
        this.geoType = geoType;
        this.coordinates = coordinates;
        this.values = values;
    }
}
