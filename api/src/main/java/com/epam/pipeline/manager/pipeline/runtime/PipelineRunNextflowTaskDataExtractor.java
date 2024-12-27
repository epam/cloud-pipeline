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

import com.epam.pipeline.entity.pipeline.run.runtime.RunRuntimeData;
import com.epam.pipeline.entity.pipeline.run.runtime.RunSyncRuntimeDataType;
import com.epam.pipeline.entity.pipeline.run.runtime.nextflow.NextflowTaskFile;
import lombok.AllArgsConstructor;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Map;

@Component
@AllArgsConstructor
public class PipelineRunNextflowTaskDataExtractor implements  PipelineRunRuntimeDataExtractor {

    public static final String HASH_PARAMETER_KEY = "hash";
    public static final String TYPE_PARAMETER_KEY = "type";

    @Override
    public RunSyncRuntimeDataType getDataType() {
        return RunSyncRuntimeDataType.NF_TASK;
    }

    @Override
    public String getDataFilePath(final Map<String, String> parameters) {
        validateParameters(parameters);
        return Paths.get(
                parameters.get(HASH_PARAMETER_KEY),
                getNextflowTaskFileType(parameters).getFilename()
        ).toString();
    }

    private static void validateParameters(final Map<String, String> parameters) {
        Assert.notNull(parameters,
                "Additional parameters 'hash', 'type' should be provided to get Nextflow task file.");
        Assert.isTrue(parameters.containsKey(HASH_PARAMETER_KEY),
                String.format("Additional parameter '%s' should be provided to get Nextflow task file.",
                        HASH_PARAMETER_KEY));
        Assert.isTrue(parameters.containsKey(TYPE_PARAMETER_KEY),
                String.format("Additional parameter '%s' should be provided to get Nextflow task file.",
                        TYPE_PARAMETER_KEY));
    }

    @Override
    public RunRuntimeData parseData(final Map<String, String> parameters, final InputStream data) {
        validateParameters(parameters);
        try {
            final NextflowTaskFile nextflowTaskFile = new NextflowTaskFile(
                    getNextflowTaskFileType(parameters),
                    IOUtils.toString(data, StandardCharsets.UTF_8)
            );
            return RunRuntimeData.builder()
                    .type(getDataType())
                    .data(nextflowTaskFile)
                    .build();
        } catch (IOException e) {
            throw new IllegalStateException("Can't read nextflow trace file!", e);
        } catch (IllegalArgumentException iae) {
            throw new IllegalStateException("Invalid nextflow trace file!", iae);
        }
    }

    private static NextflowTaskFile.Type getNextflowTaskFileType(final Map<String, String> parameters) {
        return NextflowTaskFile.Type.valueOf(parameters.get(TYPE_PARAMETER_KEY));
    }

}
