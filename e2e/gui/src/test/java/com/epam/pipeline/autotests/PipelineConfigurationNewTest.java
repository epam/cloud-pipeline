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

import com.epam.pipeline.autotests.ao.PipelineCodeTabAO;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.stream.Collectors;

import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.ao.Primitive.EXEC_ENVIRONMENT;
import static com.epam.pipeline.autotests.ao.Primitive.NODE_IMAGE;
import static com.epam.pipeline.autotests.ao.Primitive.RUN;
import static com.epam.pipeline.autotests.ao.Primitive.TYPE;
import static java.lang.String.format;
import static org.openqa.selenium.By.className;

public class PipelineConfigurationNewTest extends AbstractSeveralPipelineRunningTest {
    private final String pipeline1 = "pipe-config-" + Utils.randomSuffix();
    private final String pipeline2 = "pipe-config-" + Utils.randomSuffix();
    private String clusterNetworksConfig = "cluster.networks.config";

    @AfterClass(alwaysRun = true)
    public void removePipelines() {
        open(C.ROOT_ADDRESS);
        navigationMenu()
                .library()
                .removePipelineIfExists(pipeline1)
                .removePipelineIfExists(pipeline2);
    }

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

        String[] amis = navigationMenu()
                .settings()
                .switchToPreferences()
                .getAmisFromClusterNetworksConfigPreference();
        library()
                .clickOnDraftVersion(pipeline1)
                .codeTab()
                .clickOnFile("config.json")
                .editFile(configuration -> addInstanceImageToConfig(configuration, amiValue(amis[1])))
                .saveAndCommitWithMessage("test: Add instance image")
                .runPipeline()
                .launch(this);
        runsMenu()
                .showLog(getLastRunId())
                .instanceParameters(instance ->
                        instance.ensure(NODE_IMAGE, text(amiValue(amis[1]))));
    }

    private String addInstanceImageToConfig(String code, String image) {
        if (!code.contains("\"instance_image\"")) {
            return code.replace("\"configuration\" : {",
                    format("\"configuration\" : {\n\"instance_image\" : \"%s\",", image));
        }
        return code;
    }

    private String amiValue(String value) {
        return value.replace("\",", "")
                .replace("\"ami\": \"", "").replaceAll(" ", "");
    }
}
