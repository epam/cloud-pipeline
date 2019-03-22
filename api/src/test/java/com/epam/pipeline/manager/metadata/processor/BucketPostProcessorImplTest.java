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

package com.epam.pipeline.manager.metadata.processor;

import com.epam.pipeline.entity.metadata.PipeConfValue;
import org.junit.Test;

import static org.junit.Assert.*;

public class BucketPostProcessorImplTest {

    private static final String PARAM_NAME = "name";
    private static final String BUCKET_NAME_ORIGINAL = "BucketName_1";
    private static final String BUCKET_NAME_NORMALIZED = "bucketname-1";
    private static final String S3_PATH_ORIGINAL = "s3://BucketName_1/path";
    private static final String S3_PATH_NORMALIZED = "s3://bucketname-1/path";
    private static final String CP_PATH_ORIGINAL = "cp://BucketName_1/path";
    private static final String CP_PATH_NORMALIZED = "cp://bucketname-1/path";

    private BucketPostProcessorImpl bucketPostProcessor = new BucketPostProcessorImpl();

    @Test
    public void shouldHandleSimpleBucketName() {
        PipeConfValue value = new PipeConfValue(bucketPostProcessor.supportedType(), BUCKET_NAME_ORIGINAL);
        bucketPostProcessor.process(PARAM_NAME, value);
        assertEquals(BUCKET_NAME_NORMALIZED, value.getValue());
    }

    @Test
    public void shouldHandleS3URL() {
        PipeConfValue value = new PipeConfValue(bucketPostProcessor.supportedType(), S3_PATH_ORIGINAL);
        bucketPostProcessor.process(PARAM_NAME, value);
        assertEquals(S3_PATH_NORMALIZED, value.getValue());
    }

    @Test
    public void shouldHandleCPURL() {
        PipeConfValue value = new PipeConfValue(bucketPostProcessor.supportedType(), CP_PATH_ORIGINAL);
        bucketPostProcessor.process(PARAM_NAME, value);
        assertEquals(CP_PATH_NORMALIZED, value.getValue());
    }
}