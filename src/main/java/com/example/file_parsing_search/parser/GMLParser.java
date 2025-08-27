package com.example.file_parsing_search.parser;

import com.example.file_parsing_search.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.*;
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
    public CapabilityDto getcapability(String filePath, String fileType) throws ParserConfigurationException, IOException, SAXException, XPathExpressionException {
        XPath xpath = XPathFactory.newInstance().newXPath();

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

        Element root = doc.getDocumentElement();
        NodeList bboxInfo = (NodeList) xpath.evaluate(".//*[local-name()='boundedBy']", root, XPathConstants.NODESET);
        if (bboxInfo.getLength() == 1) {
            Element boundedBy = (Element) bboxInfo.item(0); // boundedBy는 한개니까 item(0)으로 요소 노드로 가져와서

            NodeList envelopeList = (NodeList) xpath.evaluate(".//*[local-name()='Envelope']", boundedBy, XPathConstants.NODESET); // 이 요소 노드의 gml:Envelope 태그로 가져와서
            if (envelopeList.getLength() == 1) {
                Element envelope = (Element) envelopeList.item(0);  // gml:Envelope도 한개니까 item(0)으로 element로 가져와서

                //각 정보에 해당하는 태그로 추출하기
                String srsName = envelope.getAttribute("srsName");
                epsg = srsName;

                Node nodeLower = (Node) xpath.evaluate(".//*[local-name()='lowerCorner']",envelope,XPathConstants.NODE);
                Node nodeUpper = (Node) xpath.evaluate(".//*[local-name()='upperCorner']",envelope,XPathConstants.NODE);
                if(nodeLower != null && nodeUpper != null) {
                    String[] lower = nodeLower.getTextContent().trim().split(" ");
                    lowerLon = Double.parseDouble(lower[0]);
                    lowerLat = Double.parseDouble(lower[1]);
                    String[] upper = nodeUpper.getTextContent().trim().split(" ");
                    upperLon = Double.parseDouble(upper[0]);
                    upperLat = Double.parseDouble(upper[1]);
                }
            }
        }
        List<Double> lower = List.of(lowerLon,lowerLat);
        List<Double> upper = List.of(upperLon,upperLat);
        fileBBOX.add(lower);
        fileBBOX.add(upper);

        NodeList memberList = (NodeList) xpath.evaluate(".//*[local-name()='member']", doc, XPathConstants.NODESET);   //"member"라는 태그를 가진 모든 요소를 가져옴
        List<String> tmpList = new ArrayList<>();

        for (int i=0;i< memberList.getLength();i++) {
            if(memberList.item(i).getNodeType() == Node.ELEMENT_NODE) {
                Element member = (Element) memberList.item(i);  // 하나씩 돌면서 요소 노드로 바꾸고
                NodeList memberChildNodes = member.getElementsByTagName("*");   //모든 자식노드들 중 요소 노드들을 불러서
                Element firstChild = (Element) memberChildNodes.item(0);    //그 중에서 가장 앞에 있는 노드가 객체 타입 정의

                tmpList.add(firstChild.getTagName());   //태그 이름을 집어넣기
            }
        }
        Set<String> tmpSet = new HashSet<String>(tmpList);  //객체 종류 목록이니까 중복 제거해서
        List<String> resultList = new ArrayList<>(tmpSet);

        String normalizedPath = filePath.replace("\\", "/");
        return new CapabilityDto(fileType, resultList, fileBBOX, normalizedPath,epsg);
    }

    @Override
    public List<SearchObject> parse(GetObjectRequestDto request, Polygon polygon) throws Exception {
        GeometryFactory gf = new GeometryFactory();
        XPath xpath = XPathFactory.newInstance().newXPath();
        List<SearchObject> gmlfeatures = new ArrayList<>();

        //서비스 로직에서 요청이 들어온 파일 경로가 있는지 체크하고 request 넘겨주니까 그냥 그대로 request의 getFilePath 사용
        String gmlFilePath = request.getFilePath().replace("\\", "/");

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = (Document) builder.parse(gmlFilePath);
        doc.getDocumentElement().normalize();   //모든 요소들을 dom tree 구조로 만들어 두고

        NodeList memberList = (NodeList) xpath.evaluate(".//*[local-name()='member']", doc, XPathConstants.NODESET);
        log.info("member: " + memberList.getLength());
        for (int i=0;i< memberList.getLength();i++) {
            Element member = (Element) memberList.item(i);  // 하나씩 돌면서 요소 노드로 바꾸고

            NodeList memberChildNodes = member.getElementsByTagName("*");   //모든 자식노드들 중 요소 노드들을 불러서
            Element firstChild = (Element) memberChildNodes.item(0);    //그 중 첫번째꺼는 객체 타입
            String gmlId = firstChild.getAttribute("gml:id");   //gml:id라는 속성 값에 객체 id
            log.info("gml:id >>" + gmlId);

            List<ObjGroupInfo> tmpGroupInfo = new ArrayList<>();
            ObjGroupInfo tmpGroup = new ObjGroupInfo();
            List<ObjInfo> tmpObjInfos = new ArrayList<>();

            NodeList geoList = (NodeList) xpath.evaluate(".//*[local-name()='geometry']", member, XPathConstants.NODESET); //geometry 노드들을 불러서
            log.info("gml geometry >> "+geoList.getLength());
            //List<GeometryInfo> responseGeo = new ArrayList<>();
            for(int j=0;j< geoList.getLength();j++) {
                Element geoChild = (Element) geoList.item(j);
                NodeList pointList = (NodeList) xpath.evaluate(".//*[local-name()='Point']", geoChild, XPathConstants.NODESET);
                NodeList surfaceList = (NodeList) xpath.evaluate(".//*[local-name()='Surface']", geoChild, XPathConstants.NODESET);
                NodeList polygonList = (NodeList) xpath.evaluate(".//*[local-name()='Polygon']", geoChild, XPathConstants.NODESET);
                NodeList curveList = (NodeList) xpath.evaluate(".//*[local-name()='Curve']", geoChild, XPathConstants.NODESET);
                log.info("Point >> " + pointList.getLength());
                log.info("Surface >> " + surfaceList.getLength());
                log.info("Polygon >> " + polygonList.getLength());
                log.info("Curve >> " + curveList.getLength());

                for(int k=0;k<pointList.getLength();k++) {
                    Node tmpPoint = pointList.item(k);
                    Node pointNode = (Node) xpath.evaluate(".//*[local-name()='pos']",tmpPoint,XPathConstants.NODE);
                    List<Object> tmpP = new ArrayList<>();
                    if(pointNode != null) {
                        String[] coords = pointNode.getTextContent().trim().split("\\s+");
                        Point p = gf.createPoint(new Coordinate(Double.parseDouble(coords[0]), Double.parseDouble(coords[1])));
                        if(polygon.contains(p)) {
                            tmpP.add(Double.parseDouble(coords[0]));
                            tmpP.add(Double.parseDouble(coords[1]));
                        }
                    }
                    if(!tmpP.isEmpty()) {
                        //GeometryInfo tmpresponse = new GeometryInfo("Point",tmpP);
                        ObjInfo tmpObj = new ObjInfo("Point",tmpP,null);
                        tmpObjInfos.add(tmpObj);
                    }
                }

                for(int k=0;k<surfaceList.getLength();k++) {
                    List<Object> tmpSurfaceResult = new ArrayList<>();
                    Node tmpSurface = surfaceList.item(k);
                    tmpSurfaceResult = surfaceFromXml(tmpSurface,polygon);
                    if(!tmpSurfaceResult.isEmpty()) {
                        ObjInfo tmpObj = new ObjInfo("Surface",tmpSurfaceResult,null);
                        tmpObjInfos.add(tmpObj);
                    }
                }

                for(int k=0;k<polygonList.getLength();k++) {
                    List<Object> tmpPolygonResult = new ArrayList<>();
                    Node tmpSurface = polygonList.item(k);
                    tmpPolygonResult = polygonFromXml(tmpSurface,polygon);
                    if(!tmpPolygonResult.isEmpty()) {
                        System.out.println("polygon input");
                        ObjInfo tmpObj = new ObjInfo("Polygon",tmpPolygonResult,null);
                        tmpObjInfos.add(tmpObj);
                    }
                }

                for(int k=0;k<curveList.getLength();k++) {
                    List<Object> tmpCurveResult = new ArrayList<>();
                    Node tmpCurve = curveList.item(k);
                    tmpCurveResult = curveFromXml(tmpCurve,polygon);
                    if(!tmpCurveResult.isEmpty()) {
                        System.out.println("curve input");
                        ObjInfo tmpObj = new ObjInfo("Curve",tmpCurveResult,null);
                        tmpObjInfos.add(tmpObj);
                    }
                }
            }
            tmpGroup.setObjInfo(tmpObjInfos);
            tmpGroupInfo.add(tmpGroup);
            SearchObject newobject = new SearchObject(firstChild.getTagName(),gmlId,null,null,tmpGroupInfo);
            gmlfeatures.add(newobject);
        }
        log.info("total: " + gmlfeatures.size());
        return gmlfeatures;
    }

    public List<Object> surfaceFromXml(Node node, Polygon polygon) throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();
        GeometryFactory gf = new GeometryFactory();
        List<Object> result = new ArrayList<>();

        NodeList patchesNodes = (NodeList) xpath.evaluate(".//*[local-name()='patches']", node, XPathConstants.NODESET);

        for (int i = 0; i < patchesNodes.getLength(); i++) {
            Node patch = patchesNodes.item(i);

            Node exteriorNode = (Node) xpath.evaluate(".//*[local-name()='exterior']", patch, XPathConstants.NODE);
            if (exteriorNode != null) {
                List<Object> exteriorRing = parseLinearRing(exteriorNode, xpath);
                if (!exteriorRing.isEmpty()) {
                    Polygon exteriorPolygon = createPolygonFromRing(exteriorRing, gf);
                    if (polygon.intersects(exteriorPolygon)) {
                        List<Object> surface = new ArrayList<>();
                        surface.add(exteriorRing);

                        // interior
                        NodeList interiorNodes = (NodeList) xpath.evaluate(".//*[local-name()='interior']", patch, XPathConstants.NODESET);
                        List<List<Object>> interiorGroup = new ArrayList<>();
                        for (int j = 0; j < interiorNodes.getLength(); j++) {
                            List<Object> interiorRing = parseLinearRing(interiorNodes.item(j), xpath);
                            if (!interiorRing.isEmpty()) {
                                interiorGroup.add(interiorRing);
                            }
                        }

                        if (!interiorGroup.isEmpty()) {
                            surface.add(interiorGroup);
                        }

                        result.add(surface);
                    }
                }
            }
        }

        return result;
    }

    public List<Object> curveFromXml(Node node, Polygon polygon) throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();
        GeometryFactory gf = new GeometryFactory();
        List<Object> result = new ArrayList<>();

        NodeList segmentsNodes = (NodeList) xpath.evaluate(".//*[local-name()='segments']", node, XPathConstants.NODESET);
        for (int i = 0; i < segmentsNodes.getLength(); i++) {
            List<Object> lineCoords = parseLinearRing(segmentsNodes.item(i), xpath);
            if (!lineCoords.isEmpty()) {
                LineString line = createLineStringFromCoords(lineCoords, gf);
                if (polygon.intersects(line)) {
                    result.add(lineCoords);
                }
            }
        }
        return result;
    }

    public List<Object> polygonFromXml(Node node, Polygon polygon) throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();
        GeometryFactory gf = new GeometryFactory();
        List<Object> result = new ArrayList<>();

        Node exteriorNode = (Node) xpath.evaluate(".//*[local-name()='exterior']", node, XPathConstants.NODE);
        if (exteriorNode != null) {
            List<Object> exteriorRing = parseLinearRing(exteriorNode, xpath);
            if (!exteriorRing.isEmpty()) {
                Polygon exteriorPolygon = createPolygonFromRing(exteriorRing, gf);
                if (polygon.intersects(exteriorPolygon)) {
                    List<Object> polygonGeom = new ArrayList<>();
                    polygonGeom.add(exteriorRing);

                    // interior
                    NodeList interiorNodes = (NodeList) xpath.evaluate(".//*[local-name()='interior']", node, XPathConstants.NODESET);
                    List<List<Object>> interiorGroup = new ArrayList<>();
                    for (int i = 0; i < interiorNodes.getLength(); i++) {
                        List<Object> interiorRing = parseLinearRing(interiorNodes.item(i), xpath);
                        if (!interiorRing.isEmpty()) {
                            interiorGroup.add(interiorRing);
                        }
                    }

                    if (!interiorGroup.isEmpty()) {
                        polygonGeom.add(interiorGroup);
                    }

                    result.add(polygonGeom);
                }
            }
        }

        return result;
    }

    private List<Object> parseLinearRing(Node ringNode, XPath xpath) throws Exception {
        Node posListNode = (Node) xpath.evaluate(".//*[local-name()='posList']", ringNode, XPathConstants.NODE);
        List<Object> coordinates = new ArrayList<>();

        if (posListNode != null) {
            String[] tokens = posListNode.getTextContent().trim().split("\\s+");
            //System.out.println(posListNode.getTextContent());
            for (int i = 0; i < tokens.length; i += 2) {
                List<Object> tmppos = new ArrayList<>();
                tmppos.add(Double.parseDouble(tokens[i]));
                tmppos.add(Double.parseDouble(tokens[i + 1]));
                coordinates.add(tmppos);
            }
        }
        else {
            NodeList posNodes = (NodeList) xpath.evaluate(".//*[local-name()='pos']", ringNode, XPathConstants.NODESET);
            for (int i = 0; i < posNodes.getLength(); i++) {
                String[] coords = posNodes.item(i).getTextContent().trim().split("\\s+");
                List<Object> coord = new ArrayList<>();
                coord.add(Double.parseDouble(coords[0]));
                coord.add(Double.parseDouble(coords[1]));
                coordinates.add(coord);
            }
        }
        return coordinates;
    }

    public Polygon createPolygonFromRing(List<Object> ring, GeometryFactory gf) {
        Coordinate[] coords = ring.stream()
                .map(coord -> (List<?>) coord)
                .map(pair -> new Coordinate(
                        ((Number) pair.get(0)).doubleValue(),  // lon (X)
                        ((Number) pair.get(1)).doubleValue()   // lat (Y)
                ))
                .toArray(Coordinate[]::new);
        return gf.createPolygon(coords);
    }

    public LineString createLineStringFromCoords(List<Object> coordsList, GeometryFactory gf) {
        Coordinate[] coords = coordsList.stream()
                .map(coord -> (List<?>) coord)
                .map(pair -> new Coordinate(
                        ((Number) pair.get(0)).doubleValue(),  // lon
                        ((Number) pair.get(1)).doubleValue()   // lat
                ))
                .toArray(Coordinate[]::new);
        return gf.createLineString(coords);
    }
}
