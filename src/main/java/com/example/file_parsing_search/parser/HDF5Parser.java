package com.example.file_parsing_search.parser;

import com.example.file_parsing_search.dto.CapabilityDto;
import com.example.file_parsing_search.dto.GeometryInfo;
import com.example.file_parsing_search.dto.GetObjectRequestDto;
import com.example.file_parsing_search.dto.SearchObject;
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

            Object objectEPSG = getIgnoreCase(fileAttr, "horizontalDatumValue");
            Object objectwest = getIgnoreCase(fileAttr, "westBoundLongitude");
            Object objectsouth = getIgnoreCase(fileAttr, "southBoundLatitude");
            Object objecteast = getIgnoreCase(fileAttr, "eastBoundLongitude");
            Object objectnorth = getIgnoreCase(fileAttr, "northBoundLatitude");
            if(objectEPSG != null) epsg = objectEPSG.toString();
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
        String hdf5FilePath = request.getFilePath();
        // --- 파일 파싱 되었을 때 여기로 변경 ---
        try (HdfFile hdfFile = new HdfFile(Paths.get(hdf5FilePath))) {
            for(Node node: hdfFile) {
                if (node instanceof Group && !node.getName().equalsIgnoreCase("Group_F")) {
                    Group objectGroup = (Group) node;
                    String objectType = objectGroup.getName();

                    // Step 2: dataCodingFormat 속성 값 가져오기
                    Map<String, Object> attributes = printAttributes(objectGroup); // 가정된 메서드
                    Object codingFormatAttr = getIgnoreCase(attributes, "dataCodingFormat");

                    if (codingFormatAttr != null) {
                        String tmpformat = codingFormatAttr.toString();
                        int format = Integer.parseInt(tmpformat);

                        for (Node subNode : objectGroup) {
                            if (subNode instanceof Group) {
                                Group dataObject = (Group) subNode;
                                String objectId = dataObject.getName();
                                Map<String, Object> subGroupAttr = new HashMap<>();
                                List<GeometryInfo> responseGeo = new ArrayList<>();
                                GeometryInfo tmpGeo = new GeometryInfo();

                                switch(format) {
                                    case 1:
                                        //System.out.println(objectId + " (coding format: " + format + ")");
                                        // parsing, extract geo info
                                        subGroupAttr = printAttributes(subNode);
                                        Object numStationsObj = getIgnoreCase(subGroupAttr, "numberOfStations");
                                        if (numStationsObj != null) {
                                            String numStationsStr = numStationsObj.toString();
                                            int numStations = Integer.parseInt(numStationsStr);
                                            List<List<Double>> stationCoordinates = new ArrayList<>();

                                            // 하위 그룹 중에 데이터셋의 컬럼이 Latitude, Longitude로 이루어져 있는 그룹을 찾고
                                            for(Node geoNode:dataObject) {
                                                if(geoNode instanceof Group) {
                                                    Group geoGroup = (Group) geoNode;
                                                    for(Node datasetNode:geoGroup) {
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
                                                            if(coordinatesDataset.getDimensions()[0] == numStations && hasLat && hasLon) {
                                                                try {
                                                                    Map<String, Object> rawData = (Map<String, Object>) coordinatesDataset.getData();
                                                                    Object lonArr = rawData.get(lonKey);
                                                                    Object latArr = rawData.get(latKey);
                                                                    float[] longitudes = (float[]) lonArr;
                                                                    float[] latitudes = (float[]) latArr;
                                                                    for (int i = 0; i < numStations; i++) {
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
                                            // numStations 만큼 (lat, lon) 쌍을 추출해서
                                            // List<List<Double>>에 넣기
                                            //System.out.println("Object Type: " + objectType);
                                            //System.out.println("Object Id: " + objectId);
                                            //System.out.println("Object Geo Type: " + dataCodingFormats.get(format));
                                            //System.out.println("Object Geo Coordinates: " + stationCoordinates);

                                            tmpGeo.setType(dataCodingFormats.get(format));
                                            tmpGeo.setCoordinates(stationCoordinates);
                                            responseGeo.add(tmpGeo);
                                            //hdf5features
                                        }
                                        break;
                                    case 2:
                                        //System.out.println(objectId + " (coding format: " + format + ")");
                                        // parsing, extract geo info
                                        subGroupAttr = printAttributes(subNode);
                                        Object gridOriginLatObj = getIgnoreCase(subGroupAttr, "gridOriginLatitude");
                                        Object gridOriginLonObj = getIgnoreCase(subGroupAttr, "gridOriginLongitude");
                                        Object gridSpacingLatObj = getIgnoreCase(subGroupAttr, "gridSpacingLatitudinal");
                                        Object gridSpacingLonObj = getIgnoreCase(subGroupAttr, "gridSpacingLongitudinal");
                                        if(gridOriginLatObj!=null && gridOriginLonObj!=null && gridSpacingLatObj!=null && gridSpacingLonObj!=null) {
                                            String gridOriginLatStr = gridOriginLatObj.toString();
                                            String gridOriginLonStr = gridOriginLonObj.toString();
                                            String gridSpacingLatStr = gridSpacingLatObj.toString();
                                            String gridSpacingLonStr = gridSpacingLonObj.toString();

                                            Double gridOriginLat = Double.parseDouble(gridOriginLatStr);
                                            Double gridOriginLon = Double.parseDouble(gridOriginLonStr);
                                            Double gridSpacingLat = Double.parseDouble(gridSpacingLatStr);
                                            Double gridSpacingLon = Double.parseDouble(gridSpacingLonStr);

                                            //System.out.println("Object Type: " + objectType);
                                            //System.out.println("Object Id: " + objectId);
                                            //System.out.println("Object Geo Type: " + dataCodingFormats.get(format));
                                            //System.out.println("Object Geo Coordinates: (" + gridOriginLat + ", " + gridOriginLon + ") ,(" + gridSpacingLat + ", " + gridSpacingLon + ")");

                                            List<Double> OriginLatLon = List.of(gridOriginLat,gridOriginLon);
                                            List<Double> SpacingLatLon = List.of(gridSpacingLat,gridSpacingLon);
                                            List<List<Double>> tmpGridGeo = List.of(OriginLatLon,SpacingLatLon);
                                            tmpGeo.setType(dataCodingFormats.get(format));
                                            tmpGeo.setCoordinates(tmpGridGeo);
                                            responseGeo.add(tmpGeo);
                                        }
                                        break;
                                    case 3:
                                    case 4:
                                    case 5:
                                    case 6:
                                    case 7:
                                    case 8:
                                    default:
                                        log.error("지원하지 않는 Coding Format입니다: " + objectId);
                                        break;
                                }
                                SearchObject tmpSearchObj = new SearchObject(objectType,objectId,responseGeo);
                                hdf5features.add(tmpSearchObj);
                            }
                        }
                    }
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        //1. System.getProperty("user.dir") + "/uploads" + "/iso8211/" + fileInfo.getFilePath()으로 접근해서 파일 로드
        //2. request.getGeometry().getGeoType(), request.getGeometry().getCoordinates() 으로 파일에 사용자가 요청한 범위 계산
        //3. 해당 범위 안에 있는 객체들 탐색, 한 객체에 대해 GeoJson 표준에 들어가는 정보들 뽑아서 SearchObject객체로 만들고
        //4. iso8211features에 add

        return hdf5features;
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
