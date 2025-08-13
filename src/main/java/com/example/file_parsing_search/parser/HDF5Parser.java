package com.example.file_parsing_search.parser;

import com.example.file_parsing_search.dto.CapabilityDto;
import com.example.file_parsing_search.dto.GetObjectRequestDto;
import com.example.file_parsing_search.dto.SearchObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
        List<Double> fileBBOX = new ArrayList<>();

        // --- 파일 파싱 되었을 때 여기로 변경 ---
        //1. 전달받은 filePath로 접근
        //2. 파일 열고 객체 목록들과 파일의 bbox 정보 가져오기
        //3. ObjectList, fileBBOX에 넣고
        //4. Map으로 묶은뒤 반환

        // --- 파서 완성 전 mock 초기값 ---
        objectList.add("ObjectA");
        objectList.add("ObjectB");
        fileBBOX.add(0.0);
        fileBBOX.add(0.0);
        fileBBOX.add(100.0);
        fileBBOX.add(100.0);

        return new CapabilityDto(fileName, fileType, objectList, fileBBOX, filePath);
    }

    @Override
    public List<SearchObject> parse(CapabilityDto fileInfo, GetObjectRequestDto request) {
        List<SearchObject> hdf5features = new ArrayList<>();

        // --- 파일 파싱 되었을 때 여기로 변경 ---
        //1. System.getProperty("user.dir") + "/uploads" + "/iso8211/" + fileInfo.getFileName()으로 접근해서 파일 로드
        //2. request.getGeometry().getGeoType(), request.getGeometry().getCoordinates() 으로 파일에 사용자가 요청한 범위 계산
        //3. 해당 범위 안에 있는 객체들 탐색, 한 객체에 대해 GeoJson 표준에 들어가는 정보들 뽑아서 SearchObject객체로 만들고
        //4. iso8211features에 add

        // --- 파서 완성 전 mock 초기값 ---
        List<?> coordinates1 = Arrays.asList(127.0, 37.0);
        SearchObject newobject1 = new SearchObject("Point",coordinates1,"Example Point","This is an example point feature.");
        List<?> coordinates2 = Arrays.asList(Arrays.asList(127.0276, 37.4979), Arrays.asList(127.0286, 37.4979), Arrays.asList(127.0286, 37.4989), Arrays.asList(127.0276, 37.4989), Arrays.asList(127.0276, 37.4979));
        SearchObject newobject2 = new SearchObject("Polygon",coordinates2,"강남역 주변","강남역 주변 지역");
        hdf5features.add(newobject1);
        hdf5features.add(newobject2);

        return hdf5features;
    }
}
