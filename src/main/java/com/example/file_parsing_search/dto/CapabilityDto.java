package com.example.file_parsing_search.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

public class CapabilityDto {
    private String fileName;
    private String fileType;
    @JsonIgnore
    private String filePath;
    private List<String> availableObjects;
    private List<Integer> bbox;

    public CapabilityDto() {}

    public CapabilityDto(String fileName, String fileType, List<String> availableObjects, List<Integer> bbox, String filePath) {
        this.fileName = fileName;
        this.fileType = fileType;
        this.availableObjects = availableObjects;
        this.bbox = bbox;
        this.filePath = filePath;
    }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public List<String> getAvailableObjects() { return availableObjects; }
    public void setAvailableObjects(List<String> availableObjects) { this.availableObjects = availableObjects; }
    public List<Integer> getBbox() { return bbox; }
    public void setBbox(List<Integer> bbox) { this.bbox = bbox; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
}
