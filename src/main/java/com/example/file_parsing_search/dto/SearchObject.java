package com.example.file_parsing_search.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

// Todo: 파싱 결과에 따라 구조 달라질수도
@Getter
@Setter
public class SearchObject {
    private String type;
    private String id;
    private Map<String, String> fieldInfo;
    private List<ObjGroupInfo> objGroupInfos;
    //private Map<String, Object> properties;

    public SearchObject() { }

    public SearchObject(String type, String id,List<String> fieldList, List<String> uomNameList, List<ObjGroupInfo> objGroupInfos) {
        this.type = type;
        this.id = id;
        this.objGroupInfos = objGroupInfos;

        this.fieldInfo = new HashMap<>();
        if(fieldList!=null && uomNameList!=null) {
            for(int i=0;i<fieldList.size();i++) {
                this.fieldInfo.put(fieldList.get(i),uomNameList.get(i));
            }
        }
    }
}
