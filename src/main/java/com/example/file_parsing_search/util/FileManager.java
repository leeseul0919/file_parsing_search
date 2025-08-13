package com.example.file_parsing_search.util;

import com.example.file_parsing_search.dto.CapabilityDto;
import com.example.file_parsing_search.parser.ObjectParser;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class FileManager {
    private volatile List<CapabilityDto> fileObjects;
    private final String[] fileTypes = {"iso8211","hdf5","gml"};
    private final Map<String, ObjectParser> parserMap;

    @Value("${file.upload-dir}")
    private String uploadDir;

    public FileManager(List<ObjectParser> parsers) {
        //처음에 실행할 때 System.getProperty("user.dir") + "/uploads" 안에 있는 파일들 읽어서
        //CapabilityDto 객체로 만들어서 fileObjects에다가 저장해두기?

        parserMap = parsers.stream().collect(Collectors.toMap(ObjectParser::getSupportedFileType, Function.identity()));
        log.info(parserMap.values().toString());
        this.fileObjects = new ArrayList<>();
    }

    public ObjectParser getParserByFileType(String fileType) {
        return parserMap.get(fileType);
    }

    @PostConstruct
    public void init() {
        log.info("파일 정보 세팅");
        List<CapabilityDto> next = new ArrayList<>();
        next = loadFiles();
        fileObjects = next;
    }

    private List<CapabilityDto> loadFiles() {
        log.info("파일 로드 시작");
        List<CapabilityDto> resultFiles = new ArrayList<>();

        File baseDir = new File(uploadDir);
        if (!baseDir.exists() || !baseDir.isDirectory()) {
            log.error("기본 업로드 폴더가 없습니다: {}", uploadDir);
            return resultFiles;
        }

        List<File> files = new ArrayList<>();
        findFilesRecursively(baseDir, files);

        for (File file : files) {
            if (file.isFile()) {
                String ext = getExtension(file.getName()).toLowerCase();
                String fileType = getFileTypeByExtension(ext);

                if (fileType == null) {
                    //System.out.println("지원하지 않는 확장자 파일 무시: " + file.getAbsolutePath());
                    continue;
                }

                ObjectParser parser = getParserByFileType(fileType);
                if (parser == null) {
                    //System.out.println("해당 타입 파서 없음: " + fileType);
                    continue;
                }

                CapabilityDto capabilityInfo = parser.getcapability(file.getAbsolutePath(), fileType);
                resultFiles.add(capabilityInfo);
                //System.out.println("로드 완료: " + file.getName());
            }
        }
        log.info("파일 로드 끝");
        return resultFiles;
    }

    private void findFilesRecursively(File dir, List<File> files) {
        File[] list = dir.listFiles();
        if (list == null) return;

        for (File f : list) {
            if (f.isDirectory()) {
                findFilesRecursively(f, files);
            } else {
                files.add(f);
            }
        }
    }

    private String getExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1 || lastDot == filename.length() - 1) return "";
        return filename.substring(lastDot + 1);
    }

    private String getFileTypeByExtension(String ext) {
        return switch (ext) {
            case "gml" -> "gml";
            case "000" -> "iso8211";
            case "h5" -> "hdf5";
            default -> null;
        };
    }

    public List<CapabilityDto> capabilityResponse() {
        return List.copyOf(fileObjects); //내부 리스트 인스턴스 자체를 돌려주는건 위험함, 방어적 복사 사용해보기
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
