package com.example.file_parsing_search.parser;

import com.example.file_parsing_search.dto.CapabilityDto;
import com.example.file_parsing_search.dto.GeometryInfo;
import com.example.file_parsing_search.dto.GetObjectRequestDto;
import com.example.file_parsing_search.dto.SearchObject;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Polygon;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.gdal.gdal.gdal;
import org.gdal.ogr.*;
import org.gdal.osr.SpatialReference;

import java.util.*;
import java.util.List;

@Component
@Slf4j
public class ISO8211Parser implements ObjectParser{
    @Value("${file.upload-dir}")
    private String uploadDir;

    @Override
    public String getSupportedFileType() {
        return "iso8211";  // 이 파서는 iso8211
    }

    @PostConstruct
    public void initGdal() {
        gdal.AllRegister();
        ogr.RegisterAll();
        log.info("GDAL 및 OGR 초기화 완료");
    }

    @Override
    public CapabilityDto getcapability(String filePath, String fileType) {
        String normalizedPath = filePath.replace("\\", "/");
        log.info(filePath);
        if (filePath.length() < 1) {
            System.out.println("Usage: java ReadISO8211 <iso8211_file>");
            return null;
        }

        DataSource ds = ogr.Open(filePath, 0); // 0 = read-only
        if (ds == null) {
            System.err.println("Failed to open file: " + filePath);
            return null;
        }

        System.out.println("Opened file: " + filePath);

        double globalMinX = Double.MAX_VALUE;
        double globalMinY = Double.MAX_VALUE;
        double globalMaxX = -Double.MAX_VALUE;
        double globalMaxY = -Double.MAX_VALUE;

        Set<String> objectTypes = new HashSet<>();

        Set<String> metadataLayerNames = Set.of(
                "DSID", "DSPM", "VRID", "FRID", "FOID", "FSPM",
                "FOVL", "FOUB", "FOND", "FOAR", "FOSN",
                "DSSI", "DSPT", "FRSP", "VRSP", "RSTA", "RVER"
        );

        int layerCount = ds.GetLayerCount();
        String CRS = "unknown";

        for (int i = 0; i < layerCount; i++) {
            Layer layer = ds.GetLayer(i);
            if (layer == null) continue;

            String layerName = layer.GetName();

            if (!metadataLayerNames.contains(layerName)) {
                objectTypes.add(layerName);
            }

            if (CRS.equals("unknown")) {
                SpatialReference sr = layer.GetSpatialRef();
                if (sr != null) {
                    String name = sr.GetAuthorityName(null);
                    String code = sr.GetAuthorityCode(null);
                    if (name != null && code != null) {
                        CRS = name + ":" + code;
                    }
                }
            }

            double[] extent = layer.GetExtent(true);
            if (extent != null) {
                globalMinX = Math.min(globalMinX, extent[0]);
                globalMaxX = Math.max(globalMaxX, extent[1]);
                globalMinY = Math.min(globalMinY, extent[2]);
                globalMaxY = Math.max(globalMaxY, extent[3]);
            }
        }

        List<Double> minPos = List.of(globalMinX, globalMinY);
        List<Double> maxPos = List.of(globalMaxX, globalMaxY);
        List<List<Double>> PosInfo = List.of(minPos, maxPos); // [ [minX, minY], [maxX, maxY] ]

        List<String> objectTypesList = new ArrayList<>(objectTypes);

        return new CapabilityDto(fileType, objectTypesList, PosInfo, normalizedPath, CRS);
    }

    @Override
    public List<SearchObject> parse(GetObjectRequestDto request, Polygon polygon) throws Exception {
        List<SearchObject> iso8211features = new ArrayList<>();

        // --- 파일 파싱 되었을 때 여기로 변경 ---
        //1. System.getProperty("user.dir") + fileInfo.getfilePath()으로 접근해서 파일 로드
        //2. request.getGeometry().getGeoType(), request.getGeometry().getCoordinates() 으로 파일에 사용자가 요청한 범위 계산
        //3. 해당 범위 안에 있는 객체들 탐색, 한 객체에 대해 GeoJson 표준에 들어가는 정보들 뽑아서 SearchObject객체로 만들고
        //4. iso8211features에 add

        // --- 파서 완성 전 mock 초기값 ---
        SearchObject newobject1 = new SearchObject("feature","0000",null,null,null);
        SearchObject newobject2 = new SearchObject("feature","0000",null,null,null);

        iso8211features.add(newobject1);
        iso8211features.add(newobject2);

        return iso8211features;
    }
}
