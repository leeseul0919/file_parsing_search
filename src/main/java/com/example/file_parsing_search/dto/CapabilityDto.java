package com.example.file_parsing_search.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CapabilityDto {
    private String filePath;
    private String fileType;
    private List<String> availableObjects;
    private String epsg;
    private List<List<Double>> bbox;

    public CapabilityDto() {}

    public CapabilityDto(String fileType, List<String> availableObjects, List<List<Double>> bbox, String filePath, String epsg) {
        this.fileType = fileType;
        this.availableObjects = availableObjects;
        this.bbox = bbox;
        this.filePath = filePath;
        this.epsg = epsg;
    }
}
