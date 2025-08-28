package com.example.file_parsing_search.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ObjInfo {
    private List<Integer> Index;
    private List<Object> Values;
    public ObjInfo() { }
    public ObjInfo(List<Integer> index, List<Object> Values) {
        this.Index = index;
        this.Values = Values;
    }
}
