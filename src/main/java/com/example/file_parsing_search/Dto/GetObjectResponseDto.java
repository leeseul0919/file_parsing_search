package com.example.file_parsing_search.Dto;

import java.util.List;

public class GetObjectResponseDto {
    private long searchTime;
    private List<SearchObject> features;

    public GetObjectResponseDto() { }

    public long getSearchTime() { return searchTime; }
    public void setSearchTime(long searchTime) { this.searchTime = searchTime; }
    public List<SearchObject> getFeatures() { return features; }
    public void setFeatures(List<SearchObject> features) { this.features = features; }
}
