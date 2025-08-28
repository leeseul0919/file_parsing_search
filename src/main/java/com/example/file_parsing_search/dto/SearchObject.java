package com.example.file_parsing_search.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SearchObject {
    private String type;
    private String id;
    private Map<String, String> valueFields;
    private Map<String,Object> Attribute;
    private List<GeometryInfo> geoInfo;
    private List<ObjGroupInfo> valueInfo;
    //private Map<String, Object> properties;

    public SearchObject() { }

    public SearchObject(String type, String id,List<String> fieldList, List<String> uomNameList, Map<String, Object> attribute, List<GeometryInfo> geoInfo, List<ObjGroupInfo> valueInfo) {
        this.type = type;
        this.id = id;
        this.Attribute = attribute;
        this.geoInfo = geoInfo;
        this.valueInfo = valueInfo;

        if(fieldList!=null && uomNameList!=null) {
            this.valueFields = new HashMap<>();
            for(int i=0;i<fieldList.size();i++) {
                this.valueFields.put(fieldList.get(i),uomNameList.get(i));
            }
        }
    }
}
