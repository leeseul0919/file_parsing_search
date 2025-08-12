package com.example.file_parsing_search.Dto;

import java.util.List;

public class CapabilityDto {
    private String fileName;
    private String fileType;

    private List<String> availableObjects;
    private List<Integer> bbox;

    public CapabilityDto() {}

    public CapabilityDto(String filePath, String fileType, List<String> availableObjects, List<Integer> bbox) {
        this.fileName = filePath;
        this.fileType = fileType;
        this.availableObjects = availableObjects;
        this.bbox = bbox;
    }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public List<String> getAvailableObjects() { return availableObjects; }
    public void setAvailableObjects(List<String> availableObjects) { this.availableObjects = availableObjects; }
    public List<Integer> getBbox() { return bbox; }
    public void setBbox(List<Integer> bbox) { this.bbox = bbox; }
}
