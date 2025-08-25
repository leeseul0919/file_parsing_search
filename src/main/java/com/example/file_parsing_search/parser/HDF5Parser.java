package com.example.file_parsing_search.parser;

import com.example.file_parsing_search.dto.*;
import com.fasterxml.jackson.core.util.Separators;
import io.jhdf.HdfFile;
import io.jhdf.api.Attribute;
import io.jhdf.api.Dataset;
import io.jhdf.api.Group;
import io.jhdf.api.Node;
import io.jhdf.object.datatype.CompoundDataType;
import io.jhdf.object.datatype.DataType;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Polygon;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.lang.model.util.Elements;
import java.awt.*;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

@Component
@Slf4j
public class HDF5Parser implements ObjectParser{
    private final Map<Integer, String> dataCodingFormats = new HashMap<>() {{
        put(1, "fixed stations");
        put(2, "Regular Grid");
        put(3, "Ungeorectified Grid");
        put(4, "Moving Platform");
        put(5, "Irregular Grid");
        put(6, "Variable cell size");
        put(7, "TIN");
        put(8, "fixed stations (Stationwise)");
    }};

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Override
    public String getSupportedFileType() {
        return "hdf5";  // 이 파서는 hdf5
    }

    @Override
    public CapabilityDto getcapability(String filePath, String fileType) {
        String fileName = Paths.get(filePath).getFileName().toString();

        List<String> objectList = new ArrayList<>();
        List<List<Double>> fileBBOX = new ArrayList<>();
        String westbbox = "0.0";
        String southbbox = "0.0";
        String eastbbox = "0.0";
        String northbbox = "0.0";
        String epsg = "";

        // --- 파일 파싱 되었을 때 여기로 변경 ---
        try (HdfFile hdfFile = new HdfFile(Paths.get(filePath))) {
            Map<String, Object> fileAttr = printAttributes(hdfFile);

            Object objectCRSRef = getIgnoreCase(fileAttr,"horizontalDatumReference");
            Object objectCRS = getIgnoreCase(fileAttr, "horizontalDatumValue");
            Object objectwest = getIgnoreCase(fileAttr, "westBoundLongitude");
            Object objectsouth = getIgnoreCase(fileAttr, "southBoundLatitude");
            Object objecteast = getIgnoreCase(fileAttr, "eastBoundLongitude");
            Object objectnorth = getIgnoreCase(fileAttr, "northBoundLatitude");
            if(objectCRS != null) epsg = objectCRSRef.toString() + ":" + objectCRS.toString();
            if(objectwest != null) westbbox = objectwest.toString();
            if(objectsouth != null) southbbox = objectsouth.toString();
            if(objecteast != null) eastbbox = objecteast.toString();
            if(objectnorth != null) northbbox = objectnorth.toString();
            List<Double> lowerCorner = List.of(Double.parseDouble(westbbox),Double.parseDouble(southbbox));
            List<Double> upperCorner = List.of(Double.parseDouble(eastbbox),Double.parseDouble(northbbox));
            fileBBOX.add(lowerCorner);
            fileBBOX.add(upperCorner);

            for(Node node: hdfFile) {
                if(node instanceof Group) {
                    Group tmpGroup = (Group) node;
                    if(!tmpGroup.getName().equalsIgnoreCase("Group_F")) objectList.add(tmpGroup.getName());
                }
            }
            Set<String> tmpSet = new HashSet<String>(objectList);
            List<String> resultList = new ArrayList<>(tmpSet);

            String normalizedPath = filePath.replace("\\", "/");
            return new CapabilityDto(fileType, resultList, fileBBOX, normalizedPath, epsg);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public List<SearchObject> parse(GetObjectRequestDto request, Polygon polygon) throws Exception {
        List<SearchObject> hdf5features = new ArrayList<>();
        String hdf5FilePath = request.getFilePath().replace("\\", "/");
        Map<String, HDFObj> hdfobjList = new HashMap<>();

        try (HdfFile hdfFile = new HdfFile(Paths.get(hdf5FilePath))) {
            for (Node node : hdfFile) {
                if (node instanceof Group && node.getName().equalsIgnoreCase("Group_F")) {
                    //System.out.println("group f find");

                    Group objectGroup = (Group) node;
                    for (Node subNode : objectGroup) {
                        if(subNode instanceof Dataset && !node.getName().equalsIgnoreCase("featureCode")) {
                            Dataset groupInfo = (Dataset) subNode;
                            DataType fieldNames = groupInfo.getDataType();
                            //System.out.println(groupInfo.getName());
                            HDFObj tmphdfObj = new HDFObj();

                            if (fieldNames instanceof CompoundDataType) {
                                CompoundDataType compoundDataType = (CompoundDataType) fieldNames;
                                Object rawData = groupInfo.getData();
                                Map<String,Object> records = (Map<String,Object>) rawData;

                                List<String> tmpcodeList = new ArrayList<>();
                                List<String> tmpuomList = new ArrayList<>();
                                List<CompoundDataType.CompoundDataMember> members = compoundDataType.getMembers();
                                for (CompoundDataType.CompoundDataMember member : members) {
                                    String rawmemName = member.getName();
                                    String memberName = rawmemName.toLowerCase();
                                    //System.out.println("Member: " + memberName);

                                    Object tmpvalue = records.get(rawmemName);
                                    if (tmpvalue == null) {
                                        continue;
                                    }
                                    switch(memberName) {
                                        case "code":
                                            if (tmpvalue.getClass().isArray()) {
                                                int len = java.lang.reflect.Array.getLength(tmpvalue);
                                                for (int i = 0; i < len; i++) {
                                                    Object elem = java.lang.reflect.Array.get(tmpvalue, i);
                                                    if(elem.getClass().isArray()) {
                                                        int sublen = java.lang.reflect.Array.getLength(elem);
                                                        for(int j=0;j<sublen;j++) {
                                                            Object subelem = java.lang.reflect.Array.get(elem, j);
                                                            //System.out.println(subelem.toString());
                                                            tmpcodeList.add(subelem.toString());
                                                        }
                                                    }
                                                    else tmpcodeList.add(elem.toString());
                                                }
                                            } else {
                                                // 단일 값인 경우
                                                tmpcodeList.add(tmpvalue.toString());
                                            }
                                            break;
                                        case "uom.name":
                                            if (tmpvalue.getClass().isArray()) {
                                                int len = java.lang.reflect.Array.getLength(tmpvalue);
                                                for (int i = 0; i < len; i++) {
                                                    Object elem = java.lang.reflect.Array.get(tmpvalue, i);
                                                    //System.out.println("  " + elem);
                                                    if(elem.getClass().isArray()) {
                                                        int sublen = java.lang.reflect.Array.getLength(elem);
                                                        for(int j=0;j<sublen;j++) {
                                                            Object subelem = java.lang.reflect.Array.get(elem, j);
                                                            //System.out.println(subelem.toString());
                                                            tmpuomList.add(subelem.toString());
                                                        }
                                                    }
                                                    else tmpuomList.add(elem.toString());
                                                }
                                            } else {
                                                // 단일 값인 경우
                                                //System.out.println("  " + tmpvalue.toString());
                                                tmpuomList.add(tmpvalue.toString());
                                            }
                                            break;
                                        default:
                                            //System.out.println(memberName);
                                            break;
                                    }
                                    if(memberName.equals("code") || memberName.equals("uom.name")) {
                                        tmphdfObj.setObjType(groupInfo.getName());
                                        tmphdfObj.setCodeList(tmpcodeList);
                                        tmphdfObj.setUomNameList(tmpuomList);
                                    }
                                }
                                //groupInfo
                                hdfobjList.put(groupInfo.getName(),tmphdfObj);
                            }
                        }
                    }
                }
            }

            for (Node node : hdfFile) {
                if (node instanceof Group && !node.getName().equalsIgnoreCase("Group_F")) {
                    //System.out.println("group f find");
                    Group objectGroup = (Group) node;
                    String objectType = objectGroup.getName();
                    Map<String, Object> objGroupAttr = printAttributes(objectGroup);

                    int codingFormat = 0;
                    Object codingFormatObj = getIgnoreCase(objGroupAttr, "dataCodingFormat");
                    if (codingFormatObj instanceof Number) {
                        codingFormat = ((Number) codingFormatObj).intValue();
                    }

                    int dimension = 0;
                    Object dimensionObj = getIgnoreCase(objGroupAttr, "dimension");
                    if (dimensionObj instanceof Number) {
                        dimension = ((Number) dimensionObj).intValue();
                    }
                    System.out.println(node.getName() + " data coding format: " + codingFormat + ", dimension: " + dimension);

                    if(codingFormat != 0 && dimension != 0) {
                        for (Node subNode : objectGroup) {
                            if (subNode instanceof Group) {
                                Group objIdGroup = (Group) subNode;
                                Map<String, Object> objIdAttr = printAttributes(objIdGroup);
                                List<HDFValues> hdfResults = new ArrayList<>();
                                List<GeometryInfo> responseGeo = new ArrayList<>();
                                GeometryInfo tmpGeo = new GeometryInfo();

                                int timeInterval = 0;
                                Object timeIntervalObj = getIgnoreCase(objIdAttr,"timeRecordInterval");
                                if(timeIntervalObj instanceof Number) {
                                    timeInterval = ((Number) timeIntervalObj).intValue();
                                }

                                switch(codingFormat) {
                                    case 1:
                                        Object numStationObj = getIgnoreCase(objIdAttr,"numberOfStations");
                                        int numofStations = 0;
                                        if(numStationObj != null) {
                                            if(numStationObj instanceof Number) {
                                                numofStations = ((Number) numStationObj).intValue();
                                            }
                                        }
                                        if(numofStations > 0) {
                                            List<List<Double>> stationCoordinates = new ArrayList<>();
                                            for(Node valuesNode: objIdGroup) {
                                                if(valuesNode instanceof Group) {
                                                    Group valuesGroup = (Group) valuesNode;
                                                    for(Node datasetNode: valuesGroup) {
                                                        if(datasetNode instanceof Dataset) {
                                                            Dataset coordinatesDataset = (Dataset) datasetNode;
                                                            DataType fieldNames = coordinatesDataset.getDataType();

                                                            boolean hasLon = false;
                                                            boolean hasLat = false;
                                                            String lonKey = null;
                                                            String latKey = null;

                                                            if(fieldNames instanceof CompoundDataType) {
                                                                String tmplon = null;
                                                                String tmplat = null;

                                                                CompoundDataType compoundDataType = (CompoundDataType) fieldNames;
                                                                List<CompoundDataType.CompoundDataMember> members = compoundDataType.getMembers();
                                                                for(CompoundDataType.CompoundDataMember member:members) {
                                                                    String memberName = member.getName();
                                                                    if(memberName.equalsIgnoreCase("longitude")) {
                                                                        hasLon=true;
                                                                        tmplon = memberName;
                                                                    }
                                                                    if(memberName.equalsIgnoreCase("latitude")) {
                                                                        hasLat=true;
                                                                        tmplat = memberName;
                                                                    }
                                                                }
                                                                if(tmplon != null) lonKey = tmplon;
                                                                if(tmplat != null) latKey = tmplat;
                                                            }
                                                            if(coordinatesDataset.getDimensions()[0] == numofStations && hasLat && hasLon) {
                                                                try {
                                                                    Map<String, Object> rawData = (Map<String, Object>) coordinatesDataset.getData();
                                                                    Object lonArr = rawData.get(lonKey);
                                                                    Object latArr = rawData.get(latKey);
                                                                    float[] longitudes = (float[]) lonArr;
                                                                    float[] latitudes = (float[]) latArr;
                                                                    for (int i = 0; i < numofStations; i++) {
                                                                        stationCoordinates.add(List.of((double) longitudes[i], (double) latitudes[i]));
                                                                    }
                                                                    break;
                                                                } catch (Exception e) {
                                                                    log.error("Failed to parse coordinates dataset: " + e.getMessage());
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            tmpGeo.setType(dataCodingFormats.get(codingFormat));
                                            tmpGeo.setCoordinates(stationCoordinates);
                                            responseGeo.add(tmpGeo);
                                        }
                                        break;
                                    case 2:
                                        Object gridOriginLatObj = getIgnoreCase(objIdAttr, "gridOriginLatitude");
                                        Object gridOriginLonObj = getIgnoreCase(objIdAttr, "gridOriginLongitude");
                                        Object gridSpacingLatObj = getIgnoreCase(objIdAttr, "gridSpacingLatitudinal");
                                        Object gridSpacingLonObj = getIgnoreCase(objIdAttr, "gridSpacingLongitudinal");
                                        if(gridOriginLatObj!=null && gridOriginLonObj!=null && gridSpacingLatObj!=null && gridSpacingLonObj!=null) {
                                            String gridOriginLatStr = gridOriginLatObj.toString();
                                            String gridOriginLonStr = gridOriginLonObj.toString();
                                            String gridSpacingLatStr = gridSpacingLatObj.toString();
                                            String gridSpacingLonStr = gridSpacingLonObj.toString();

                                            Double gridOriginLat = Double.parseDouble(gridOriginLatStr);
                                            Double gridOriginLon = Double.parseDouble(gridOriginLonStr);
                                            Double gridSpacingLat = Double.parseDouble(gridSpacingLatStr);
                                            Double gridSpacingLon = Double.parseDouble(gridSpacingLonStr);

                                            List<Double> OriginLatLon = List.of(gridOriginLat,gridOriginLon);
                                            List<Double> SpacingLatLon = List.of(gridSpacingLat,gridSpacingLon);
                                            List<List<Double>> tmpGridGeo = List.of(OriginLatLon,SpacingLatLon);

                                            tmpGeo.setType(dataCodingFormats.get(codingFormat));
                                            tmpGeo.setCoordinates(tmpGridGeo);
                                            responseGeo.add(tmpGeo);
                                        }
                                        break;
                                    default:
                                        log.error("지원하지 않는 인코딩 형식");
                                        break;
                                }

                                for(Node valuesNode: objIdGroup) {
                                    if(valuesNode instanceof Group) {
                                        Group valueGroup = (Group) valuesNode;
                                        Map<String, Object> valueGroupAttr = printAttributes(valuesNode);
                                        LocalDateTime timePoint = null;
                                        if(!valueGroupAttr.isEmpty()) {
                                            String timePointStr = (String) getIgnoreCase(valueGroupAttr, "timePoint");
                                            if (timePointStr != null && timePointStr.length() > 15) {
                                                timePointStr = timePointStr.substring(0, 15);
                                                log.info(timePointStr);
                                                if(timePointStr != null) {
                                                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
                                                    timePoint = LocalDateTime.parse(timePointStr,formatter);
                                                }
                                            }
                                        }

                                        for(Node valueset: valueGroup) {
                                            if(valueset instanceof Dataset && valueset.getName().equalsIgnoreCase("values")) {
                                                Dataset valueDataset = (Dataset) valueset;
                                                HDFObj tmpobjType = hdfobjList.get(node.getName());
                                                List<Object> parsedValues = new ArrayList<>();

                                                Object valuesRaw = valueDataset.getData();
                                                List<String> tmpcodeList = tmpobjType.getCodeList();
                                                List<String> tmpuomList = tmpobjType.getUomNameList();

                                                //컬럼이 한개인 상태랑 여러개인 상태 나눠서?
                                                //한개면 그냥 단순 배열일거고 여러개면 codeList에서 하나씩 꺼내서 컬럼명으로 꺼내야 하나?
                                                //꺼낼때 tmpobjType에서 dataTypeList에서 확인하면서 꺼내야 함

                                                // 단일 컬럼
                                                // 단일 컬럼
                                                if (tmpcodeList.size() == 1) {
                                                    int latsize = 1;
                                                    // valuesRaw가 배열일 때만 길이를 가져옴
                                                    if (valuesRaw != null && valuesRaw.getClass().isArray()) {
                                                        latsize = java.lang.reflect.Array.getLength(valuesRaw);
                                                    }

                                                    for (int i = 0; i < latsize; i++) {
                                                        List<Object> rowValues = new ArrayList<>();

                                                        if (valuesRaw.getClass().isArray()) {
                                                            // valuesRaw가 배열인 경우, i번째 행을 가져옴
                                                            Object rowData = java.lang.reflect.Array.get(valuesRaw, i);

                                                            if (rowData != null && rowData.getClass().isArray()) {
                                                                // rowData가 2차원 배열의 '행'인 경우
                                                                for (int j = 0; j < java.lang.reflect.Array.getLength(rowData); j++) {
                                                                    Object val = java.lang.reflect.Array.get(rowData, j);
                                                                    rowValues.add(castPrimitive(val));
                                                                }
                                                            } else {
                                                                // rowData가 단일 값인 경우 (1차원 배열의 요소)
                                                                rowValues.add(castPrimitive(rowData));
                                                            }
                                                        } else {
                                                            // valuesRaw 자체가 단일 값인 경우
                                                            rowValues.add(castPrimitive(valuesRaw));
                                                        }
                                                        parsedValues.add(rowValues);
                                                    }

                                                    if (!parsedValues.isEmpty()) {
                                                        List<Object> tmpSizeCheck = (List<Object>) parsedValues.get(0);
                                                        System.out.println(valueGroup.getName() + ": (" + parsedValues.size() + ", " + tmpSizeCheck.size() + ")");
                                                    }
                                                }
                                                // 다중 컬럼
                                                else {
                                                    Map<String, Object> valuesMap = (Map<String, Object>) valuesRaw;
                                                    List<String> tmpcodeList_t = new ArrayList<>(valuesMap.keySet());
                                                    String firstCode = tmpcodeList_t.get(0);
                                                    Object firstColData = valuesMap.get(firstCode);

                                                    // latsize 동적 계산
                                                    int latsize = 1;
                                                    if (firstColData.getClass().isArray()) {
                                                        latsize = java.lang.reflect.Array.getLength(firstColData);
                                                    }

                                                    for (int latIdx = 0; latIdx < latsize; latIdx++) {
                                                        List<Object> latArray = new ArrayList<>();
                                                        for (String tmpcode : tmpcodeList_t) {
                                                            Object colData = valuesMap.get(tmpcode);
                                                            if (colData != null && colData.getClass().isArray()) {
                                                                latArray.add(java.lang.reflect.Array.get(colData, latIdx));
                                                            } else {
                                                                latArray.add(colData);
                                                            }
                                                        }

                                                        int lonsize = 1;
                                                        if (!latArray.isEmpty() && latArray.get(0) != null && latArray.get(0).getClass().isArray()) {
                                                            lonsize = java.lang.reflect.Array.getLength(latArray.get(0));
                                                        }

                                                        // 각 행의 데이터를 파싱하여 HDFValues에 추가
                                                        List<List<Object>> rowRecords = new ArrayList<>();
                                                        for (int lonIdx = 0; lonIdx < lonsize; lonIdx++) {
                                                            List<Object> pointRecord = new ArrayList<>();
                                                            for (Object rowData : latArray) {
                                                                if (rowData != null && rowData.getClass().isArray()) {
                                                                    if (java.lang.reflect.Array.getLength(rowData) > lonIdx) {
                                                                        Object val = java.lang.reflect.Array.get(rowData, lonIdx);
                                                                        pointRecord.add(castPrimitive(val));
                                                                    }
                                                                } else {
                                                                    pointRecord.add(castPrimitive(rowData));
                                                                }
                                                            }
                                                            rowRecords.add(pointRecord);
                                                        }
                                                        parsedValues.add(rowRecords);
                                                    }
                                                }
                                                HDFValues tmpHDFValues = new HDFValues(valueGroup.getName(),timePoint,parsedValues,timeInterval,tmpcodeList,tmpuomList);
                                                hdfResults.add(tmpHDFValues);
                                            }
                                        }
                                    }
                                }
                                SearchObject tmpSearchObj = new SearchObject(objectType,objIdGroup.getName(),responseGeo,hdfResults);
                                hdf5features.add(tmpSearchObj);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        return hdf5features;
    }
    private Object castPrimitive(Object val){
        //System.out.println(val.getClass());
        if(val == null) return null;
        if (val instanceof Float) {
            double d = ((Float) val).doubleValue();
            return Math.round(d * 1e3) / 1e3;
        } else if (val instanceof Double) {
            return Math.round((Double) val * 1e3) / 1e3;
        } else if (val instanceof Short) {
            return ((Short) val).longValue();
        } else if (val instanceof Integer) {
            return ((Integer) val).longValue();
        } else if (val instanceof Long) {
            return val;
        } else if (val instanceof String) {
            return val;
        } else {
            System.out.println("Unknown data type: " + val.getClass().getName());
            return val;
        }
    }
    public static Object getIgnoreCase(Map<String, Object> map, String key) {
        for (String k : map.keySet()) {
            if (k.equalsIgnoreCase(key)) {
                return map.get(k);
            }
        }
        return null;
    }
    private static Map<String, Object> printAttributes(Node node) {
        Map<String, Object> resultAttr = new HashMap<>();
        Map<String, Attribute> attrs = node.getAttributes();
        for (Map.Entry<String, Attribute> entry : attrs.entrySet()) {
            String attrName = entry.getKey();
            Attribute attr = entry.getValue();
            Object value = attr.getData();  // getValue()가 아니라 getData()임
            resultAttr.put(attrName,value);
//
            //System.out.println("    [Attribute] " + attrName + " = " + value);
        }
        return resultAttr;
    }
}
