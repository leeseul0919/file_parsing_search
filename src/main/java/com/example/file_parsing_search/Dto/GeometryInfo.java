package com.example.file_parsing_search.Dto;

import java.util.List;

public class GeometryInfo {
    private String type;
    private List<?> coordinates;

    public GeometryInfo() { }

    public String getGeoType() { return type; }
    public void setGeoType(String type) { this.type = type; }
    public List<?> getCoordinates() { return coordinates; }
    public void setCoordinates(List<?> coordinates) { this.coordinates = coordinates; }
}
