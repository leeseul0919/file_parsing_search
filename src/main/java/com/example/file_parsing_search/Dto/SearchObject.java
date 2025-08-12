package com.example.file_parsing_search.Dto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SearchObject {
    private String type = "Feature";
    private GeometryInfo geometryInfo;
    private Map<String, Object> properties;

    public SearchObject() { }
    public SearchObject(String geoType, List<?> coordinates, String propName, String propDesc) {
        this.geometryInfo = new GeometryInfo();
        geometryInfo.setGeoType(geoType);
        geometryInfo.setCoordinates(coordinates);

        this.properties = new HashMap<>();
        properties.put("name", propName);
        properties.put("description", propDesc);
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public GeometryInfo getGeometry() { return geometryInfo; }
    public void setGeometry(GeometryInfo geometryInfo) { this.geometryInfo = geometryInfo; }
    public Map<String, Object> getProperties() { return properties; }
    public void setProperties(Map<String, Object> properties) { this.properties = properties; }
}
