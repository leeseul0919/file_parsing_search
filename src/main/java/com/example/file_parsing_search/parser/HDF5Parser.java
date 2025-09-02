package com.example.file_parsing_search.parser;

import com.example.file_parsing_search.dto.*;
import com.example.file_parsing_search.exception.CustomException;
import com.example.file_parsing_search.exception.ErrorCode;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.reflect.Array;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@Slf4j
public class HDF5Parser implements ObjectParser {
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
        return "hdf5";
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
    public List<SearchObject> parse(GetObjectRequestDto request, org.locationtech.jts.geom.Polygon polygon) throws Exception {
        if (request == null || request.getFilePath() == null || request.getFilePath().isEmpty() || polygon == null) {
            throw new CustomException(ErrorCode.NULL_FILE_PATH);
        }

        GeometryFactory gf = new GeometryFactory();
        List<SearchObject> hdf5features = new ArrayList<>();
        String hdf5FilePath = request.getFilePath().replace("\\", "/");

        try (HdfFile hdfFile = new HdfFile(Paths.get(hdf5FilePath))) {
            if (hdfFile == null) throw new CustomException(ErrorCode.FAIL_OPEN_FILE);

            // 1) Build code/uom lookup from Group_F
            Map<String, HDFObj> typeToHdfObj = buildHdfObjList(hdfFile);

            // 2) Iterate object groups (skip Group_F)
            for (Node node : hdfFile) {
                if (!(node instanceof Group)) continue;
                Group objectGroup = (Group) node;
                if (objectGroup.getName().equalsIgnoreCase("Group_F")) continue;

                String objectType = objectGroup.getName();
                Map<String, Object> objGroupAttr = printAttributes(objectGroup);

                int codingFormat = getInt(objGroupAttr, "dataCodingFormat", 1);
                int dimension = getInt(objGroupAttr, "dimension", 0);

                if (codingFormat == 0) throw new CustomException(ErrorCode.UNKNOWN_CODING_FORMAT);
                log.info("ObjectType={} codingFormat={} dimension={}", objectType, codingFormat, dimension);

                HDFObj template = typeToHdfObj.get(objectType.toLowerCase());
                if (template == null) {
                    log.warn("No code/uom template found for {} - skipping", objectType);
                    continue;
                }

                // Make mutable copies for this objectType
                List<String> tmpcodeListBase = new ArrayList<>(template.getCodeList());
                List<String> tmpuomListBase = new ArrayList<>(template.getUomNameList());

                // Process each object identifier group under this objectGroup
                processObjectGroup(objectGroup, objectType, codingFormat, dimension, tmpcodeListBase, tmpuomListBase, polygon, gf, hdf5features);
            }

            log.info("Parsed features: {}", hdf5features.size());
        } catch (CustomException ce) {
            throw ce;
        } catch (Exception e) {
            log.error("parse error: {}", e.getMessage(), e);
        }

        if (hdf5features.isEmpty()) {
            throw new CustomException(ErrorCode.NO_RESULTS_FOUND);
        }
        return hdf5features;
    }

    private Map<String, HDFObj> buildHdfObjList(HdfFile hdfFile) {
        Map<String, HDFObj> result = new LinkedHashMap<>();
        for (Node node : hdfFile) {
            if (!(node instanceof Group)) continue;
            Group tmpGroup = (Group) node;
            if (!tmpGroup.getName().equalsIgnoreCase("Group_F")) continue;

            for (Node subNode : tmpGroup) {
                if (!(subNode instanceof Dataset)) continue;
                Dataset groupInfo = (Dataset) subNode;
                DataType fieldNames = groupInfo.getDataType();

                if (!(fieldNames instanceof CompoundDataType)) continue;
                CompoundDataType compound = (CompoundDataType) fieldNames;
                Object rawData = groupInfo.getData();
                if (!(rawData instanceof Map)) continue;
                Map<String, Object> records = (Map<String, Object>) rawData;

                List<String> codes = new ArrayList<>();
                List<String> uoms = new ArrayList<>();
                for (CompoundDataType.CompoundDataMember member : compound.getMembers()) {
                    String memberName = member.getName();
                    Object tmpvalue = records.get(memberName);
                    if (tmpvalue == null) continue;
                    switch (memberName.toLowerCase()) {
                        case "code":
                            flattenIntoStringList(tmpvalue, codes);
                            break;
                        case "uom.name":
                            flattenIntoStringList(tmpvalue, uoms);
                            break;
                        default:
                            break;
                    }
                }
                if (!codes.isEmpty() || !uoms.isEmpty()) {
                    HDFObj obj = new HDFObj();
                    obj.setObjType(groupInfo.getName());
                    obj.setCodeList(codes);
                    obj.setUomNameList(uoms);
                    result.put(groupInfo.getName().toLowerCase(), obj);
                }
            }
        }
        if (result.isEmpty()) throw new CustomException(ErrorCode.FAIL_READ_GROUP_F);
        return result;
    }

