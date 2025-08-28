package com.example.file_parsing_search.parser;

import com.example.file_parsing_search.dto.*;
import com.example.file_parsing_search.exception.CustomException;
import com.example.file_parsing_search.exception.ErrorCode;
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
            log.info("Usage: java ReadISO8211 <iso8211_file>");
            return null;
        }

        DataSource ds = ogr.Open(filePath, 0); // 0 = read-only
        if (ds == null) {
            log.info("Failed to open file: " + filePath);
            return null;
        }
        log.info("Opened file: " + filePath);

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

            SpatialReference sr = layer.GetSpatialRef();
            if (sr != null) {
                String name = sr.GetAuthorityName(null);
                String code = sr.GetAuthorityCode(null);
                if (name != null && code != null) {
                    CRS = name + ":" + code;
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

    private final List<String> attrNames = List.of("rcid","grup","objl","sordat","sorind");

    @Override
    public List<SearchObject> parse(GetObjectRequestDto request, Polygon polygon) throws Exception {
        List<SearchObject> iso8211features = new ArrayList<>();
        int cnt = 0;

        if (request == null || request.getFilePath() == null || request.getFilePath().isEmpty() || polygon == null) {
            throw new CustomException(ErrorCode.NULL_FILE_PATH);
        }

        String isoFilePath = request.getFilePath().replace("\\", "/");
        DataSource ds = ogr.Open(isoFilePath, 0); // 0 = read-only
        if (ds == null) {
            log.info("파일 열기 실패: " + isoFilePath);
            throw new CustomException(ErrorCode.FAIL_OPEN_FILE);
        }

        //log.info("레이어 개수: " + ds.GetLayerCount());
        WKTReader reader = new WKTReader();

        for (int i = 0; i < ds.GetLayerCount(); i++) {
            Layer layer = ds.GetLayer(i);
            if (layer == null) {
                log.warn("Layer at index {} is null. Skipping.", i);
                continue;
            }

            FeatureDefn defn = layer.GetLayerDefn();
            layer.ResetReading();
            Feature feature;

            while ((feature = layer.GetNextFeature()) != null) {
                cnt+=1;

                List<ObjGroupInfo> tmpObjGroups = new ArrayList<>();

                ObjGroupInfo tmpObjGroup = new ObjGroupInfo();
                List<ObjInfo> tmpObjs = new ArrayList<>();

                Map<String, Object> tmpAttribute = new HashMap<>();
                for (int j = 0; j < defn.GetFieldCount(); j++) {
                    FieldDefn fieldDefn = defn.GetFieldDefn(j);
                    String fieldName = fieldDefn.GetNameRef().toLowerCase();
                    if(attrNames.contains(fieldName)) tmpAttribute.put(fieldDefn.GetNameRef(),feature.GetFieldAsString(j));
                }

                List<GeometryInfo> tmpGeoInfo = new ArrayList<>();

                Geometry geom = feature.GetGeometryRef();
                if (geom != null) {
                    String wkt = geom.ExportToWkt();
                    org.locationtech.jts.geom.Geometry jtsGeom = reader.read(wkt);

                    if (!polygon.contains(jtsGeom)) {
                        feature.delete();
                        continue;
                    }

                    String geomType = jtsGeom.getGeometryType();

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
                            coordsList.add(toRing(poly.getExteriorRing()));
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
                        case "MultiPoint" -> {
                            MultiPoint mp = (MultiPoint) jtsGeom;
                            for (int k = 0; k < mp.getNumGeometries(); k++) {
                                Point pt = (Point) mp.getGeometryN(k);
                                List<List<Double>> pointRing = new ArrayList<>();
                                pointRing.add(toPair(pt.getCoordinate()));
                                coordsList.add(pointRing);
                            }
                        }
                        default -> {
                            log.info("Unhandled geometry type: " + geomType);
                        }
                    }
                    GeometryInfo tmpGeo = new GeometryInfo(geomType, coordsList);
                    tmpGeoInfo.add(tmpGeo);
                }
                if(!tmpGeoInfo.isEmpty()) {
                    SearchObject tmpSearchObject = new SearchObject(layer.GetName(),String.valueOf(feature.GetFID()),null,null,tmpAttribute,tmpGeoInfo,null);
                    iso8211features.add(tmpSearchObject);
                }
                feature.delete();
            }
        }
        ds.delete();
        log.info("parsing end: "+cnt + ", result cnt: " + iso8211features.size());
        if(iso8211features.isEmpty()) {
            throw new CustomException(ErrorCode.NO_RESULTS_FOUND);
        }
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
