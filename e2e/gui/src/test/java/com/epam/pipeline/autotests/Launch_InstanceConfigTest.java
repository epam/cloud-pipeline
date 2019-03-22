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
package com.epam.pipeline.autotests;

import com.epam.pipeline.autotests.ao.PipelineRunFormAO;
import com.epam.pipeline.autotests.ao.Template;
import com.epam.pipeline.autotests.utils.*;
import org.testng.annotations.Test;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.value;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byText;
import static com.epam.pipeline.autotests.ao.Primitive.DISK;
import static com.epam.pipeline.autotests.ao.Primitive.DOCKER_IMAGE;
import static com.epam.pipeline.autotests.ao.Primitive.INSTANCE_TYPE;
import static com.epam.pipeline.autotests.utils.Conditions.expandedTab;
import static com.epam.pipeline.autotests.utils.Json.selectProfileWithName;
import static com.epam.pipeline.autotests.utils.Json.transferringJsonToObject;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.collapsiblePanel;

public class Launch_InstanceConfigTest extends AbstractAutoRemovingPipelineRunningTest {

    private final String dockerImage = String.format("%s/library/%s", C.DEFAULT_REGISTRY_IP, C.LUIGI_IMAGE);
    private final String instanceType = C.DEFAULT_INSTANCE;
    private final String instanceDisk = "25";

    @Test
    @TestCase("EPMCMBIBPC-368")
    public void preparePipelineAndValidateInstanceType() {
        navigationMenu()
                .createPipeline(Template.SHELL, getPipelineName())
                .firstVersion()
                .codeTab()
                .clickOnFile("config.json")
                .editFile(transferringJsonToObject(profiles -> {
                    final ConfigurationProfile profile = selectProfileWithName("default", profiles);
                    profile.configuration.instanceSize = instanceType;
                    profile.configuration.instanceDisk = instanceDisk;
                    profile.configuration.dockerImage = dockerImage;
                    return profiles;
                }))
                .saveAndCommitWithMessage("test: Add output parameter in configuration file")
                .runPipeline()
                .ensure(INSTANCE_TYPE, text(instanceType))
                .ensure(byText(getPipelineName()), visible)
                .ensure(byText("Estimated price per hour:"), visible)
                .ensure(button("Launch"), visible, enabled)
                .ensure(collapsiblePanel("Exec environment"), visible, expandedTab)
                .ensure(collapsiblePanel("Advanced"), visible, expandedTab)
                .ensure(collapsiblePanel("Parameters"), visible, expandedTab)
                .ensure(button("Add parameter"), visible, enabled)
                .ensure(DOCKER_IMAGE, value(dockerImage));
    }

    @Test(dependsOnMethods = {"preparePipelineAndValidateInstanceType"})
    @TestCase("EPMCMBIBPC-369")
    public void validateDiskStorage() {
        new PipelineRunFormAO(getPipelineName())
                .ensure(DISK, value(instanceDisk));
    }

    @Test(dependsOnMethods = {"validateDiskStorage"})
    @TestCase("EPMCMBIBPC-370")
    public void validateDockerImage() {
        new PipelineRunFormAO(getPipelineName())
                .ensure(DOCKER_IMAGE, value(dockerImage));
    }
}