    private void processObjectGroup(Group objectGroup,
                                    String objectType,
                                    int codingFormat,
                                    int dimension,
                                    List<String> tmpcodeListBase,
                                    List<String> tmpuomListBase,
                                    org.locationtech.jts.geom.Polygon polygon,
                                    GeometryFactory gf,
                                    List<SearchObject> outFeatures) {

        for (Node subNode : objectGroup) {
            if (!(subNode instanceof Group)) continue;
            Group objIdGroup = (Group) subNode;
            Map<String, Object> objIdAttr = printAttributes(objIdGroup);

            int timeInterval = getInt(objIdAttr, "timeRecordInterval", 0);

            // grid metadata for codingFormat 2
            double gridOriginLat = 0, gridOriginLon = 0, gridSpacingLat = 0, gridSpacingLon = 0;
            List<List<Double>> stationCoordinates = new ArrayList<>();
            String timePointStr = "";

            if (codingFormat == 1 || codingFormat == 8) {
                timePointStr = (String) getIgnoreCase(objIdAttr, "dateTimeOfFirstRecord");
                Object numStationObj = getIgnoreCase(objIdAttr, "numberOfStations");
                int numofStations = numStationObj instanceof Number ? ((Number) numStationObj).intValue() : 0;
                if (numofStations > 0) {
                    stationCoordinates = parseStationCoordinates(objIdGroup, numofStations);
                }
            } else if (codingFormat == 2) {
                Object gridOriginLatObj = getIgnoreCase(objIdAttr, "gridOriginLatitude");
                Object gridOriginLonObj = getIgnoreCase(objIdAttr, "gridOriginLongitude");
                Object gridSpacingLatObj = getIgnoreCase(objIdAttr, "gridSpacingLatitudinal");
                Object gridSpacingLonObj = getIgnoreCase(objIdAttr, "gridSpacingLongitudinal");
                if (gridOriginLatObj != null && gridOriginLonObj != null && gridSpacingLatObj != null && gridSpacingLonObj != null) {
                    gridOriginLat = round6(Double.parseDouble(gridOriginLatObj.toString()));
                    gridOriginLon = round6(Double.parseDouble(gridOriginLonObj.toString()));
                    gridSpacingLat = round6(Double.parseDouble(gridSpacingLatObj.toString()));
                    gridSpacingLon = round6(Double.parseDouble(gridSpacingLonObj.toString()));
                }
            }

            // iterate value groups
            List<ObjGroupInfo> hdfResults = new ArrayList<>();
            int orderNode = 0;
            for (Node valuesNode : objIdGroup) {
                if (!(valuesNode instanceof Group)) continue;
                Group valueGroup = (Group) valuesNode;
                Map<String, Object> valueGroupAttr = printAttributes(valueGroup);

                LocalDateTime timePoint = null;
                if (codingFormat == 1 && timePointStr != null) {
                    if (!timePointStr.isEmpty()) {
                        try {
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                            timePoint = LocalDateTime.parse(timePointStr, formatter);
                        } catch (Exception ex) {
                            log.debug("Failed to parse timePoint for codingFormat 1: {}", timePointStr);
                        }
                    }
                }
                if (codingFormat == 2 && !valueGroupAttr.isEmpty()) {
                    Object tp = getIgnoreCase(valueGroupAttr, "timePoint");
                    if (tp instanceof String) {
                        String tpStr = (String) tp;
                        if (tpStr.length() >= 15) {
                            tpStr = tpStr.substring(0, 15);
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
                            try {
                                timePoint = LocalDateTime.parse(tpStr, formatter);
                            } catch (Exception ex) {
                                log.debug("Failed to parse timePoint for codingFormat 2: {}", tpStr);
                            }
                        }
                    }
                }

                for (Node valueset : valueGroup) {
                    if (!(valueset instanceof Dataset) || !valueset.getName().equalsIgnoreCase("values")) continue;
                    Dataset valueDataset = (Dataset) valueset;
                    Object valuesRaw = valueDataset.getData();
                    List<ObjInfo> tmpvalues = new ArrayList<>();

                    List<String> tmpcodeList = new ArrayList<>(tmpcodeListBase);
                    List<String> tmpuomList = new ArrayList<>(tmpuomListBase);

                    if (codingFormat == 1) {
                        tmpvalues = parseValuesForCodingFormat1(valuesRaw, tmpcodeList, tmpuomList);

                        if (!tmpvalues.isEmpty() && !stationCoordinates.isEmpty()) {
                            List<Double> gridGeo = stationCoordinates.get(Math.min(orderNode, stationCoordinates.size()-1));
                            double lon = (double) ((Number) gridGeo.get(0)).doubleValue();
                            double lat = (double) ((Number) gridGeo.get(1)).doubleValue();
                            org.locationtech.jts.geom.Point p = gf.createPoint(new Coordinate(lon, lat));

                            if (polygon.contains(p)) {
                                ObjGroupInfo objGroup = new ObjGroupInfo();
                                objGroup.setTimePoint(timePoint);
                                objGroup.setValues(tmpvalues);

                                List<GeometryInfo> geo = new ArrayList<>();
                                geo.add(new GeometryInfo(dataCodingFormats.get(codingFormat), stationCoordinates.get(Math.min(orderNode, stationCoordinates.size()-1))));

                                List<ObjGroupInfo> singleObjList = new ArrayList<>();
                                singleObjList.add(objGroup);

                                SearchObject tmpSearchObj = new SearchObject(objectType, objIdGroup.getName(), tmpcodeList, tmpuomList, null, geo, singleObjList);
                                outFeatures.add(tmpSearchObj);
                            }
                        }
                    } else if (codingFormat == 2) {
                        tmpvalues = parseValuesForCodingFormat2(valuesRaw, tmpcodeList, tmpuomList, gridOriginLat, gridOriginLon, gridSpacingLat, gridSpacingLon, polygon, gf);
                        if (!tmpvalues.isEmpty()) {
                            ObjGroupInfo objGroup = new ObjGroupInfo();
                            objGroup.setTimePoint(timePoint);
                            objGroup.setValues(tmpvalues);
                            hdfResults.add(objGroup);
                        }
                    }
                }
                orderNode++;
            }

            if (codingFormat == 2 && !hdfResults.isEmpty()) {
                List<GeometryInfo> responseGeo = new ArrayList<>();
                List<Double> origin = List.of(gridOriginLon, gridOriginLat);
                List<Double> spacing = List.of(gridSpacingLon, gridSpacingLat);
                responseGeo.add(new GeometryInfo(dataCodingFormats.get(codingFormat), List.of(origin, spacing)));
                SearchObject tmpSearchObj = new SearchObject(objectType, objIdGroup.getName(), tmpcodeListBase, tmpuomListBase, null, responseGeo, hdfResults);
                outFeatures.add(tmpSearchObj);
            }
        }
    }

