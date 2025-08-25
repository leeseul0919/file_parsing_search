package com.example.file_parsing_search.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class HDFObj {
    private String objType;
    private List<String> codeList;
    private List<String> uomNameList;

    public HDFObj() { }

    public HDFObj(String objType, List<String> codeList, List<String> uomNameList) {
        this.objType = objType;
        this.codeList = codeList;
        this.uomNameList = uomNameList;
    }
}
