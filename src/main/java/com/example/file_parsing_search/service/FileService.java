package com.example.file_parsing_search.service;

import com.example.file_parsing_search.domain.History;
import com.example.file_parsing_search.dto.CapabilityDto;
import com.example.file_parsing_search.dto.GetObjectRequestDto;
import com.example.file_parsing_search.dto.GetObjectResponseDto;
import com.example.file_parsing_search.dto.SearchObject;
import com.example.file_parsing_search.exception.CustomException;
import com.example.file_parsing_search.exception.ErrorCode;
import com.example.file_parsing_search.mapper.HistoryMapper;
import com.example.file_parsing_search.parser.ObjectParser;
import com.example.file_parsing_search.util.FileManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;
import java.util.List;

import org.locationtech.jts.geom.*;

@Service
@Slf4j
public class FileService {
    private final FileManager fileManager;
    private final HistoryMapper historyMapper;

    @Autowired
    public FileService(FileManager fileManager, HistoryMapper historyMapper) {
        this.fileManager = fileManager;
        this.historyMapper = historyMapper;
    }

    public String hellotest() {
        return "Hello Spring Boot";
    }

    public void fileinit() throws ParserConfigurationException, IOException, SAXException, XPathExpressionException {
        fileManager.init();
    }

    public List<CapabilityDto> capabilityService() {
        return fileManager.capabilityResponse();
    }

    public GetObjectResponseDto getObjectResponseDto(GetObjectRequestDto request) throws Exception {
        GetObjectResponseDto responsedto = new GetObjectResponseDto();

        //1. 파일명 받은걸로 파일 정보 불러오고
        CapabilityDto fileInfo = fileManager.getFileInfo(request.getFilePath())
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND_FILE));
                        //RuntimeException("파일을 찾을 수 없습니다: " + request.getFilePath()));

        //2. 파일 타입에 따른 파서 불러와서 작업 (파일 정보, request)
        ObjectParser parser = fileManager.getParserByFileType(fileInfo.getFileType());
        if (parser == null) {
            throw new CustomException(ErrorCode.INVALID_FILE_TYPE);
                    //UnsupportedOperationException("Unsupported file type: " + fileInfo.getFileType());
        }

        List<?> requestCoordinates = request.getGeometryInfo().getCoordinates();
        validateCoordinates(requestCoordinates);
        Polygon polygon = null;
        switch(request.getGeometryInfo().getType()) {
            case "Circle":
                log.info("Request Circle");
                List<Double> circleCoords = (List<Double>) requestCoordinates;
                polygon = createCircle(circleCoords.get(0), circleCoords.get(1), circleCoords.get(2));
                break;
            case "Rectangle":
                log.info("Request Rectangle");
                List<List<Double>> rectCoords = (List<List<Double>>) requestCoordinates;
                polygon = createRectangle(rectCoords);
                break;
            case "Polygon":
                log.info("Request Polygon");
                List<List<Double>> polyCoords = (List<List<Double>>) requestCoordinates;
                polygon = createPolygon(polyCoords);
                break;
            default:
                log.error("지원하지 않는 범위 형태");
                throw new CustomException(ErrorCode.INVALID_RANGE_TYPE);    // Todo: 에러 처리
        }

        List<SearchObject> objectsList = parser.parse(request, polygon);
        responsedto.setFeatures(objectsList);

        return responsedto;
    }

    public void saveHistory(History history) {
        historyMapper.insert(history);
    }

    public void validateCoordinates(List<?> coordinates) {
        if (coordinates == null || coordinates.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_PARAMETERS);
        }

        // 1차원인지 2차원인지 판별
        Object first = coordinates.get(0);

        if (first instanceof List<?>) {
            // 2차원 좌표
            for (Object inner : coordinates) {
                if (!(inner instanceof List<?> innerList)) {
                    throw new CustomException(ErrorCode.INVALID_PARAMETERS);
                }
                for (Object value : innerList) {
                    if (!(value instanceof Number)) { // Double, Integer 모두 허용
                        throw new CustomException(ErrorCode.INVALID_PARAMETERS);
                    }
                }
            }
        } else {
            // 1차원 좌표
            for (Object value : coordinates) {
                if (!(value instanceof Number)) {
                    throw new CustomException(ErrorCode.INVALID_PARAMETERS);
                }
            }
        }
    }


    public Polygon createPolygon(List<List<Double>> coords) {
        GeometryFactory gf = new GeometryFactory();
        Coordinate[] coordinates = new Coordinate[coords.size()];
        for (int i = 0; i < coords.size(); i++) {
            List<Double> c = coords.get(i);
            coordinates[i] = new Coordinate(c.get(0), c.get(1));
        }
        LinearRing shell = gf.createLinearRing(coordinates);
        return gf.createPolygon(shell, null);   //테두리랑 안에 비어있는 구멍들 정보, 구멍이 없다면 그냥 null
    }
    public Polygon createCircle(double x, double y, double radius) {
        GeometryFactory gf = new GeometryFactory();
        Point center = gf.createPoint(new Coordinate(x, y));
        return (Polygon) center.buffer(radius);
    }
    public Polygon createRectangle(List<List<Double>> coords) {
        GeometryFactory gf = new GeometryFactory();
        double minX = coords.get(0).get(0);
        double minY = coords.get(0).get(1);
        double maxX = coords.get(1).get(0);
        double maxY = coords.get(1).get(1);

        Coordinate[] coordinates = new Coordinate[] {
                new Coordinate(minX, minY),
                new Coordinate(maxX, minY),
                new Coordinate(maxX, maxY),
                new Coordinate(minX, maxY),
                new Coordinate(minX, minY)
        };

        LinearRing shell = gf.createLinearRing(coordinates);
        return gf.createPolygon(shell, null);
    }
}
