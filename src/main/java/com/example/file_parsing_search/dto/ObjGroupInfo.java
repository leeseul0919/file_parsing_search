package com.example.file_parsing_search.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class ObjGroupInfo {
    private LocalDateTime timePoint;
    private List<ObjInfo> objInfo;
    public ObjGroupInfo() { }
}
