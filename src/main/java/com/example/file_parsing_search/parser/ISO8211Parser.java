package com.example.file_parsing_search.parser;

import com.example.file_parsing_search.dto.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.gdal.ogr.Geometry;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKTReader;
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

        String isoFilePath = request.getFilePath().replace("\\", "/");
        DataSource ds = ogr.Open(isoFilePath, 0); // 0 = read-only
        if (ds == null) {
            log.info("파일 열기 실패: " + isoFilePath);
            return null;
        }

        log.info("레이어 개수: " + ds.GetLayerCount());
        WKTReader reader = new WKTReader();

        for (int i = 0; i < ds.GetLayerCount(); i++) {
            Layer layer = ds.GetLayer(i);
            FeatureDefn defn = layer.GetLayerDefn();
            System.out.println(layer.GetName() + " 필드 개수: " + defn.GetFieldCount());

            for (int j = 0; j < defn.GetFieldCount(); j++) {
                FieldDefn fieldDefn = defn.GetFieldDefn(j);
            }

            // 실제 피처 값 출력
            layer.ResetReading();
            Feature feature;

            while ((feature = layer.GetNextFeature()) != null) {
                System.out.println("-- Feature ID: " + feature.GetFID());

                SearchObject tmpSearchObject = new SearchObject();
                List<ObjGroupInfo> tmpObjGroups = new ArrayList<>();

                tmpSearchObject.setId(String.valueOf(feature.GetFID()));
                tmpSearchObject.setType(layer.GetName());

                ObjGroupInfo tmpObjGroup = new ObjGroupInfo();
                List<ObjInfo> tmpObjs = new ArrayList<>();

                /*
                for (int j = 0; j < defn.GetFieldCount(); j++) {
                    FieldDefn fieldDefn = defn.GetFieldDefn(j);
                    String fieldName = fieldDefn.GetNameRef();
                    System.out.println("   " + fieldName + " = " + feature.GetFieldAsString(j));
                }
                 */

                Geometry geom = feature.GetGeometryRef();
                if (geom != null) {
                    String wkt = geom.ExportToWkt();
                    org.locationtech.jts.geom.Geometry jtsGeom = reader.read(wkt);

                    String geomType = jtsGeom.getGeometryType();
                    System.out.println("Geometry type: " + geomType);

                    List<Object> coordsList = new ArrayList<>();

                    switch (geomType) {
                        case "Point" -> {
                            List<List<Double>> pointRing = new ArrayList<>();
                            pointRing.add(toPair(jtsGeom.getCoordinate()));
                            coordsList.add(pointRing);
                        }
                        case "LineString" -> {
                            List<List<Double>> line = new ArrayList<>();
                            for (Coordinate c : jtsGeom.getCoordinates()) {
                                line.add(toPair(c));
                            }
                            coordsList.add(line);
                        }
                        case "Polygon" -> {
                            Polygon poly = (Polygon) jtsGeom;
                            // 외곽 링
                            coordsList.add(toRing(poly.getExteriorRing()));
                            // 내부 링들 (홀)
                            for (int k = 0; k < poly.getNumInteriorRing(); k++) {
                                coordsList.add(toRing(poly.getInteriorRingN(k)));
                            }
                        }
                        case "MultiLineString" -> {
                            MultiLineString mls = (MultiLineString) jtsGeom;
                            for (int k = 0; k < mls.getNumGeometries(); k++) {
                                LineString ls = (LineString) mls.getGeometryN(k);
                                coordsList.add(toRing(ls));
                            }
                        }
                        case "MultiPolygon" -> {
                            MultiPolygon mp = (MultiPolygon) jtsGeom;
                            for (int k = 0; k < mp.getNumGeometries(); k++) {
                                Polygon poly = (Polygon) mp.getGeometryN(k);
                                coordsList.add(toRing(poly.getExteriorRing()));
                                for (int l = 0; l < poly.getNumInteriorRing(); l++) {
                                    coordsList.add(toRing(poly.getInteriorRingN(l)));
                                }
                            }
                        }
                        default -> {
                            System.out.println("Unhandled geometry type: " + geomType);
                        }
                    }
                    ObjInfo tmpObj = new ObjInfo(geomType,coordsList,null);
                    tmpObjs.add(tmpObj);
                }
                tmpObjGroup.setTimePoint(null);
                tmpObjGroup.setObjInfo(tmpObjs);
                tmpObjGroups.add(tmpObjGroup);

                tmpSearchObject.setObjGroupInfos(tmpObjGroups);
                iso8211features.add(tmpSearchObject);
            }
        }
        ds.delete();
        return iso8211features;
    }
    private static List<Double> toPair(Coordinate c) {
        List<Double> pair = new ArrayList<>();
        pair.add(round6(c.x));
        pair.add(round6(c.y));
        return pair;
    }

    private static List<List<Double>> toRing(org.locationtech.jts.geom.LineString ring) {
        List<List<Double>> result = new ArrayList<>();
        for (Coordinate c : ring.getCoordinates()) {
            result.add(toPair(c));
        }
        return result;
    }

    private static double round6(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }
}
