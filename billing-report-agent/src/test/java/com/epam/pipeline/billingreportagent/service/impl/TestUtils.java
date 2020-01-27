/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.billingreportagent.service.impl;

import com.epam.pipeline.billingreportagent.service.EntityToBillingRequestConverter;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.fasterxml.jackson.core.JsonFactory;
import org.apache.commons.collections.CollectionUtils;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.json.JsonXContentParser;
import org.junit.Assert;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

public final class TestUtils {

    public static final String COMMON_INDEX_PREFIX = "cp-billing-";
    public static final String RUN_BILLING_PREFIX = COMMON_INDEX_PREFIX + "pipeline-run-";
    public static final String STORAGE_BILLING_PREFIX = COMMON_INDEX_PREFIX + "storage-";

    private TestUtils() {
    }

    public static void verifyStringArray(final Collection<String> expected, final Object object) {
        ArrayList<String> actual = toStringArray(object);

        if (CollectionUtils.isEmpty(expected)) {
            if (CollectionUtils.isNotEmpty(actual)) {
                throw new IllegalArgumentException("Expected list is empty but actual not");
            }
            return;
        }

        Assert.assertEquals(expected.size(), actual.size());
        expected.forEach(element -> Assert.assertTrue(actual.contains(element)));
    }

    public static Map<String, Object> getPuttedObject(final XContentBuilder contentBuilder) throws IOException {
        JsonFactory factory = new JsonFactory();
        JsonXContentParser parser = new JsonXContentParser(NamedXContentRegistry.EMPTY, null,
                                                           factory.createParser(Strings.toString(contentBuilder)));
        return parser.map();
    }

    public static PipelineRun createTestPipelineRun(final Long runId, final String pipeline, final String tool,
                                                    final BigDecimal price, final RunInstance instance) {
        final PipelineRun run = new PipelineRun();
        run.setId(runId);
        run.setPipelineName(pipeline);
        run.setDockerImage(tool);
        run.setPricePerHour(price);
        run.setInstance(instance);
        return run;
    }

    public static RunInstance createTestInstance(final Long regionId, final String nodeType) {
        final RunInstance instance = new RunInstance();
        instance.setCloudRegionId(regionId);
        instance.setNodeType(nodeType);
        return instance;
    }

    public static String buildBillingIndex(final String prefix, final LocalDateTime syncDate) {
        return prefix
               + EntityToBillingRequestConverter.SIMPLE_DATE_FORMAT.format(syncDate.toLocalDate());
    }

    private static ArrayList<String> toStringArray(final Object object) {
        return new ArrayList<>((Collection<? extends String>) object);
    }
}
