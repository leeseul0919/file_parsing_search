package com.example.file_parsing_search.parser;

import com.example.file_parsing_search.dto.*;
import com.example.file_parsing_search.exception.CustomException;
import com.example.file_parsing_search.exception.ErrorCode;
import com.fasterxml.jackson.core.util.Separators;
import io.jhdf.HdfFile;
import io.jhdf.api.Attribute;
import io.jhdf.api.Dataset;
import io.jhdf.api.Group;
import io.jhdf.api.Node;
import io.jhdf.object.datatype.CompoundDataType;
import io.jhdf.object.datatype.DataType;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.lang.model.util.Elements;
import java.awt.*;
import java.io.IOException;
import java.lang.reflect.Array;
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
        if (filePath == null || filePath.isEmpty()) {
            log.error("Null File : " + filePath);
        }

        // --- 파일 파싱 되었을 때 여기로 변경 ---
        try (HdfFile hdfFile = new HdfFile(Paths.get(filePath))) {
            Map<String, Object> fileAttr = printAttributes(hdfFile);
            List<String> objectList = new ArrayList<>();
            List<List<Double>> fileBBOX = new ArrayList<>();

            String westbbox = "0.0";
            String southbbox = "0.0";
            String eastbbox = "0.0";
            String northbbox = "0.0";
            String epsg = "";

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
        if (request == null || request.getFilePath() == null || request.getFilePath().isEmpty() || polygon == null) {
            throw new CustomException(ErrorCode.NULL_FILE_PATH);
        }

        GeometryFactory gf = new GeometryFactory();
        List<SearchObject> hdf5features = new ArrayList<>();
        String hdf5FilePath = request.getFilePath().replace("\\", "/");
        Map<String, HDFObj> hdfobjList = new HashMap<>();

        try (HdfFile hdfFile = new HdfFile(Paths.get(hdf5FilePath))) {
            for (Node node : hdfFile) {
                if (node instanceof Group && node.getName().equalsIgnoreCase("Group_F")) {
                    Group objectGroup = (Group) node;
                    for (Node subNode : objectGroup) {
                        if(subNode instanceof Dataset && !node.getName().equalsIgnoreCase("featureCode")) {
                            Dataset groupInfo = (Dataset) subNode;
                            DataType fieldNames = groupInfo.getDataType();
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
                                                            tmpcodeList.add(subelem.toString());
                                                        }
                                                    }
                                                    else tmpcodeList.add(elem.toString());
                                                }
                                            } else {
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
                                                tmpuomList.add(tmpvalue.toString());
                                            }
                                            break;
                                        default:
                                            break;
                                    }
                                    if(memberName.equals("code") || memberName.equals("uom.name")) {
                                        tmphdfObj.setObjType(groupInfo.getName());
                                        tmphdfObj.setCodeList(tmpcodeList);
                                        tmphdfObj.setUomNameList(tmpuomList);
                                    }
                                }
                                hdfobjList.put(groupInfo.getName(),tmphdfObj);
                            }
                        }
                    }
                }
            }

            if(hdfobjList.isEmpty()) {
                throw new CustomException(ErrorCode.FAIL_READ_GROUP_F);
            }

            for (Node node : hdfFile) {
                if (node instanceof Group && !node.getName().equalsIgnoreCase("Group_F")) {
                    Group objectGroup = (Group) node;
                    String objectType = objectGroup.getName();
                    Map<String, Object> objGroupAttr = printAttributes(objectGroup);

                    int codingFormat = 0;
                    Object codingFormatObj = getIgnoreCase(objGroupAttr, "dataCodingFormat");
                    if (codingFormatObj instanceof Number) {
                        codingFormat = ((Number) codingFormatObj).intValue();
                    }
                    if (codingFormat == 0) {
                        throw new CustomException(ErrorCode.UNKNOWN_CODING_FORMAT);
                    }

                    int dimension = 0;
                    Object dimensionObj = getIgnoreCase(objGroupAttr, "dimension");
                    if (dimensionObj instanceof Number) {
                        dimension = ((Number) dimensionObj).intValue();
                    }
                    //System.out.println(node.getName() + " data coding format: " + codingFormat + ", dimension: " + dimension);

                    HDFObj tmpobjType = hdfobjList.get(node.getName());
                    List<String> tmpcodeList = tmpobjType.getCodeList();
                    List<String> tmpuomList = tmpobjType.getUomNameList();

                    if(codingFormat != 0 && dimension != 0) {
                        for (Node subNode : objectGroup) {
                            if (subNode instanceof Group) {
                                Group objIdGroup = (Group) subNode;
                                Map<String, Object> objIdAttr = printAttributes(objIdGroup);
                                List<ObjGroupInfo> hdfResults = new ArrayList<>();

                                int timeInterval = 0;
                                Object timeIntervalObj = getIgnoreCase(objIdAttr,"timeRecordInterval");
                                if(timeIntervalObj instanceof Number) {
                                    timeInterval = ((Number) timeIntervalObj).intValue();
                                }

                                Double gridOriginLat = 0.0;
                                Double gridOriginLon = 0.0;
                                Double gridSpacingLat = 0.0;
                                Double gridSpacingLon = 0.0;

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

                                            gridOriginLat = Double.parseDouble(gridOriginLatStr);
                                            gridOriginLon = Double.parseDouble(gridOriginLonStr);
                                            gridSpacingLat = Double.parseDouble(gridSpacingLatStr);
                                            gridSpacingLon = Double.parseDouble(gridSpacingLonStr);
                                        }
                                        break;
                                    default:
                                        throw new CustomException(ErrorCode.NOT_SUPPORTED_CODING_FORMAT);
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
                                                Object valuesRaw = valueDataset.getData();
                                                List<ObjInfo> objInfoList = new ArrayList<>();

                                                if(codingFormat == 2) {
                                                    if (tmpcodeList.size() == 1) {
                                                        int latSize = 1;
                                                        if (valuesRaw != null && valuesRaw.getClass().isArray()) {
                                                            latSize = Array.getLength(valuesRaw);
                                                        }

                                                        for (int i = 0; i < latSize; i++) {
                                                            Object row = Array.get(valuesRaw, i);

                                                            if (row != null && row.getClass().isArray()) {
                                                                for (int j = 0; j < Array.getLength(row); j++) {
                                                                    Object val = castPrimitive(Array.get(row, j));

                                                                    double lat = gridOriginLat + i * gridSpacingLat;
                                                                    double lon = gridOriginLon + j * gridSpacingLon;
                                                                    org.locationtech.jts.geom.Point p = gf.createPoint(new Coordinate(lon, lat));

                                                                    if(polygon.contains(p)) {
                                                                        ObjInfo point = new ObjInfo();
                                                                        point.setGeoType("Point");
                                                                        point.setCoordinates(List.of(lat, lon));
                                                                        point.setValues(List.of(val));

                                                                        objInfoList.add(point);
                                                                    }
                                                                }
                                                            } else {
                                                                Object val = castPrimitive(row);

                                                                double lat = gridOriginLat + i * gridSpacingLat;
                                                                double lon = gridOriginLon;  // j가 없으니까 시작 지점만
                                                                org.locationtech.jts.geom.Point p = gf.createPoint(new Coordinate(lon, lat));

                                                                if(polygon.contains(p)) {
                                                                    ObjInfo point = new ObjInfo();
                                                                    point.setGeoType("Point");
                                                                    point.setCoordinates(List.of(lat, lon));
                                                                    point.setValues(List.of(val));

                                                                    objInfoList.add(point);
                                                                }
                                                            }
                                                        }
                                                    } else {
                                                        Map<String, Object> valuesMap = (Map<String, Object>) valuesRaw;
                                                        List<String> fieldNames = new ArrayList<>(valuesMap.keySet());

                                                        Object firstCol = valuesMap.get(fieldNames.get(0));
                                                        int latSize = firstCol.getClass().isArray() ? Array.getLength(firstCol) : 1;

                                                        for (int i = 0; i < latSize; i++) {
                                                            List<Object> colSlices = new ArrayList<>();
                                                            for (String code : fieldNames) {
                                                                Object colData = valuesMap.get(code);

                                                                if (colData != null && colData.getClass().isArray()) {
                                                                    Object latRow = Array.getLength(colData) > i ? Array.get(colData, i) : null;
                                                                    colSlices.add(latRow);
                                                                } else {
                                                                    colSlices.add(colData);
                                                                }
                                                            }

                                                            boolean is2D = colSlices.get(0) != null && colSlices.get(0).getClass().isArray();

                                                            if (is2D) {
                                                                int lonSize = Array.getLength(colSlices.get(0));

                                                                for (int j = 0; j < lonSize; j++) {
                                                                    List<Object> values = new ArrayList<>();
                                                                    for (Object col : colSlices) {
                                                                        if (col != null && col.getClass().isArray()) {
                                                                            Object val = Array.getLength(col) > j ? Array.get(col, j) : null;
                                                                            values.add(castPrimitive(val));
                                                                        } else {
                                                                            values.add(castPrimitive(col));  // 그냥 단일 값이면 그대로 넣음
                                                                        }
                                                                    }

                                                                    double lat = gridOriginLat + i * gridSpacingLat;
                                                                    double lon = gridOriginLon + j * gridSpacingLon;
                                                                    org.locationtech.jts.geom.Point p = gf.createPoint(new Coordinate(lon, lat));
                                                                    if(polygon.contains(p)) {
                                                                        ObjInfo point = new ObjInfo();
                                                                        point.setGeoType("Point");
                                                                        point.setCoordinates(List.of(lat, lon));
                                                                        point.setValues(values);

                                                                        objInfoList.add(point);
                                                                    }
                                                                }
                                                            } else {
                                                                List<Object> values = new ArrayList<>();
                                                                for (Object col : colSlices) {
                                                                    values.add(castPrimitive(col));
                                                                }

                                                                double lat = gridOriginLat + i * gridSpacingLat;
                                                                double lon = gridOriginLon;
                                                                Point p = gf.createPoint(new Coordinate(lon, lat));
                                                                if(polygon.contains(p)) {
                                                                    ObjInfo point = new ObjInfo();
                                                                    point.setGeoType("Point");
                                                                    point.setCoordinates(List.of(lat, lon));
                                                                    point.setValues(values);

                                                                    objInfoList.add(point);
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                if(!objInfoList.isEmpty()) {
                                                    ObjGroupInfo objGroup = new ObjGroupInfo();
                                                    objGroup.setTimePoint(timePoint);
                                                    objGroup.setObjInfo(objInfoList);
                                                    hdfResults.add(objGroup);
                                                }
                                            }
                                        }
                                    }
                                }
                                if(!hdfResults.isEmpty()) {
                                    SearchObject tmpSearchObj = new SearchObject(objectType,objIdGroup.getName(),tmpcodeList,tmpuomList,hdfResults,null);
                                    hdf5features.add(tmpSearchObj);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        if(hdf5features.isEmpty()) {
            throw new CustomException(ErrorCode.NO_RESULTS_FOUND);
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
