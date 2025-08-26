package com.example.file_parsing_search.util;

import com.example.file_parsing_search.dto.CapabilityDto;
import com.example.file_parsing_search.parser.ObjectParser;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import java.io.File;
import java.io.IOException;
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
        
        // Todo: stream, collect 문법 알아두기, Component 애너테이션으로 해두면 자동으로 인식되던데 그 원리 알기
        parserMap = parsers.stream().collect(Collectors.toMap(ObjectParser::getSupportedFileType, Function.identity()));
        log.info(parserMap.values().toString());
        this.fileObjects = new ArrayList<>();
    }

    public ObjectParser getParserByFileType(String fileType) {
        return parserMap.get(fileType);
    }

    // 파일을 중간에 추가하거나 삭제할 때 쓸 수도 있어서 생성자 말고 메소드로 따로 빼놓음, 대신 처음에도 자동으로 되게끔 postconstruct로 생성자 다음으로 실행되게 해놓음  
    @PostConstruct
    public void init() throws ParserConfigurationException, IOException, SAXException, XPathExpressionException {
        log.info("파일 정보 세팅");
        List<CapabilityDto> next = new ArrayList<>();
        next = loadFiles();
        fileObjects = next;
        log.info("파일 로드 끝, 파일 개수: {}",fileObjects.size());
    }

    private List<CapabilityDto> loadFiles() throws ParserConfigurationException, IOException, SAXException, XPathExpressionException {
        log.info("파일 로드 시작");
        List<CapabilityDto> resultFiles = new ArrayList<>();

        File baseDir = new File(uploadDir);
        if (!baseDir.exists() || !baseDir.isDirectory()) {
            log.error("기본 업로드 폴더가 없습니다: {}", uploadDir);    // Todo: 폴더 없을 때 예외 발생, 폴더 인식 못하는 경우?
            return resultFiles;
        }

        // 일단 모든 파일 다 들고와서
        List<File> files = new ArrayList<>();
        findFilesRecursively(baseDir, files);

        // 확장자 맞는 것만 골라내서 CapabilityDto 객체로 만들어놓고 관리
        for (File file : files) {
            if (file.isFile()) {
                String ext = getExtension(file.getPath()).toLowerCase();
                String fileType = getFileTypeByExtension(ext);

                // 1차로 대상 확장자가 아닌거 거르기
                if (fileType == null) {
                    continue;
                }

                // 2차로 확장자로 파서를 찾았을 때 없는 경우 거르기,
                ObjectParser parser = getParserByFileType(fileType);
                if (parser == null) {
                    continue;
                }

                CapabilityDto capabilityInfo = parser.getcapability(file.getAbsolutePath(), fileType);
                if(capabilityInfo != null) resultFiles.add(capabilityInfo);
            }
        }
        return resultFiles;
    }

    //폴더 안에 들어있는 파일들까지 찾아내기 위해 dfs로 모든 폴더들을 다 가보도록 함
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

    private String getExtension(String filepath) {
        int lastDot = filepath.lastIndexOf('.');
        if (lastDot == -1 || lastDot == filepath.length() - 1) return "";
        return filepath.substring(lastDot + 1);
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
        //내부 리스트 인스턴스 자체를 돌려주는건 위험함(setter로 변경 가능하기 때문에, 방어적 복사 사용해보기
        return List.copyOf(fileObjects);
    }

    public Optional<CapabilityDto> getFileInfo(String filepath) {
        if (filepath == null) return Optional.empty();
        String normalizedpath = filepath.replace("\\", "/").trim();
        for (CapabilityDto tmp : fileObjects) {
            if (tmp.getFilePath().equals(normalizedpath)) {
                return Optional.of(tmp);
            }
        }
        return Optional.empty();
    }
}
