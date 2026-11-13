/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.cluster.pool;

import com.epam.pipeline.entity.cluster.AMIConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SuppressWarnings("deprecation")
public class NodePoolInstanceImageTest {

    private static final String CONFIGURED = "ami-configured";
    private static final String DEPRECATED = "ami-deprecated";

    @Test
    public void shouldLaunchFromTheConfiguredImage() {
        final NodePool pool = pool(DEPRECATED, CONFIGURED);

        assertEquals(CONFIGURED, pool.resolveInstanceImage());
        assertEquals(CONFIGURED, pool.toRunInstance().getNodeImage());
    }

    @Test
    public void shouldStillHonourTheDeprecatedImageWhenNoneIsConfigured() {
        assertEquals(DEPRECATED, pool(DEPRECATED, null).toRunInstance().getNodeImage());
        assertEquals(DEPRECATED, pool(DEPRECATED, " ").toRunInstance().getNodeImage());
    }

    @Test
    public void shouldLeaveTheImageToTheRegionWhenThePoolNamesNone() {
        assertNull(pool(null, null).toRunInstance().getNodeImage());
    }

    private static NodePool pool(final String instanceImage, final String ami) {
        final NodePool pool = new NodePool();
        pool.setInstanceImage(instanceImage);
        if (ami != null) {
            final AMIConfiguration configuration = new AMIConfiguration();
            configuration.setAmi(ami);
            pool.setAmiConfiguration(configuration);
        }
        return pool;
    }
}