    private List<List<Double>> parseStationCoordinates(Group objIdGroup, int numofStations) {
        List<List<Double>> coords = new ArrayList<>();
        for (Node valuesNode : objIdGroup) {
            if (!(valuesNode instanceof Group)) continue;
            Group valuesGroup = (Group) valuesNode;
            for (Node datasetNode : valuesGroup) {
                if (!(datasetNode instanceof Dataset)) continue;
                Dataset coordinatesDataset = (Dataset) datasetNode;
                DataType fieldNames = coordinatesDataset.getDataType();

                if (!(fieldNames instanceof CompoundDataType)) continue;
                CompoundDataType compoundDataType = (CompoundDataType) fieldNames;
                String lonKey = null, latKey = null;
                for (CompoundDataType.CompoundDataMember member : compoundDataType.getMembers()) {
                    String memberName = member.getName();
                    if (memberName.equalsIgnoreCase("longitude") || memberName.equalsIgnoreCase("long")) lonKey = memberName;
                    if (memberName.equalsIgnoreCase("latitude") || memberName.equalsIgnoreCase("lat")) latKey = memberName;
                }

                if (lonKey != null && latKey != null) {
                    try {
                        Map<String, Object> rawData = (Map<String, Object>) coordinatesDataset.getData();
                        Object lonArr = rawData.get(lonKey);
                        Object latArr = rawData.get(latKey);
                        float[] longitudes = (float[]) lonArr;
                        float[] latitudes = (float[]) latArr;
                        for (int i = 0; i < numofStations; i++) {
                            coords.add(List.of(round6((double) longitudes[i]), round6((double) latitudes[i])));
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse coordinates dataset: {}", e.getMessage());
                    }
                }
            }
        }
        return coords;
    }

    private List<ObjInfo> parseValuesForCodingFormat1(Object valuesRaw, List<String> tmpcodeList, List<String> tmpuomList) {
        List<ObjInfo> tmpvalues = new ArrayList<>();
        if (!(valuesRaw instanceof Map)) return tmpvalues;
        Map<String, Object> valuesMap = (Map<String, Object>) valuesRaw;
        List<String> fieldNames = new ArrayList<>(valuesMap.keySet());

        // Align codes/uom with actual field order
        List<String> newCodeList = new ArrayList<>();
        List<String> newUomList = new ArrayList<>();
        for (String field : fieldNames) {
            String matchedCode = field;
            String matchedUom = null;
            for (int i = 0; i < tmpcodeList.size(); i++) {
                String code = tmpcodeList.get(i);
                String uom = tmpuomList.size() > i ? tmpuomList.get(i) : null;
                if (field.toLowerCase().contains(code.toLowerCase()) || code.toLowerCase().contains(field.toLowerCase())) {
                    matchedCode = field;
                    matchedUom = uom;
                    break;
                }
            }
            newCodeList.add(matchedCode);
            newUomList.add(matchedUom != null ? matchedUom : "");
        }
        tmpcodeList.clear(); tmpcodeList.addAll(newCodeList);
        tmpuomList.clear(); tmpuomList.addAll(newUomList);

        Object firstCol = valuesMap.get(fieldNames.get(0));
        int numValues = (firstCol != null && firstCol.getClass().isArray()) ? Array.getLength(firstCol) : 1;

        for (int i = 0; i < numValues; i++) {
            List<Object> values = new ArrayList<>();
            for (String code : fieldNames) {
                Object colData = valuesMap.get(code);
                if (colData != null && colData.getClass().isArray()) {
                    Object val = Array.getLength(colData) > i ? Array.get(colData, i) : null;
                    values.add(castPrimitive(val));
                } else {
                    values.add(castPrimitive(colData));
                }
            }
            ObjInfo point = new ObjInfo();
            point.setValues(values);
            tmpvalues.add(point);
        }
        return tmpvalues;
    }

    private List<ObjInfo> parseValuesForCodingFormat2(Object valuesRaw,
                                                      List<String> tmpcodeList,
                                                      List<String> tmpuomList,
                                                      double gridOriginLat,
                                                      double gridOriginLon,
                                                      double gridSpacingLat,
                                                      double gridSpacingLon,
                                                      org.locationtech.jts.geom.Polygon polygon,
                                                      GeometryFactory gf) {
        List<ObjInfo> tmpvalues = new ArrayList<>();
        if (tmpcodeList.size() == 1) {
            int latSize = 1;
            if (valuesRaw != null && valuesRaw.getClass().isArray()) latSize = Array.getLength(valuesRaw);

            for (int i = 0; i < latSize; i++) {
                Object row = Array.get(valuesRaw, i);
                if (row != null && row.getClass().isArray()) {
                    for (int j = 0; j < Array.getLength(row); j++) {
                        Object val = castPrimitive(Array.get(row, j));
                        double lat = gridOriginLat + i * gridSpacingLat;
                        double lon = gridOriginLon + j * gridSpacingLon;
                        org.locationtech.jts.geom.Point p = gf.createPoint(new Coordinate(lon, lat));
                        if (polygon.contains(p)) {
                            ObjInfo point = new ObjInfo();
                            point.setIndex(List.of(i, j));
                            point.setValues(List.of(val));
                            tmpvalues.add(point);
                        }
                    }
                } else {
                    Object val = castPrimitive(row);
                    double lat = gridOriginLat + i * gridSpacingLat;
                    double lon = gridOriginLon;
                    org.locationtech.jts.geom.Point p = gf.createPoint(new Coordinate(lon, lat));
                    if (polygon.contains(p)) {
                        ObjInfo point = new ObjInfo();
                        point.setIndex(List.of(i, 0));
                        point.setValues(List.of(val));
                        tmpvalues.add(point);
                    }
                }
            }
        } else {
            if (!(valuesRaw instanceof Map)) return tmpvalues;
            Map<String, Object> valuesMap = (Map<String, Object>) valuesRaw;
            List<String> fieldNames = new ArrayList<>(valuesMap.keySet());

            // Align codes/uom
            List<String> newCodeList = new ArrayList<>();
            List<String> newUomList = new ArrayList<>();
            for (String field : fieldNames) {
                String matchedCode = field;
                String matchedUom = null;
                for (int i = 0; i < tmpcodeList.size(); i++) {
                    String code = tmpcodeList.get(i);
                    String uom = tmpuomList.size() > i ? tmpuomList.get(i) : null;
                    if (field.toLowerCase().contains(code.toLowerCase()) || code.toLowerCase().contains(field.toLowerCase())) {
                        matchedCode = field;
                        matchedUom = uom;
                        break;
                    }
                }
                newCodeList.add(matchedCode);
                newUomList.add(matchedUom != null ? matchedUom : "");
            }
            tmpcodeList.clear(); tmpcodeList.addAll(newCodeList);
            tmpuomList.clear(); tmpuomList.addAll(newUomList);

            Object firstCol = valuesMap.get(fieldNames.get(0));
            int latSize = firstCol != null && firstCol.getClass().isArray() ? Array.getLength(firstCol) : 1;

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
                                values.add(castPrimitive(col));
                            }
                        }
                        double lat = gridOriginLat + i * gridSpacingLat;
                        double lon = gridOriginLon + j * gridSpacingLon;
                        org.locationtech.jts.geom.Point p = gf.createPoint(new Coordinate(lon, lat));
                        if (polygon.contains(p)) {
                            ObjInfo point = new ObjInfo();
                            point.setIndex(List.of(i, j));
                            point.setValues(values);
                            tmpvalues.add(point);
                        }
                    }
                } else {
                    List<Object> values = new ArrayList<>();
                    for (Object col : colSlices) values.add(castPrimitive(col));
                    ObjInfo point = new ObjInfo();
                    point.setValues(values);
                    tmpvalues.add(point);
                }
            }
        }

        return tmpvalues;
    }

    // flatten nested arrays/objects into string list (codes/uoms)
    private void flattenIntoStringList(Object raw, List<String> out) {
        if (raw == null) return;
        if (raw.getClass().isArray()) {
            int len = Array.getLength(raw);
            for (int i = 0; i < len; i++) {
                Object elem = Array.get(raw, i);
                if (elem != null && elem.getClass().isArray()) {
                    int sublen = Array.getLength(elem);
                    for (int j = 0; j < sublen; j++) {
                        Object sub = Array.get(elem, j);
                        if (sub != null) out.add(sub.toString());
                    }
                } else if (elem != null) out.add(elem.toString());
            }
        } else {
            out.add(raw.toString());
        }
    }

    private Object castPrimitive(Object val) {
        if (val == null) return null;
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
            log.debug("Unknown data type: {}", val.getClass().getName());
            return val;
        }
    }

    public static Object getIgnoreCase(Map<String, Object> map, String key) {
        if (map == null) return null;
        for (String k : map.keySet()) {
            if (k.equalsIgnoreCase(key)) return map.get(k);
        }
        return null;
    }

    private static Map<String, Object> printAttributes(Node node) {
        Map<String, Object> resultAttr = new HashMap<>();
        Map<String, Attribute> attrs = node.getAttributes();
        for (Map.Entry<String, Attribute> entry : attrs.entrySet()) {
            String attrName = entry.getKey();
            Attribute attr = entry.getValue();
            Object value = attr.getData();
            resultAttr.put(attrName, value);
        }
        return resultAttr;
    }

    private static double round6(double value) {
        return Math.round(value * 1_000_000d) / 1_000_000d;
    }

    private int getInt(Map<String, Object> attr, String key, int defaultVal) {
        Object v = getIgnoreCase(attr, key);
        if (v instanceof Number) return ((Number) v).intValue();
        try {
            return v != null ? Integer.parseInt(v.toString()) : defaultVal;
        } catch (Exception e) {
            return defaultVal;
        }
    }
}
