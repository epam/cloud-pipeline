/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.python;

import com.epam.pipeline.controller.vo.TaskGraphVO;
import org.junit.Assert;
import org.junit.Test;

public class GraphReaderTest {

    @Test
    public void testParsing() {
        String output = "SimplePipeline(sample=&sample&, run_id=&run_id&)\n"
                + "IN:R1_001_fastqc.zip;L001_R2_001_fastqc.zip;AlignmentSummaryMetrics.txt;\n"
                + "OUT:\n"
                + "SimplePipeline(sample=&sample&, run_id=&run_id&) => "
                + "FastQC(sample=&sample&, run_id=&run_id&, first=True)\n"
                + "IN:L001_R1_001.fastq.gz;L001_R2_001.fastq.gz;\n"
                + "OUT:L001_R1_001_fastqc.zip;L001_R1_001_fastqc.html;\n"
                + "FastQC(sample=&sample&, run_id=&run_id&, first=True) => \n"
                + "IN:\n"
                + "OUT:L001_R1_001.fastq.gz;L001_R2_001.fastq.gz;\n"
                + "SimplePipeline(sample=&sample&, run_id=&run_id&) => "
                + "FastQC(sample=&sample&, run_id=&run_id&, first=False)\n"
                + "IN:L001_R1_001.fastq.gz;L001_R2_001.fastq.gz;\n"
                + "OUT:L001_R2_001_fastqc.zip;L001_R2_001_fastqc.html;\n"
                + "FastQC(sample=&sample&, run_id=&run_id&, first=False) => \n"
                + "IN:\n"
                + "OUT:L001_R1_001.fastq.gz;L001_R2_001.fastq.gz;\n"
                + "SimplePipeline(sample=&sample&, run_id=&run_id&) =>"
                + " AlignmentSummaryMetrics(sample=&sample&, run_id=&run_id&)\n"
                + "IN:sample.bai\n"
                + "OUT:AlignmentSummaryMetrics.txt\n";
        GraphReader reader = new GraphReader();
        TaskGraphVO graph = reader.createGraphFromScriptOutput(output);
        Assert.assertNotNull(graph);
        Assert.assertEquals(4, graph.getTasks().size());
    }
}
