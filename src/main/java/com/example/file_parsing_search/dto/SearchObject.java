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
    private Map<String, String> valueFields;
    private List<ObjGroupInfo> ObjList;
    private Map<String,String> Attribute;
    //private Map<String, Object> properties;

    public SearchObject() { }

    public SearchObject(String type, String id,List<String> fieldList, List<String> uomNameList, List<ObjGroupInfo> objGroupInfos, Map<String, String> attribute) {
        this.type = type;
        this.id = id;
        this.ObjList = objGroupInfos;
        this.Attribute = attribute;

        this.valueFields = new HashMap<>();
        if(fieldList!=null && uomNameList!=null) {
            for(int i=0;i<fieldList.size();i++) {
                this.valueFields.put(fieldList.get(i),uomNameList.get(i));
            }
        }
    }
}
