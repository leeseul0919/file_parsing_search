package com.example.file_parsing_search.Util;

import com.example.file_parsing_search.Dto.CapabilityDto;
import com.example.file_parsing_search.Parser.ObjectParser;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class FileManager {
    private final List<CapabilityDto> fileObjects;
    private final String[] fileTypes = {"iso8211","hdf5","gml"};
    private final Map<String, ObjectParser> parserMap;

    public FileManager(List<ObjectParser> parsers) {
        //처음에 실행할 때 System.getProperty("user.dir") + "/uploads" 안에 있는 파일들 읽어서
        //CapabilityDto 객체로 만들어서 fileObjects에다가 저장해두기?

        parserMap = parsers.stream().collect(Collectors.toMap(ObjectParser::getSupportedFileType, Function.identity()));
        System.out.println(parserMap);
        this.fileObjects = new ArrayList<>();
    }

    public ObjectParser getParserByFileType(String fileType) {
        return parserMap.get(fileType);
    }

    @PostConstruct
    public void init() {
        System.out.println("파일 정보 세팅");
        fileObjects.clear();
        loadFiles();
    }

    private void loadFiles() {
        System.out.println("파일 로드 시작");

        String uploadDir = System.getProperty("user.dir") + "/uploads/";

        for(String tmptype : fileTypes) {
            String tmpuploadDir = uploadDir + tmptype;
            File dir = new File(tmpuploadDir);

            if (!dir.exists() || !dir.isDirectory()) {
                System.out.println("폴더가 존재하지 않습니다: " + tmpuploadDir);
                continue;
            }

            File[] files = dir.listFiles();
            if (files == null) continue;

            for (File file: files) {
                if (file.isFile()) {
                    System.out.println(file.getName()+" "+tmptype);

                    //1. 해당 파일 타입에 맞는 파서 꺼내기
                    ObjectParser parser = getParserByFileType(tmptype);

                    //2. 파서로 파일 경로 넘겨서 객체 목록과 bbox 정보 가져오기
                    CapabilityDto capabilityinfo = parser.getcapability(file.getAbsolutePath(), tmptype);

                    fileObjects.add(capabilityinfo);
                }
            }
        }
        System.out.println("파일 로드 끝");
    }

    public List<CapabilityDto> capabilityResponse() {
        return fileObjects; // 비어있으면 size() == 0 인 상태로 그대로 반환
    }

    public Optional<CapabilityDto> getFileInfo(String filename) {
        for (CapabilityDto tmp : fileObjects) {
            if (tmp.getFileName().equals(filename)) {
                return Optional.of(tmp);
            }
        }
        return Optional.empty();
    }
}
