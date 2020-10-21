package com.epam.pipeline.test.creator.pipeline;

import com.epam.pipeline.entity.pipeline.PipelineRun;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;

public class PipelineCreatorUtils {

    public static PipelineRun getPipelineRun(Long id, String owner) {
        final PipelineRun pipelineRun = new PipelineRun();
        pipelineRun.setId(id);
        pipelineRun.setOwner(owner);
        pipelineRun.setName(TEST_STRING);
        return pipelineRun;
    }
}
