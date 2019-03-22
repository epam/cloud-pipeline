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
package com.epam.pipeline.autotests.mixins;

import com.epam.pipeline.autotests.ao.ClusterMenuAO;
import com.epam.pipeline.autotests.ao.NavigationMenuAO;
import com.epam.pipeline.autotests.ao.PipelinesLibraryAO;
import com.epam.pipeline.autotests.ao.RunsMenuAO;
import com.epam.pipeline.autotests.ao.ToolsPage;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selenide.$;

public interface Navigation {

    default NavigationMenuAO navigationMenu() {
        $(byId("navigation-button-pipelines")).shouldBe(visible).click();
        return new NavigationMenuAO();
    }

    default PipelinesLibraryAO library() {
        return navigationMenu().library();
    }

    default ToolsPage tools() {
        $(byId("navigation-button-tools")).shouldBe(visible).click();
        return new ToolsPage();
    }

    default ClusterMenuAO clusterMenu() {
        $(byId("navigation-button-cluster")).shouldBe(visible).click();
        return new ClusterMenuAO();
    }

    default RunsMenuAO runsMenu() {
        $(byId("navigation-button-runs")).shouldBe(visible).click();
        return new RunsMenuAO();
    }
}
