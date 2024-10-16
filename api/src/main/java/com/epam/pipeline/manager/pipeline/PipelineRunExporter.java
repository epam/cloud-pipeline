/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.utils.DateUtils;
import com.opencsv.CSVWriter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.util.TextUtils;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static com.epam.pipeline.utils.PipelineStringUtils.formatNullable;

public class PipelineRunExporter {
    private static final List<String> HEADER = Arrays.asList("Run ID", "Run Name", "Parent Run ID", "Instance Type",
            "Tags", "Pipeline", "Docker Image", "Started Date", "Completed Date", "Owner");
    private static final String KEY_VALUE_PATTERN = "%s:%s";

    public String export(final Collection<PipelineRun> runs,
                         final String delimiter, final String fieldDelimiter) {
        final StringWriter writer = new StringWriter();
        final CSVWriter csvWriter = new CSVWriter(writer, delimiter.charAt(0));
        csvWriter.writeNext(HEADER.toArray(new String[0]), false);
        CollectionUtils.emptyIfNull(runs).stream()
                .map(run -> toLine(run, fieldDelimiter))
                .forEach(line -> csvWriter.writeNext(line, false));
        return writer.toString();
    }

    private String[] toLine(final PipelineRun run, final String fieldDelimiter) {
        final List<String> result = new ArrayList<>();
        result.add(String.valueOf(run.getId()));
        result.add(getName(run));
        result.add(formatNullable(run.getParentRunId()));
        result.add(Optional.ofNullable(run.getInstance()).map(RunInstance::getNodeType).orElse(StringUtils.EMPTY));

        final List<String> tags = new ArrayList<>();
        MapUtils.emptyIfNull(run.getTags()).forEach((k, v) -> tags.add(String.format(KEY_VALUE_PATTERN, k, v)));
        result.add(String.join(fieldDelimiter, tags));

        result.add(TextUtils.isBlank(run.getPipelineName()) || TextUtils.isBlank(run.getVersion()) ?
                StringUtils.EMPTY : String.format(KEY_VALUE_PATTERN, run.getPipelineName(), run.getVersion()));
        result.add(formatNullable(run.getDockerImage()));
        result.add(DateUtils.formatDate(run.getStartDate()));
        result.add(Optional.ofNullable(run.getEndDate())
                .map(d -> DateUtils.formatDate(run.getEndDate()))
                .orElse(StringUtils.EMPTY));
        result.add(formatNullable(run.getOwner()));
        return result.toArray(new String[0]);
    }

    private static String getName(final PipelineRun run) {
        return TextUtils.isBlank(run.getName()) ?
                TextUtils.isBlank(run.getPodId()) ? StringUtils.EMPTY : run.getPodId() :
                run.getName();
    }
}
