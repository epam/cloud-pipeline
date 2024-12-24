/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.pipeline.runtime;

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.entity.pipeline.run.runtime.RunRuntimeData;
import com.epam.pipeline.entity.pipeline.run.runtime.RunSyncRuntimeDataType;
import com.epam.pipeline.entity.pipeline.run.runtime.nextflow.NextflowTask;
import com.epam.pipeline.entity.pipeline.run.runtime.nextflow.NextflowTraceFile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AllArgsConstructor;
import org.apache.commons.lang.StringUtils;
import org.elasticsearch.common.io.Streams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
@AllArgsConstructor
public class PipelineRunNextflowTraceDataExtractor implements  PipelineRunRuntimeDataExtractor {

    @Autowired
    JsonMapper jsonMapper;

    @Override
    public RunSyncRuntimeDataType getDataType() {
        return RunSyncRuntimeDataType.NF_TRACE;
    }

    @Override
    public String getDataFilePath(final Map<String, String> parameters) {
        return "trace.txt";
    }

    @Override
    public RunRuntimeData parseData(final InputStream data) {
        try {
            final List<String> traceLines = Streams.readAllLines(data);
            final Map<String, Integer> header = parseHeader(traceLines.stream().findFirst().orElse(StringUtils.EMPTY));
            final Map<String, NextflowTask> tasks = traceLines.stream()
                    .skip(1)
                    .map(l -> mapLineToNextflowTaskObject(header, l))
                    .peek(this::validateNextFlowTaskObject)
                    .collect(Collectors.toMap(NextflowTask::getHash, task -> task));

            final NextflowTraceFile nextflowTraceFile = new NextflowTraceFile(tasks);

            return RunRuntimeData.builder()
                    .type(getDataType())
                    .data(jsonMapper.writeValueAsString(nextflowTraceFile))
                    .build();
        } catch (IOException e) {
            throw new IllegalStateException("Can't read nextflow trace file!", e);
        } catch (IllegalArgumentException iae) {
            throw new IllegalStateException("Invalid nextflow trace file!", iae);
        }
    }

    private Map<String, Integer> parseHeader(final String headerLine) {
        final String[] parts = splitLineByDelimiter(headerLine);
        return IntStream.range(0, parts.length)
                .mapToObj(i -> Pair.of(parts[i], i))
                .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
    }

    private NextflowTask mapLineToNextflowTaskObject(final Map<String, Integer> header, final String line) {
        final String[] parts = splitLineByDelimiter(line);
        if (header.size() != parts.length) {
            throw new IllegalStateException(
                    String.format(
                            "Nextflow trace file line contains different number of columns than header: '%s' '%s'",
                            header, line
                    )
            );
        }
        final Map<String, String> rawTaskData = header.keySet()
                .stream()
                .map(field -> Pair.of(field, parts[header.get(field)]))
                .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
        try {
            return JsonMapper.parseData(
                    jsonMapper.writeValueAsString(rawTaskData), new TypeReference<NextflowTask>(){}
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private void validateNextFlowTaskObject(final NextflowTask nextflowTask) {
        Assert.notNull(nextflowTask.getHash(), "Nextflow doesn't have hash value!");
        Assert.notNull(nextflowTask.getId(), "Nextflow doesn't have id value!");
    }

    private static String[] splitLineByDelimiter(final String headerLine) {
        return headerLine.split("\\t");
    }
}
