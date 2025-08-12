package com.example.file_parsing_search.dto;

public class GetObjectRequestDto {
    private String fileName;
    private GeometryInfo geometryInfo;

    public GetObjectRequestDto() { }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public GeometryInfo getGeometry() { return geometryInfo; }
    public void setGeometryInfo(GeometryInfo geometryInfo) { this.geometryInfo = geometryInfo; }
}
