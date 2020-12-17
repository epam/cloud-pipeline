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

import com.epam.pipeline.autotests.ao.LogAO;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.util.Set;

import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.ao.ClusterMenuAO.nodeLabel;
import static com.epam.pipeline.autotests.ao.LogAO.Status.SUCCESS;
import static com.epam.pipeline.autotests.ao.LogAO.taskWithName;
import static com.epam.pipeline.autotests.ao.Primitive.EXEC_ENVIRONMENT;
import static com.epam.pipeline.autotests.ao.Primitive.NODE_IMAGE;
import static com.epam.pipeline.autotests.ao.Primitive.STATUS;
import static java.lang.String.format;
import static java.util.stream.Collectors.toSet;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

public class CustomNodeImagesForRunsTest extends AbstractSeveralPipelineRunningTest {
    private final String pipeline1 = "pipe-config-" + Utils.randomSuffix();
    private final String pipeline2 = "pipe-config-" + Utils.randomSuffix();
    private String runID1517_1 = "";
    private String testAmi = "";

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
        final String[] cloudRegion = new String[1];
        library()
                .createPipeline(pipeline1)
                .createPipeline(pipeline2)
                .clickOnDraftVersion(pipeline1)
                .configurationTab()
                .editConfiguration("default", profile -> cloudRegion[0] = profile
                        .expandTab(EXEC_ENVIRONMENT)
                        .getCloudRegion());

        String[] amis = navigationMenu()
                .settings()
                .switchToPreferences()
                .getAmisFromClusterNetworksConfigPreference(cloudRegion[0]);
        assertNotEquals(amiValue(amis[0]), testAmi = amiValue(amis[1]),
                "Amis are the same for different instance_masks.");
        library()
                .clickOnDraftVersion(pipeline1)
                .codeTab()
                .clickOnFile("config.json")
                .editFile(configuration -> addInstanceImageToConfig(configuration, testAmi))
                .saveAndCommitWithMessage("test: Add instance image")
                .runPipeline()
                .launch(this);
        final Set<String> logMess =
                runsMenu()
                .showLog(runID1517_1 = getLastRunId())
                .instanceParameters(instance ->
                        instance.ensure(NODE_IMAGE, text(testAmi)))
                .waitForCompletion()
                .click(taskWithName("InitializeNode"))
                .ensure(STATUS, SUCCESS.reached)
                .logMessages()
                .collect(toSet());
        runsMenu()
                .completedRuns()
                .showLog(runID1517_1)
                .logContainsMessage(logMess, format("Image: %s", testAmi))
                .logContainsMessage(logMess, format("Specified in configuration image %s will be used", testAmi));
    }

    @Test (dependsOnMethods = {"checkCustomNodeImageForThePipelineRun"})
    @TestCase("1517_2")
    public void checkNodeReuseAfterTheCustomNodeImageRun() {
        library()
                .clickOnDraftVersion(pipeline2)
                .runPipeline()
                .launch(this)
                .showLog(getLastRunId())
                .instanceParameters(instance ->
                        instance.ensureNotVisible(NODE_IMAGE));
        String nodeName =
                clusterMenu()
                .waitForTheNode(pipeline1, runID1517_1)
                .getNodeName(runID1517_1);
        clusterMenu()
                .waitForTheNode(pipeline2, getLastRunId())
                .click(nodeLabel(format("RUN ID %s", getLastRunId())), LogAO::new)
                .waitForCompletion();
        library()
                .clickOnDraftVersion(pipeline1)
                .runPipeline()
                .launch(this)
                .showLog(getLastRunId());
        assertEquals(clusterMenu()
                .waitForTheNode(getLastRunId())
                .getNodeName(getLastRunId()), nodeName);
    }

    private String addInstanceImageToConfig(String code, String image) {
        if (code.contains("\"instance_image\"")) {
            return code;
        }
        return code.replace("\"configuration\" : {",
                format("\"configuration\" : {\n\"instance_image\" : \"%s\",", image));
    }

    private String amiValue(String value) {
        return value.replace("\",", "")
                .replace("\"ami\": \"", "").replaceAll(" ", "");
    }
}
