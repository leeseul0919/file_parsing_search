package com.example.file_parsing_search.parser;

import com.example.file_parsing_search.dto.CapabilityDto;
import com.example.file_parsing_search.dto.GeometryInfo;
import com.example.file_parsing_search.dto.GetObjectRequestDto;
import com.example.file_parsing_search.dto.SearchObject;
import org.locationtech.jts.geom.Polygon;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
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
        double lowerLon = 0.0;
        double lowerLat = 0.0;
        double upperLon = 0.0;
        double upperLat = 0.0;
        String epsg = "";


        // --- 파일 파싱 되었을 때 여기로 변경 ---
        //1. 전달받은 filePath로 접근
        //2. 파일 열고 객체 목록들과 파일의 bbox 정보 가져오기
        //3. ObjectList, fileBBOX에 넣고
        //4. Map으로 묶은뒤 반환

        // --- 파서 완성 전 mock 초기값 ---
        objectList.add("ObjectA");
        objectList.add("ObjectB");
        List<Double> lower = List.of(lowerLon,lowerLat);
        List<Double> upper = List.of(upperLon,upperLat);
        fileBBOX.add(lower);
        fileBBOX.add(upper);
        epsg = "4326";

        String normalizedPath = filePath.replace("\\", "/");
        return new CapabilityDto(fileType, objectList, fileBBOX, normalizedPath, epsg);
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
}
