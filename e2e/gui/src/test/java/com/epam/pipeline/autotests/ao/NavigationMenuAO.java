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
package com.epam.pipeline.autotests.ao;

import org.openqa.selenium.By;

import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.*;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.utils.Conditions.selectedMenuItem;
import static com.epam.pipeline.autotests.utils.Utils.click;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.util.concurrent.TimeUnit.SECONDS;

public class NavigationMenuAO {

    public PipelinesLibraryAO library() {
        final By pipelinesPageSelector = byId("navigation-button-pipelines");
        click(pipelinesPageSelector);
        $(pipelinesPageSelector).shouldBe(selectedMenuItem);
        $(byXpath("//*[.//*[text()[contains(.,'Library')]] and contains(@id, 'pipelines-library-content')]"))
                .waitUntil(visible, 5000);
        return new PipelinesLibraryAO();
    }

    public RunsMenuAO runs() {
        final By runsPageSelector = byId("navigation-button-runs");
        click(runsPageSelector);
        $(runsPageSelector).shouldBe(selectedMenuItem);
        $(byId("active-runs-button")).waitUntil(visible, 5000);
        return new RunsMenuAO();
    }

    public ToolsPage tools() {
        final By toolsPageSelector = byId("navigation-button-tools");
        click(toolsPageSelector);
        $(toolsPageSelector).shouldBe(selectedMenuItem);
        $(byId("current-registry-button")).waitUntil(visible, 5000);
        return new ToolsPage();
    }

    public ClusterMenuAO clusterNodes() {
        final By clusterPageSelector = byId("navigation-button-cluster");
        click(clusterPageSelector);
        $(clusterPageSelector).shouldBe(selectedMenuItem);
        $(byXpath("//*[.//*[text()[contains(.,'Cluster nodes')]] and contains(@id, 'root-content')]"))
                .waitUntil(visible, 5000);
        return new ClusterMenuAO();
    }

    public SettingsPageAO settings() {
        click(byId("navigation-button-settings"));
        sleep(1, SECONDS);
        $(byClassName("ant-modal-content")).waitUntil(visible, 5000);
        return new SettingsPageAO(new PipelinesLibraryAO());
    }

    public PipelineLibraryContentAO createPipeline(final Template template, final String name) {
        return new PipelinesLibraryAO()
                .createPipeline(template, name)
                .clickOnPipeline(name);
    }

    public AuthenticationPageAO logout() {
        boolean successfullyLoggedOut = false;
        for (int i = 0; i < 15; i++){
            $(byId("navigation-button-logout")).shouldBe(visible).click();
            sleep(1, SECONDS);
            if ($(byId("navigation-button-logout")).is(not(exist))){
                successfullyLoggedOut = true;
                break;
            }
        }
        if (successfullyLoggedOut) {
            return new AuthenticationPageAO();
        }
        throw new RuntimeException("Could not login");
    }
}
