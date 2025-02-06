package com.epam.pipeline.entity.pipeline.run;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;

public class PipelineRunResultTest {

    public static final String NAME = "name";
    public static final String PATTERN = "pattern";
    public static final String PATH = "path";
    public static final long RUN_ID = 1L;

    @Test
    public void testCompletePipelineRunResultIsValid() {
        PipelineRunResult result = new PipelineRunResult(RUN_ID, NAME, PATTERN, Collections.singletonList(PATH));
        result.validate();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPipelineRunResultWithoutRunIdIsNotValid() {
        PipelineRunResult result = new PipelineRunResult(null, NAME, PATTERN, Collections.singletonList(PATH));
        result.validate();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPipelineRunResultWithoutFileMaskIsNotValid() {
        PipelineRunResult result = new PipelineRunResult(RUN_ID, NAME, null, Collections.singletonList(PATH));
        result.validate();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPipelineRunResultWithEmptyItemsIsNotValid() {
        PipelineRunResult result = new PipelineRunResult(null, NAME, PATTERN, Collections.emptyList());
        result.validate();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPipelineRunResultWithNullNotValid() {
        PipelineRunResult result = new PipelineRunResult(null, NAME, PATTERN, null);
        result.validate();
    }
}