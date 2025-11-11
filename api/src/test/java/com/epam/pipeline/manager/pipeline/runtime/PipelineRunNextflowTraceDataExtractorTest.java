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
//import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.junit.jupiter.api.Assertions.*;

public class PipelineRunNextflowTraceDataExtractorTest {

    public static final String VALID_TRACE_FILE =
            "task_id\thash\tnative_id\tname\tstatus\texit\tsubmit\tduration\trealtime\t%cpu\tpeak_rss\tpeak_vmem" +
                    "\trchar\twchar\n" +
                    "2\tbd/2fc28f\t-\ttask_name\tCACHED\t-\t2024-12-24 14:48:46.035\t5ms\t2ms\t-\t-\t-\t-\t-\n";

    public static final String INVALID_TRACE_FILE =
            "task_id\thash\tnative_id\tname\tstatus\texit\tsubmit\tduration\trealtime\t%cpu\tpeak_rss\tpeak_vmem" +
                    "\trchar\twchar\n" +
                    "bd/2fc28f\t-\ttask_name\tCACHED\t-\t2024-12-24 14:48:46.035\t5ms\t2ms\t-\t-\t-\t-\t-\n";

    public static final String INVALID_TRACE_FILE_WITHOUT_HASH =
            "task_id\tnative_id\tname\tstatus\texit\tsubmit\tduration\trealtime\t%cpu\tpeak_rss\tpeak_vmem" +
                    "\trchar\twchar\n" +
                    "2\t-\ttask_name\tCACHED\t-\t2024-12-24 14:48:46.035\t5ms\t2ms\t-\t-\t-\t-\t-\n";

    public static final String NF_TASK_ID = "2";
    public static final int NF_TRACE_FILE_N_TASKS = 1;
    public static final String NF_TASK_HASH = "bd/2fc28f";

    private final JsonMapper jsonMapper = new JsonMapper();;

    @BeforeEach    public void setUp() {
        jsonMapper.afterPropertiesSet();
    }

    @Test
    public void shouldParseDataForValidTraceFile() {
        final PipelineRunNextflowTraceDataExtractor extractor = new PipelineRunNextflowTraceDataExtractor(jsonMapper);
        final RunRuntimeData result = extractor.parseData(
                Collections.emptyMap(), new ByteArrayInputStream(VALID_TRACE_FILE.getBytes(StandardCharsets.UTF_8)));

        assertEquals(RunSyncRuntimeDataType.NF_TRACE, result.getType());
        final NextflowTraceFile nfTraceFile = (NextflowTraceFile) result.getData();

        assertEquals(NF_TRACE_FILE_N_TASKS, nfTraceFile.getTasks().size());
        final NextflowTask task = nfTraceFile.getTasks().values().stream().findFirst().orElse(null);
        assertEquals(NF_TASK_ID, task.getId());
        assertEquals(NF_TASK_HASH, task.getHash());
    }

    @Test
    public void shouldThrowIfParseNotValidTraceFile() {
        final PipelineRunNextflowTraceDataExtractor extractor = new PipelineRunNextflowTraceDataExtractor(jsonMapper);
        assertThrows(IllegalStateException.class, () -> extractor.parseData(Collections.emptyMap(),
            new ByteArrayInputStream(INVALID_TRACE_FILE.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    public void shouldThrowIfParseTraceFileWithoutExpectedFields() {
        final PipelineRunNextflowTraceDataExtractor extractor = new PipelineRunNextflowTraceDataExtractor(jsonMapper);
        assertThrows(IllegalStateException.class,
            () -> extractor.parseData(Collections.emptyMap(),
                new ByteArrayInputStream(INVALID_TRACE_FILE_WITHOUT_HASH.getBytes(StandardCharsets.UTF_8))));
    }

}
