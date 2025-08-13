package com.example.file_parsing_search.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CapabilityDto {
    private String fileName;
    private String fileType;
    private String filePath;
    private List<String> availableObjects;
    private List<Double> bbox;

    public CapabilityDto() {}

    public CapabilityDto(String fileName, String fileType, List<String> availableObjects, List<Double> bbox, String filePath) {
        this.fileName = fileName;
        this.fileType = fileType;
        this.availableObjects = availableObjects;
        this.bbox = bbox;
        this.filePath = filePath;
    }
}
