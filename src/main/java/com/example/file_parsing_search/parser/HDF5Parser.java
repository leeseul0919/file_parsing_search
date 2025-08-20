package com.example.file_parsing_search.parser;

import com.example.file_parsing_search.dto.CapabilityDto;
import com.example.file_parsing_search.dto.GeometryInfo;
import com.example.file_parsing_search.dto.GetObjectRequestDto;
import com.example.file_parsing_search.dto.SearchObject;
import io.jhdf.HdfFile;
import io.jhdf.api.Attribute;
import io.jhdf.api.Group;
import io.jhdf.api.Node;
import org.locationtech.jts.geom.Polygon;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

@Component
public class HDF5Parser implements ObjectParser{
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

        // --- 파일 파싱 되었을 때 여기로 변경 ---
        //1. System.getProperty("user.dir") + "/uploads" + "/iso8211/" + fileInfo.getFilePath()으로 접근해서 파일 로드
        //2. request.getGeometry().getGeoType(), request.getGeometry().getCoordinates() 으로 파일에 사용자가 요청한 범위 계산
        //3. 해당 범위 안에 있는 객체들 탐색, 한 객체에 대해 GeoJson 표준에 들어가는 정보들 뽑아서 SearchObject객체로 만들고
        //4. iso8211features에 add

        // --- 파서 완성 전 mock 초기값 ---
        List<GeometryInfo> coordinates1 = new ArrayList<>();
        List<?> mockpos1 =Arrays.asList(127.0276, 37.4979);
        GeometryInfo mockinfo1 = new GeometryInfo("Point",mockpos1);
        coordinates1.add(mockinfo1);
        SearchObject newobject1 = new SearchObject("feature","0000",coordinates1);

        List<GeometryInfo> coordinates2 = new ArrayList<>();
        List<?> mockpos2 = Arrays.asList(Arrays.asList(127.0276, 37.4979), Arrays.asList(127.0286, 37.4979), Arrays.asList(127.0286, 37.4989), Arrays.asList(127.0276, 37.4989), Arrays.asList(127.0276, 37.4979));
        GeometryInfo mockinfo2 = new GeometryInfo("Point",mockpos2);
        coordinates2.add(mockinfo2);
        SearchObject newobject2 = new SearchObject("feature","0000",coordinates2);

        hdf5features.add(newobject1);
        hdf5features.add(newobject2);

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
