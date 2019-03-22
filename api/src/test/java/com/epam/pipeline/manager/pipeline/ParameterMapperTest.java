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

package com.epam.pipeline.manager.pipeline;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.manager.AbstractManagerTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ParameterMapperTest extends AbstractManagerTest {

    private static final String SAMPLE_ID = "s1";
    private static final String PATIENT_ID = "p1";
    private static final String SAMPLE_NAME = "sample1";
    private static final String PATIENT_UID = "UID123";
    private static final String SCALAR_VALUE = "/hg38/test/ref.fa";
    private static final String BATCH1_NAME = "BATCH1";
    private static final String BATCH2_NAME = "BATCH2";
    private static final String BATCH1_ID = "b1";
    private static final String BATCH2_ID = "b2";
    private static final String BATCH_NAME_PARAMETER = "Batch_Name";
    private static final String BATCH_CLASS = "Batch";
    private static final String PATIENT_PIPE_PARAM = "patient";
    private static final String SAMPLE_PIPE_PARAM = "sample";
    private static final String REFERENCE_PIPE_PARAM = "reference";
    private static final String BATCHES_PIPE_PARAM = "batches";

    @Autowired
    private ParameterMapper parameterMapper;

    @Test
    public void mapParameters() throws Exception {
        Map<String, PipeConfValueVO > parametersToResolve = new HashMap<>();
        parametersToResolve.put(PATIENT_PIPE_PARAM, new PipeConfValueVO("this.Patient.Patient_ID"));
        parametersToResolve.put(SAMPLE_PIPE_PARAM, new PipeConfValueVO("this.Sample_Name"));
        parametersToResolve.put(REFERENCE_PIPE_PARAM, new PipeConfValueVO(SCALAR_VALUE));
        parametersToResolve.put(BATCHES_PIPE_PARAM, new PipeConfValueVO("this.Patient.Batch.Batch_Name"));

        Map<ParameterMapper.MetadataKey, MetadataEntity> references = new HashMap<>();
        MetadataEntity sample = new MetadataEntity();
        sample.setExternalId(SAMPLE_ID);

        Map<String, PipeConfValue> sampleData = new HashMap<>();
        sampleData.put("Patient", new PipeConfValue("Participant:ID", PATIENT_ID));
        sampleData.put("Sample_Name", new PipeConfValue(PipeConfValueVO.DEFAULT_TYPE, SAMPLE_NAME));
        sample.setData(sampleData);

        references.put(new ParameterMapper.MetadataKey("Sample", SAMPLE_ID), sample);

        MetadataEntity patient = new MetadataEntity();
        patient.setExternalId(PATIENT_ID);
        Map<String, PipeConfValue> patientData = new HashMap<>();
        patientData.put("Patient_ID", new PipeConfValue(PipeConfValueVO.DEFAULT_TYPE, PATIENT_UID));
        patientData.put("Batch", new PipeConfValue("Array[Batch]", "[\"b1\", \"b2\"]"));
        patient.setData(patientData);

        references.put(new ParameterMapper.MetadataKey("Participant", PATIENT_ID), patient);

        MetadataEntity batch1 = new MetadataEntity();
        batch1.setExternalId(BATCH1_ID);
        Map<String, PipeConfValue> batch1Data = new HashMap<>();
        batch1Data.put(
                BATCH_NAME_PARAMETER, new PipeConfValue(PipeConfValueVO.DEFAULT_TYPE, BATCH1_NAME));
        batch1.setData(batch1Data);

        references.put(new ParameterMapper.MetadataKey(BATCH_CLASS, BATCH1_ID), batch1);

        MetadataEntity batch2 = new MetadataEntity();
        batch2.setExternalId(BATCH2_ID);
        Map<String, PipeConfValue> batch2Data = new HashMap<>();
        batch2Data.put(
                BATCH_NAME_PARAMETER, new PipeConfValue(PipeConfValueVO.DEFAULT_TYPE, BATCH2_NAME));
        batch2.setData(batch2Data);

        references.put(new ParameterMapper.MetadataKey(BATCH_CLASS, BATCH2_ID), batch2);

        Map<String, PipeConfValueVO> result =
                parameterMapper.mapParameters(sample, null, parametersToResolve, references);

        assertEquals(SAMPLE_NAME, result.get(SAMPLE_PIPE_PARAM).getValue());
        assertEquals(PATIENT_UID, result.get(PATIENT_PIPE_PARAM).getValue());
        assertEquals(SCALAR_VALUE, result.get(REFERENCE_PIPE_PARAM).getValue());
        assertEquals(BATCH1_NAME + "," + BATCH2_NAME, result.get(BATCHES_PIPE_PARAM).getValue());
    }

}
