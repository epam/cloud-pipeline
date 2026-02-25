package com.epam.pipeline.dts.sync.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class IlluminaValidator {

    public boolean validateIlluminaFolder(final String runFolder) {
        log.info("Validating Illumina folder structure for path {}", runFolder);
        final Path runInfoXml = getRequiredFile(runFolder, "RunInfo.xml");
        getRequiredFile(runFolder, "RunParameters.xml");
        getRequiredFile(runFolder, "Data/Intensities", "s.locs");

        try (InputStream xml = Files.newInputStream(runInfoXml)) {
            final DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            final Document doc = builder.parse(xml);
            doc.getDocumentElement().normalize();
            int cycleCount = getValue(doc, "Read", "NumCycles");
            int lanesCount = getValue(doc, "FlowcellLayout", "LaneCount");

            final Set<Integer> surfaces = new HashSet<>();
            final Map<String, List<String>> tiles = new HashMap<>();
            final NodeList tileNodes = doc.getElementsByTagName("Tile");

            for (int i = 0; i < tileNodes.getLength(); i++) {
                final Node tile = tileNodes.item(i);
                final String[] tileId = tile.getTextContent().split("_");
                final String lane = tileId[0];
                final String name = tileId[1];
                tiles.putIfAbsent(lane, new ArrayList<>());
                tiles.get(lane).add(tile.getTextContent());
                surfaces.add(Integer.parseInt(name.substring(0, 1)));
            }

            log.info("[{}] Cycles count: {}", runFolder, cycleCount);
            log.info("[{}] Lane count: {}", runFolder, lanesCount);
            log.info("[{}] Surfaces list: {}", runFolder, surfaces);
            log.info("[{}] Tiles count:", runFolder);
            tiles.forEach((lane, tile) -> {
                log.info("[{}] Lane {}: {}", runFolder, lane, tile.size());
            });

            if (!checkFilterFiles(runFolder, tiles)) {
                return false;
            } else {
                log.info("All filter files are present for {}", runFolder);
            }

            if (!checkCbclFiles(runFolder, lanesCount, cycleCount, surfaces)) {
                return false;
            } else {
                log.info("All cbcl files are present for {}", runFolder);
            }
            return true;
        } catch (ParserConfigurationException | SAXException | IOException e) {
            log.error("Failed to validate folder {}", runFolder);
            log.error(e.getMessage(), e);
            return false;
        }
    }

    private static boolean checkCbclFiles(final String runFolder,
                                          final int lanesCount,
                                          final int cycleCount,
                                          final Set<Integer> surfaces) {
        for (int lane = 1; lane <= lanesCount; lane++) {
            for (int cycle = 1; cycle < cycleCount; cycle++) {
                for (int surface : surfaces) {
                    final File cbcl = Paths.get(runFolder,
                            String.format("Data/Intensities/BaseCalls/L00%d/C%d.1/L00%d_%d.cbcl",
                                    lane, cycle, lane, surface)).toFile();
                    if (!cbcl.isFile()) {
                        log.error("Cbcl file {} is not present. Folder {} is incomplete.", cbcl, runFolder);
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean checkFilterFiles(final String runFolder,
                                            final Map<String, List<String>> tiles) {
        return tiles.entrySet().stream().anyMatch(entry ->
                entry.getValue().stream().anyMatch(tile -> {
                    final File filter = Paths.get(runFolder,
                            String.format("Data/Intensities/BaseCalls/L00%s", entry.getKey()),
                            String.format("s_%s.filter", tile)).toFile();
                    if (!filter.isFile()) {
                        log.error("Filter file {} is not present. Folder {} is incomplete.", filter, runFolder);
                        return false;
                    }
                    return true;
                }));
    }

    private int getValue(final Document doc, final String elementName, final String attributeName) {
        final NodeList read = doc.getElementsByTagName(elementName);
        int value = 0;
        for (int i = 0; i < read.getLength(); i++) {
            value = value + Integer.parseInt(read.item(i).getAttributes().getNamedItem(attributeName).getNodeValue());
        }
        return value;
    }

    private Path getRequiredFile(final String runFolder, final String... parts) {
        final Path path = Paths.get(runFolder, parts);
        final File file = path.toFile();
        Assert.isTrue(file.isFile(), String.format("Required file %s doesn't exist.", path.toAbsolutePath()));
        return path;
    }
}
