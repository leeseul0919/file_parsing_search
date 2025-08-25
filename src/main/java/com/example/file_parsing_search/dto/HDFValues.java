package com.example.file_parsing_search.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class HDFValues {
    private String groupId;
    private LocalDateTime timePoint;
    private Map<String, String> fieldInfo;
    private List<Object> rawValues;
    private int timeInterval;

    public HDFValues() { }
    public HDFValues(String groupId, LocalDateTime timePoint, List<Object> rawValues, int timeInterval, List<String> fieldList, List<String> uomNameList) {
        this.groupId = groupId;
        this.timePoint = timePoint;
        this.rawValues = rawValues;
        this.timeInterval = timeInterval;

        this.fieldInfo = new HashMap<>();
        for(int i=0;i<fieldList.size();i++) {
            this.fieldInfo.put(fieldList.get(i),uomNameList.get(i));
        }
    }
}