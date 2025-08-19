package com.example.file_parsing_search.parser;

import com.example.file_parsing_search.dto.CapabilityDto;
import com.example.file_parsing_search.dto.GeometryInfo;
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
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

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

        List<List<Double>> fileBBOX = new ArrayList<>();

        String gmlFilePath = filePath;

        //1. 파일 열고
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = (Document) builder.parse(gmlFilePath);
        doc.getDocumentElement().normalize();  //모든 요소들을 dom tree 구조로 만들어 놓기

        //2. 객체 목록들과 파일의 bbox 정보 가져오기
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
            }
        }
        List<Double> lower = List.of(lowerLon,lowerLat);
        List<Double> upper = List.of(upperLon,upperLat);
        fileBBOX.add(lower);
        fileBBOX.add(upper);

        NodeList memberList = doc.getElementsByTagName("member");   //"member"라는 태그를 가진 모든 요소를 가져옴
        List<String> tmpList = new ArrayList<>();

        for (int i=0;i< memberList.getLength();i++) {
            Element member = (Element) memberList.item(i);  // 하나씩 돌면서 요소 노드로 바꾸고
            NodeList memberChildNodes = member.getElementsByTagName("*");   //모든 자식노드들 중 요소 노드들을 불러서
            Element firstChild = (Element) memberChildNodes.item(0);    //그 중에서 가장 앞에 있는 노드가 객체 타입 정의

            tmpList.add(firstChild.getTagName());   //태그 이름을 집어넣기
            //System.out.println(firstChild.getTagName());
        }
        Set<String> tmpSet = new HashSet<String>(tmpList);  //객체 종류 목록이니까 중복 제거해서
        List<String> resultList = new ArrayList<>(tmpSet);

        String normalizedPath = filePath.replace("\\", "/");
        return new CapabilityDto(fileType, resultList, fileBBOX, normalizedPath,epsg);
    }

    @Override
    public List<SearchObject> parse(GetObjectRequestDto request) throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();
        List<SearchObject> gmlfeatures = new ArrayList<>();

        //서비스 로직에서 요청이 들어온 파일 경로가 있는지 체크하고 request 넘겨주니까 그냥 그대로 request의 getFilePath 사용
        String gmlFilePath = request.getFilePath();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = (Document) builder.parse(gmlFilePath);
        doc.getDocumentElement().normalize();   //모든 요소들을 dom tree 구조로 만들어 두고

        NodeList memberList = doc.getElementsByTagName("member");
        for (int i=0;i< memberList.getLength();i++) {
            Element member = (Element) memberList.item(i);  // 하나씩 돌면서 요소 노드로 바꾸고

            NodeList memberChildNodes = member.getElementsByTagName("*");   //모든 자식노드들 중 요소 노드들을 불러서
            Element firstChild = (Element) memberChildNodes.item(0);    //그 중 첫번째꺼는 객체 타입
            String gmlId = firstChild.getAttribute("gml:id");   //gml:id라는 속성 값에 객체 id
            //System.out.println(firstChild.getTagName()+": "+gmlId);

            NodeList geoList = member.getElementsByTagName("geometry"); //geometry 노드들을 불러서
            List<GeometryInfo> responseGeo = new ArrayList<>();
            for(int j=0;j< geoList.getLength();j++) {
                Element geoChild = (Element) geoList.item(j);
                NodeList pointList = (NodeList) xpath.evaluate(".//*[local-name()='Point']", geoChild, XPathConstants.NODESET);
                NodeList surfaceList = (NodeList) xpath.evaluate(".//*[local-name()='Surface']", geoChild, XPathConstants.NODESET);
                NodeList polygonList = (NodeList) xpath.evaluate(".//*[local-name()='Polygon']", geoChild, XPathConstants.NODESET);

                for(int k=0;k<pointList.getLength();k++) {
                    Node tmpPoint = pointList.item(k);
                    Node pointNode = (Node) xpath.evaluate(".//*[local-name()='pos']",tmpPoint,XPathConstants.NODE);
                    List<Double> tmpP = new ArrayList<>();
                    if(pointNode != null) {
                        String[] coords = pointNode.getTextContent().trim().split("\\s+");
                        tmpP.add(Double.parseDouble(coords[0]));
                        tmpP.add(Double.parseDouble(coords[1]));
                    }
                    if(!tmpP.isEmpty()) {
                        GeometryInfo tmpresponse = new GeometryInfo("Point",tmpP);
                        responseGeo.add(tmpresponse);
                    }
                }

                for(int k=0;k<surfaceList.getLength();k++) {
                    List<List<List<Double>>> tmpSurfaceResult = new ArrayList<>();
                    Node tmpSurface = surfaceList.item(k);
                    tmpSurfaceResult = surfaceFromXml(tmpSurface);
                    if(!tmpSurfaceResult.isEmpty()) {
                        GeometryInfo tmpresponse = new GeometryInfo("Surface",tmpSurfaceResult);
                        responseGeo.add(tmpresponse);
                    }
                }

                for(int k=0;k<polygonList.getLength();k++) {
                    List<List<List<Double>>> tmpPolygonResult = new ArrayList<>();
                    Node tmpSurface = polygonList.item(k);
                    tmpPolygonResult = polygonFromXml(tmpSurface);
                    if(!tmpPolygonResult.isEmpty()) {
                        System.out.println("polygon input");
                        GeometryInfo tmpresponse = new GeometryInfo("Polygon",tmpPolygonResult);
                        responseGeo.add(tmpresponse);
                    }
                }
            }
            SearchObject newobject = new SearchObject(firstChild.getTagName(),gmlId,responseGeo);
            gmlfeatures.add(newobject);
        }
        return gmlfeatures;
    }

    public List<List<List<Double>>> surfaceFromXml(Node node) throws Exception {
        if(node==null || !node.hasChildNodes()) {
            throw new IllegalArgumentException("Node is null of has no child nodes");
        }
        XPath xpath = XPathFactory.newInstance().newXPath();

        List<List<List<Double>>> LinearRings = new ArrayList<>();
        NodeList patchesNodes = (NodeList) xpath.evaluate(".//*[local-name()='patches']", node, XPathConstants.NODESET);
        //List<List<Double>> LinearRings = new ArrayList<>();
        //System.out.println("patches 개수 >> " + patchesNodes.getLength());
        for(int i=0;i<patchesNodes.getLength();i++) {
            Node patch = patchesNodes.item(i);
            Node exteriorNode = (Node) xpath.evaluate(".//*[local-name()='exterior']", patch, XPathConstants.NODE);
            if(exteriorNode != null) {
                List<List<Double>> exteriorRing = parseLinearRing(exteriorNode, xpath);
                if (!exteriorRing.isEmpty()) LinearRings.add(exteriorRing);
            }

            NodeList interiorNodes = (NodeList) xpath.evaluate(".//*[local-name()='interior']", patch, XPathConstants.NODESET);
            //System.out.println("interior 개수 >> " + interiorNodes.getLength());
            for (int j = 0; j < interiorNodes.getLength(); j++) {
                List<List<Double>> interiorRing = parseLinearRing(interiorNodes.item(j), xpath);
                if (!interiorRing.isEmpty()) LinearRings.add(interiorRing);
            }
        }
        return LinearRings;
    }

    public List<List<List<Double>>> polygonFromXml(Node node) throws Exception {
        if(node==null || !node.hasChildNodes()) {
            throw new IllegalArgumentException("Node is null of has no child nodes");
        }
        XPath xpath = XPathFactory.newInstance().newXPath();

        List<List<List<Double>>> LinearRings = new ArrayList<>();
        //NodeList patchesNodes = (NodeList) xpath.evaluate(".//*[local-name()='patches']", node, XPathConstants.NODESET);
        //List<List<Double>> LinearRings = new ArrayList<>();
        //System.out.println("patches 개수 >> " + patchesNodes.getLength());
        Node exteriorNode = (Node) xpath.evaluate(".//*[local-name()='exterior']", node, XPathConstants.NODE);
        if(exteriorNode != null) {
            List<List<Double>> exteriorRing = parseLinearRing(exteriorNode, xpath);
            if (!exteriorRing.isEmpty()) LinearRings.add(exteriorRing);
        }

        NodeList interiorNodes = (NodeList) xpath.evaluate(".//*[local-name()='interior']", node, XPathConstants.NODESET);
        System.out.println("interior 개수 >> " + interiorNodes.getLength());
        for (int j = 0; j < interiorNodes.getLength(); j++) {
            List<List<Double>> interiorRing = parseLinearRing(interiorNodes.item(j), xpath);
            if (!interiorRing.isEmpty()) LinearRings.add(interiorRing);
        }

        return LinearRings;
    }

    private List<List<Double>> parseLinearRing(Node ringNode, XPath xpath) throws Exception {
        //coordinates는 첫번째 if문, coordinate는 else 과정 이것도 포함시킬까?
        Node posListNode = (Node) xpath.evaluate(".//*[local-name()='posList']", ringNode, XPathConstants.NODE);
        Node coordinatesListNode = (Node) xpath.evaluate(".//*[local-name()='coordinates']", ringNode, XPathConstants.NODE);
        List<List<Double>> coordinates = new ArrayList<>();

        if (posListNode != null) {
            String[] tokens = posListNode.getTextContent().trim().split("\\s+");
            //System.out.println(posListNode.getTextContent());
            for (int i = 0; i < tokens.length; i += 2) {
                List<Double> tmppos = new ArrayList<>();
                tmppos.add(Double.parseDouble(tokens[i]));
                tmppos.add(Double.parseDouble(tokens[i + 1]));
                coordinates.add(tmppos);
            }
        } else if (coordinatesListNode != null) {
            String[] tokens = coordinatesListNode.getTextContent().trim().split("\\s+");
            //System.out.println(posListNode.getTextContent());
            for (String token:tokens) {
                String[] xy = token.split(",");
                List<Double> tmppos = new ArrayList<>();
                tmppos.add(Double.parseDouble(xy[0]));
                tmppos.add(Double.parseDouble(xy[1]));
                coordinates.add(tmppos);
            }
        }
        else {
            NodeList posNodes = (NodeList) xpath.evaluate(".//*[local-name()='pos']", ringNode, XPathConstants.NODESET);
            for (int i = 0; i < posNodes.getLength(); i++) {
                String[] coords = posNodes.item(i).getTextContent().trim().split("\\s+");
                List<Double> coord = new ArrayList<>();
                coord.add(Double.parseDouble(coords[0]));
                coord.add(Double.parseDouble(coords[1]));
                coordinates.add(coord);
            }
        }
        return coordinates;
    }
}
