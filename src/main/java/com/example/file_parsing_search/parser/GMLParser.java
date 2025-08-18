package com.example.file_parsing_search.parser;

import com.example.file_parsing_search.dto.CapabilityDto;
import com.example.file_parsing_search.dto.GetObjectRequestDto;
import com.example.file_parsing_search.dto.SearchObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
public class GMLParser implements ObjectParser{
    @Value("${file.upload-dir}")
    private String uploadDir;

    @Override
    public String getSupportedFileType() {
        return "gml";  // 이 파서는 gml
    }

    @Override
    public CapabilityDto getcapability(String filePath, String fileType) throws ParserConfigurationException, IOException, SAXException {
        String fileName = Paths.get(filePath).getFileName().toString();

        List<String> objectList = new ArrayList<>();
        List<List<Double>> fileBBOX = new ArrayList<>();

        // --- 파일 파싱 되었을 때 여기로 변경 ---
        String gmlFilePath = filePath;

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = (Document) builder.parse(gmlFilePath);
        doc.getDocumentElement().normalize();  // dom tree 구조로 만들어 놓기

        //2. 파일 열고 객체 목록들과 파일의 bbox 정보 가져오기
        //3. ObjectList, fileBBOX에 넣고
        double lowerLon = 0.0;
        double lowerLat = 0.0;
        double upperLon = 0.0;
        double upperLat = 0.0;
        String epsg = "";

        NodeList bboxInfo = doc.getElementsByTagName("gml:boundedBy");
        if (bboxInfo.getLength() > 0) {
            Element boundedBy = (Element) bboxInfo.item(0); // gml:boundedBy는 한개니까 item(0)으로 요소 노드로 가져와서

            NodeList envelopeList = boundedBy.getElementsByTagName("gml:Envelope"); // 이 요소 노드의 gml:Envelope 태그로 가져와서
            if (envelopeList.getLength() > 0) {
                Element envelope = (Element) envelopeList.item(0);  // gml:Envelope도 한개니까 item(0)으로 element로 가져와서

                //각 정보에 해당하는 태그로 추출하기
                String srsName = envelope.getAttribute("srsName");
                String[] srsnameSplit = srsName.split(":");
                epsg = srsnameSplit[srsnameSplit.length - 1];

                String[] lower = envelope.getElementsByTagName("gml:lowerCorner").item(0).getTextContent().split(" ");
                lowerLon = Double.parseDouble(lower[0]);
                lowerLat = Double.parseDouble(lower[1]);
                String[] upper = envelope.getElementsByTagName("gml:upperCorner").item(0).getTextContent().split(" ");
                upperLon = Double.parseDouble(upper[0]);
                upperLat = Double.parseDouble(upper[1]);

                //System.out.println(srsName + "->" + epsg);
                //System.out.println("LowerCorner (lon, lat): (" + lowerLon + "," + lowerLat + ")");
                //System.out.println("UpperCorner (lon, lat): (" + upperLon + "," + upperLat + ")");
            }
        }
        List<Double> lower = List.of(lowerLon,lowerLat);
        List<Double> upper = List.of(upperLon,upperLat);
        fileBBOX.add(lower);
        fileBBOX.add(upper);

        NodeList memberList = doc.getElementsByTagName("member");   //"member"라는 태그를 가진 모든 요소를 가져옴

        for (int i=0;i< memberList.getLength();i++) {
            Element member = (Element) memberList.item(i);  // 하나씩 돌면서 요소 노드로 바꾸고
            NodeList children = member.getChildNodes(); // 요소 노드의 모든 하위 요소들을 가져와서

            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);  //하나씩 보면서

                if (child.getNodeType() == Node.ELEMENT_NODE) { // 그 자식 노드가 HTMLElement 요소 노드라면
                    Element feature = (Element) child;  // element로 받아서
                    String gmlId = feature.getAttribute("gml:id");  // 피처 객체의 "gml:id" 속성 값을 가져옴

                    if (gmlId != null && !gmlId.isEmpty()) {
                        objectList.add(gmlId);
                        //System.out.println("Found gml:id: " + gmlId);
                    } else {
                        log.info("No gml:id in member " + (i+1));
                    }
                    break;
                }
            }
        }

        /*
        // --- 파서 완성 전 mock 초기값 ---
        objectList.add("ObjectA");
        objectList.add("ObjectB");
        fileBBOX.add(0.0);
        fileBBOX.add(0.0);
        fileBBOX.add(100.0);
        fileBBOX.add(100.0);
        String epsg = "4326";
         */

        String normalizedPath = filePath.replace("\\", "/");
        return new CapabilityDto(fileName, fileType, objectList, fileBBOX, normalizedPath,epsg);
    }

    @Override
    public List<SearchObject> parse(CapabilityDto fileInfo, GetObjectRequestDto request) {
        List<SearchObject> gmlfeatures = new ArrayList<>();

        // --- 파일 파싱 되었을 때 여기로 변경 ---
        //1. System.getProperty("user.dir") + "/uploads" + "/iso8211/" + fileInfo.getFilePath()으로 접근해서 파일 로드
        //2. request.getGeometry().getGeoType(), request.getGeometry().getCoordinates() 으로 파일에 사용자가 요청한 범위 계산
        //3. 해당 범위 안에 있는 객체들 탐색, 한 객체에 대해 GeoJson 표준에 들어가는 정보들 뽑아서 SearchObject객체로 만들고
        //4. iso8211features에 add

        // --- 파서 완성 전 mock 초기값 ---
        List<?> coordinates1 = Arrays.asList(127.0, 37.0);
        SearchObject newobject1 = new SearchObject("Point",coordinates1,"Example Point","This is an example point feature.");
        List<?> coordinates2 = Arrays.asList(Arrays.asList(127.0276, 37.4979), Arrays.asList(127.0286, 37.4979), Arrays.asList(127.0286, 37.4989), Arrays.asList(127.0276, 37.4989), Arrays.asList(127.0276, 37.4979));
        SearchObject newobject2 = new SearchObject("Polygon",coordinates2,"강남역 주변","강남역 주변 지역");
        gmlfeatures.add(newobject1);
        gmlfeatures.add(newobject2);

        return gmlfeatures;
    }
}
