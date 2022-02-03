/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.preprocessing;

import com.epam.pipeline.entity.samplesheet.SampleSheet;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.util.Pair;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This class parses provided content regarding to sample sheet description:
 * https://www.illumina.com/content/dam/illumina-marketing/documents/products/technotes/sequencing-sheet-format-specifications-technical-note-970-2017-004.pdf
 * */
@SuppressWarnings({"LineLength", "HideUtilityClassConstructor"})
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SampleSheetParser {

    public static final String SAMPLE_ID_COLUMN = "Sample_ID";
    public static final String LANE_COLUMN = "Lane";

    public static final String HEADER_SECTION = "[Header]";
    public static final int HEADER_SECTION_NUMBER = 1;
    public static final String MANIFESTS_SECTION = "[Manifests]";
    public static final int MANIFESTS_SECTION_NUMBER = 2;
    public static final String READS_SECTION = "[Reads]";
    public static final int READS_SECTION_NUMBER = 3;
    public static final String SETTINGS_SECTION = "[Settings]";
    public static final int SETTINGS_SECTION_NUMBER = 4;
    public static final String DATA_SECTION = "[Data]";
    public static final int DATA_SECTION_NUMBER = 5;
    public static final String SAMPLESHEET_DELIMITER = ",";
    public static final int SECTION_DEFAULT_VALUE = 0;

    public static SampleSheet parseSampleSheet(final byte[] content) {
        try(BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(content)))) {
            final SampleSheet.SampleSheetBuilder sampleSheetBuilder = SampleSheet.builder();
            int section = SECTION_DEFAULT_VALUE;
            final List<String> lineStorage = new ArrayList<>();
            for (String line : reader.lines().filter(StringUtils::hasText)
                    .filter(l -> !l.matches("^,+$")).collect(Collectors.toList())) {
                if (line.isEmpty()) {
                    continue;
                }

                int newSection = checkForNewSection(line, sampleSheetBuilder);
                if (newSection != SECTION_DEFAULT_VALUE) {
                    switch (section) {
                        case HEADER_SECTION_NUMBER:
                            sampleSheetBuilder.header(parseMapLines(lineStorage));
                            break;
                        case MANIFESTS_SECTION_NUMBER:
                            sampleSheetBuilder.manifests(parseMapLines(lineStorage));
                            break;
                        case READS_SECTION_NUMBER:
                            sampleSheetBuilder.reads(parseReadLines(lineStorage));
                            break;
                        case SETTINGS_SECTION_NUMBER:
                            sampleSheetBuilder.settings(parseMapLines(lineStorage));
                            break;
                        case DATA_SECTION_NUMBER:
                            throw new IllegalStateException("Data section should be last one!");
                        default:
                    }
                    section = newSection;
                    lineStorage.clear();
                    continue;
                }
                lineStorage.add(line);
            }

            sampleSheetBuilder.dataHeader(parseDataHeader(lineStorage));
            sampleSheetBuilder.dataLines(
                    // filter header line
                    lineStorage.stream().skip(1).collect(Collectors.toList())
            );

            SampleSheet result = sampleSheetBuilder.build();
            Assert.state(result.isWithHeader(), "Sample sheet has no header!");
            Assert.state(result.isWithData(), "Sample sheet has no data!");
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot parse sample sheet", e);
        }
    }

    private static List<String> parseDataHeader(final List<String> lines) {
        Assert.state(lines.size() > 1, "No data lines in sample sheet!");
        String dataHeaderLine = lines.stream().findFirst().orElseThrow(
            () -> new IllegalStateException("No data lines in sample sheet!"));
        return Arrays.asList(dataHeaderLine.split(","));
    }

    private static List<Integer> parseReadLines(final List<String> lineContainer) {
        return lineContainer.stream().map(Integer::getInteger).collect(Collectors.toList());
    }

    private static Map<String, String> parseMapLines(final List<String> lines) {
        return lines.stream().map(l -> {
            String[] firstSecond = l.split(SAMPLESHEET_DELIMITER);
            return Pair.of(firstSecond[0], firstSecond[1]);
        }).collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
    }

    private static int checkForNewSection(final String line, final SampleSheet.SampleSheetBuilder builder) {
        if (line.contains(HEADER_SECTION)) {
            builder.withHeader(true);
            return HEADER_SECTION_NUMBER;
        } else if (line.contains(MANIFESTS_SECTION)) {
            builder.withManifests(true);
            return MANIFESTS_SECTION_NUMBER;
        } else if (line.contains(READS_SECTION)) {
            builder.withReads(true);
            return READS_SECTION_NUMBER;
        } else if (line.contains(SETTINGS_SECTION)) {
            builder.withSettings(true);
            return SETTINGS_SECTION_NUMBER;
        } else if (line.contains(DATA_SECTION)) {
            builder.withData(true);
            return DATA_SECTION_NUMBER;
        }
        return SECTION_DEFAULT_VALUE;
    }
}
