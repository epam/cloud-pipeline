/*
 * Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.execution;

import io.fabric8.kubernetes.api.model.Toleration;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

public class PodSpecMapperHelperTest {

    public static final String LABEL_KEY = "label-key";
    public static final String ANOTHER_LABEL_KEY = "another-label-key";
    public static final String LABEL_VALUE = "label-value";
    public static final String EMPTY = "";

    @Test
    public void tolerationLabelWithoutValueShouldBeMappedToTolerationWithExistsOperator() {
        final List<Toleration> tolerations = PodSpecMapperHelper
                .buildTolerations(Collections.singletonMap(LABEL_KEY, EMPTY));
        Assert.assertEquals(1, tolerations.size());
        final Toleration toleration = tolerations.get(0);
        Assert.assertEquals(LABEL_KEY, toleration.getKey());
        Assert.assertEquals(PodSpecMapperHelper.TOLERATION_OP_EXISTS, toleration.getOperator());
    }

    @Test
    public void tolerationLabelWithoutValueShouldBeMappedToTolerationWithEqualsOperator() {
        final List<Toleration> tolerations = PodSpecMapperHelper
                .buildTolerations(Collections.singletonMap(LABEL_KEY, LABEL_VALUE));
        Assert.assertEquals(1, tolerations.size());
        final Toleration toleration = tolerations.get(0);
        Assert.assertEquals(LABEL_KEY, toleration.getKey());
        Assert.assertEquals(LABEL_VALUE, toleration.getValue());
        Assert.assertEquals(PodSpecMapperHelper.TOLERATION_OP_EQUALS, toleration.getOperator());
    }

    @Test
    public void shouldBePossibleToSpecifySeveralTolerations() {
        final List<Toleration> tolerations = PodSpecMapperHelper
                .buildTolerations(
                    new LinkedHashMap<String, String>() {{
                        put(LABEL_KEY, LABEL_VALUE);
                        put(ANOTHER_LABEL_KEY, EMPTY);
                    }}
                );
        Assert.assertEquals(2, tolerations.size());
        Toleration toleration = tolerations.get(0);
        Assert.assertEquals(LABEL_KEY, toleration.getKey());
        Assert.assertEquals(LABEL_VALUE, toleration.getValue());
        Assert.assertEquals(PodSpecMapperHelper.TOLERATION_OP_EQUALS, toleration.getOperator());

        toleration = tolerations.get(1);
        Assert.assertEquals(ANOTHER_LABEL_KEY, toleration.getKey());
        Assert.assertEquals(PodSpecMapperHelper.TOLERATION_OP_EXISTS, toleration.getOperator());
    }
}
