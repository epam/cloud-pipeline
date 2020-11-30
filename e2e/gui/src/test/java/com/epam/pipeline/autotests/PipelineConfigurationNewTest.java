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
package com.epam.pipeline.autotests;

import com.epam.pipeline.autotests.ao.Primitive;
import com.epam.pipeline.autotests.ao.Template;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.Test;

import static com.epam.pipeline.autotests.ao.Primitive.EXEC_ENVIRONMENT;

public class PipelineConfigurationNewTest extends AbstractSeveralPipelineRunningTest {
    private final String pipeline1 = "pipe-config-" + Utils.randomSuffix();
    private final String pipeline2 = "pipe-config-" + Utils.randomSuffix();
    private String clusterNetworksConfig = "cluster.networks.config";

    @Test
    @TestCase("1517_1")
    public void checkCustomNodeImageForThePipelineRun() {
        final String[] cloudRegion = {""};
        library()
                .createPipeline(pipeline1)
                .createPipeline(pipeline2)
                .clickOnDraftVersion(pipeline1)
                .configurationTab()
                .editConfiguration("default", profile -> {
                    cloudRegion[0] = profile
                            .expandTab(EXEC_ENVIRONMENT)
                            .getCloudRegion();});

        navigationMenu()
                .settings()
                .switchToPreferences();
    }
}
