/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.codeborne.selenide.Selenide;
import com.epam.pipeline.autotests.utils.C;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;

import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.actions;
import static com.epam.pipeline.autotests.utils.Conditions.selectedMenuItem;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.util.concurrent.TimeUnit.SECONDS;

public class NavigationMenuAO {

    public PipelinesLibraryAO library() {
        final By pipelinesPageSelector = byId("navigation-button-pipelines");
        $(pipelinesPageSelector).shouldBe(visible).click();
        $(pipelinesPageSelector).shouldBe(selectedMenuItem);
        $(byXpath("//*[.//*[text()[contains(.,'Library')]] and contains(@id, 'pipelines-library-content')]"))
                .waitUntil(visible, 5000);
        return new PipelinesLibraryAO();
    }

    public RunsMenuAO runs() {
        final By runsPageSelector = byId("navigation-button-runs");
        $(runsPageSelector).shouldBe(visible).click();
        $(runsPageSelector).shouldBe(selectedMenuItem);
        $(byId("active-runs-button")).waitUntil(visible, 5000);
        return new RunsMenuAO();
    }

    public ToolsPage tools() {
        final By toolsPageSelector = byId("navigation-button-tools");
        $(toolsPageSelector).shouldBe(visible).click();
        $(toolsPageSelector).shouldBe(selectedMenuItem);
        $(byId("current-registry-button")).waitUntil(visible, 5000);
        return new ToolsPage();
    }

    public ClusterMenuAO clusterNodes() {
        final By clusterPageSelector = byId("navigation-button-cluster");
        $(clusterPageSelector).shouldBe(visible).click();
        $(clusterPageSelector).shouldBe(selectedMenuItem);
        $(byXpath("//*[.//*[text()[contains(.,'Cluster nodes')]] and contains(@id, 'root-content')]"))
                .waitUntil(visible, 5000);
        return new ClusterMenuAO();
    }

    public SettingsPageAO settings() {
        $(byId("navigation-button-settings")).shouldBe(visible).click();
        sleep(1, SECONDS);
        $(byId("root-content")).waitUntil(visible, 5000);
        return new SettingsPageAO(new PipelinesLibraryAO());
    }

    public GlobalSearchAO search() {
        actions().sendKeys(Keys.chord(Keys.CONTROL, "F")).perform();
        return new GlobalSearchAO();
    }

    public PipelineLibraryContentAO createPipeline(final Template template, final String name) {
        return new PipelinesLibraryAO()
                .createPipeline(template, name)
                .clickOnPipeline(name);
    }

    public void logout() {
        boolean successfullyLoggedOut = false;
        for (int i = 0; i < 15; i++){
            sleep(1, SECONDS);
            $(byId("navigation-button-logout")).shouldBe(visible).click();
            if ("true".equals(C.AUTH_TOKEN)) {
                Selenide.clearBrowserCookies();
            }
            sleep(2, SECONDS);
            if ($(byId("navigation-button-logout")).is(not(exist))){
                successfullyLoggedOut = true;
                break;
            }
        }
        if (successfullyLoggedOut) {
            return;
        }
        throw new RuntimeException("Could not login");
    }
}
