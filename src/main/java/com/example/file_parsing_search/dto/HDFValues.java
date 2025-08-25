package com.example.file_parsing_search.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class HDFValues {
    private String groupId;
    private LocalDateTime timePoint;
    private int timeInterval;
    private List<String> fieldList;
    private List<String> uomNameList;
    private List<Object> rawValues;

    public HDFValues() { }
    public HDFValues(String groupId, LocalDateTime timePoint, List<Object> rawValues, int timeInterval, List<String> fieldList, List<String> uomNameList) {
        this.groupId = groupId;
        this.timePoint = timePoint;
        this.rawValues = rawValues;
        this.timeInterval = timeInterval;
        this.fieldList = fieldList;
        this.uomNameList = uomNameList;
    }
}