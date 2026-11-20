/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.vmmonitor.model.vm;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * The SMTP sender renders the MISSING-LABELS and MISSING-NODE templates in-process with Velocity 1.7, which
 * reads bean getters only, so the summaries the templates read must keep them.
 */
public class MissingSummaryTemplateTest {

    private static final String NODE_NAME = "node-1";
    private static final String INSTANCE_TYPE = "m5.large";
    private static final Long POOL_ID = 7L;

    @Test
    public void shouldRenderTheFieldsOfAMissingLabelsSummary() {
        final MissingLabelsSummary summary = new MissingLabelsSummary(NODE_NAME, Collections.singletonList("label"),
                INSTANCE_TYPE, null, null, POOL_ID);

        assertThat(render("$summary.nodeName $summary.instanceType $summary.correspondingPoolId", summary),
                is(NODE_NAME + " " + INSTANCE_TYPE + " " + POOL_ID));
    }

    @Test
    public void shouldRenderTheFieldsOfAMissingNodeSummary() {
        final MissingNodeSummary summary = new MissingNodeSummary(null, Collections.emptyList(), POOL_ID);

        assertThat(render("$summary.matchingPoolId $summary.matchingRuns.size()", summary), is(POOL_ID + " 0"));
    }

    private static String render(final String template, final Object summary) {
        final VelocityContext context = new VelocityContext();
        context.put("summary", summary);
        final StringWriter out = new StringWriter();
        Velocity.evaluate(context, out, "test", template);
        return out.toString();
    }
}
