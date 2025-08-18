package com.example.file_parsing_search.dto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

// Todo: 파싱 결과에 따라 구조 달라질수도
@Getter
@Setter
public class SearchObject {
    private String type;
    private String id;
    private GeometryInfo geometryInfo;
    //private Map<String, Object> properties;

    public SearchObject() { }

    public SearchObject(String type, String id,String geoType, List<?> coordinates) {
        this.type = type;
        this.id = id;

        this.geometryInfo = new GeometryInfo();
        geometryInfo.setType(geoType);
        geometryInfo.setCoordinates(coordinates);

        //this.properties = new HashMap<>();
        //properties.put("name", propName);
        //properties.put("description", propDesc);
    }
}
