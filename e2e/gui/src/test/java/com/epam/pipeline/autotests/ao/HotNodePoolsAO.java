/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.codeborne.selenide.SelenideElement;
import static com.epam.pipeline.autotests.ao.Primitive.CONDITION;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.PipelineSelectors;

import java.util.Arrays;
import java.util.Map;
import java.util.NoSuchElementException;

import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.AUTOSCALED;
import static com.epam.pipeline.autotests.ao.Primitive.CANCEL;
import static com.epam.pipeline.autotests.ao.Primitive.CLOSE;
import static com.epam.pipeline.autotests.ao.Primitive.CLOUD_REGION;
import static com.epam.pipeline.autotests.ao.Primitive.CREATE;
import static com.epam.pipeline.autotests.ao.Primitive.DELETE;
import static com.epam.pipeline.autotests.ao.Primitive.DISK;
import static com.epam.pipeline.autotests.ao.Primitive.EDIT;
import static com.epam.pipeline.autotests.ao.Primitive.ENDS_ON;
import static com.epam.pipeline.autotests.ao.Primitive.ENDS_ON_TIME;
import static com.epam.pipeline.autotests.ao.Primitive.INSTANCE_TYPE;
import static com.epam.pipeline.autotests.ao.Primitive.POOL_NAME;
import static com.epam.pipeline.autotests.ao.Primitive.REFRESH;
import static com.epam.pipeline.autotests.ao.Primitive.STARTS_ON;
import static com.epam.pipeline.autotests.ao.Primitive.STARTS_ON_TIME;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static com.epam.pipeline.autotests.utils.Utils.nameWithoutGroup;
import static java.lang.String.format;
import static java.lang.String.valueOf;
import static java.util.concurrent.TimeUnit.SECONDS;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;

public class HotNodePoolsAO  implements AccessObject<ClusterMenuAO> {

    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            entry(REFRESH, context().$(button("Refresh"))),
            entry(CREATE, context().$(button("Create")))
    );

    public CreateHotNodePoolAO clickCreatePool() {
        click(CREATE);
        return new CreateHotNodePoolAO(this);
    }

    public NodeEntry searchForNodeEntry(String node) {
        sleep(1, SECONDS);
        SelenideElement entry = getNode(node).shouldBe(visible);
        return new NodeEntry(this, entry);
    }

    private SelenideElement getNode(final String node) {
        return $(byXpath(format(
                ".//div[contains(@class, 'ool-card__header-container') and .//*[contains(text(), '%s')]]", node)))
                .closest(".cp-hot-node-pool");
    }

    public HotNodePoolsAO waitUntilRunningNodesAppear(String poolName, int count) {
        return waitUntilNodesAppear(poolName, 3, count);
    }

    public HotNodePoolsAO waitUntilActiveNodesAppear(String poolName, int count) {
        return waitUntilNodesAppear(poolName, 1, count);
    }

    private HotNodePoolsAO waitUntilNodesAppear(String poolName, int nodeType, int count) {
        NodeEntry node = searchForNodeEntry(poolName);
        int attempt = 0;
        int maxAttempts = 10;
        while (!node.getNodeCount(nodeType).equals(valueOf(count)) && attempt < maxAttempts) {
            sleep(30, SECONDS);
            click(REFRESH);
            attempt++;
        }
        return this;
    }

    public ClusterMenuAO switchToCluster() {
        context().find(byXpath("//a[.='Cluster']")).parent().click();
        return new ClusterMenuAO();
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    @Override
    public SelenideElement context() {
        return $(PipelineSelectors.visible(byId("root-content")));
    }

    public class NodeEntry implements AccessObject<NodeEntry> {
        private final HotNodePoolsAO parentAO;
        private SelenideElement entry;
        private final Map<Primitive, SelenideElement> elements;

        public NodeEntry(HotNodePoolsAO parentAO, SelenideElement entry) {
            this.parentAO = parentAO;
            this.entry = entry;
            this.elements = initialiseElements(
                    entry(EDIT, entry.find(byClassName("anticon-edit")).parent()),
                    entry(DELETE, entry.find(byClassName("anticon-delete")).parent())
            );
        }

        private String getNodeCount(int number) {
            return entry
                    .find(byXpath(format(".//div[@class='ool-card__header-container']//span[%s]", number)))
                    .getText();
        }

        public HotNodePoolsAO deleteNode(String node) {
            click(DELETE);
            new ConfirmationPopupAO(this)
                    .ensureTitleIs(format("Are you sure you want to delete \"%s\" pool?", node))
                    .ok();
            return parentAO;
        }

        @Override
        public Map<Primitive, SelenideElement> elements() {
            return elements;
        }
    }

    public static class CreateHotNodePoolAO extends PopupAO<CreateHotNodePoolAO, HotNodePoolsAO> {

        private final Map<Primitive, SelenideElement> elements = initialiseElements(
                entry(CLOSE, context().find(byClassName("ant-modal-close"))),
                entry(CANCEL, context().find(button("CANCEL"))),
                entry(CREATE, context().find(button("CREATE"))),
                entry(POOL_NAME, context().find(byText("Pool name:"))
                        .find(byXpath("following-sibling::div//input"))),
                entry(STARTS_ON, context()
                        .find(byXpath(".//span[.='Starts on:']/following-sibling::div/div[@role='combobox']"))),
                entry(STARTS_ON_TIME, context()
                        .find(byXpath(".//span[.='Starts on:']/following-sibling::span/input"))),
                entry(ENDS_ON, context()
                        .find(byXpath(".//span[.='Ends on:']/following-sibling::div/div[@role='combobox']"))),
                entry(ENDS_ON_TIME, context()
                        .find(byXpath(".//span[.='Ends on:']/following-sibling::span/input"))),
                entry(INSTANCE_TYPE, context().find(byXpath(".//div[.='Instance type']"))),
                entry(CLOUD_REGION, context().find(byXpath(".//div[.='Region']"))),
                entry(DISK, context().find(byText("Disk:"))
                        .find(byXpath("following-sibling::div//input"))),
                entry(AUTOSCALED, context().find(byText("Autoscaled:"))
                        .parent().find(byClassName("ant-checkbox"))),
                entry(CONDITION, context()
                        .find(byXpath(".//span[.='Condition:']/following-sibling::div/div[@role='combobox']")))
        );

        public CreateHotNodePoolAO(HotNodePoolsAO parentAO) {
            super(parentAO);
        }

        public CreateHotNodePoolAO setScheduleTime(Primitive timeBox, String time) {
            click(timeBox);
            setValue($(byXpath(".//div[@class='ant-time-picker-panel-inner']//input[@placeholder='Select time']")),
                    time);
            return this;
        }

        public CreateHotNodePoolAO setAutoscaledParameter(String param, int size) {
            setValue(context().find(byText(format("%s:", param)))
                    .find(byXpath("following-sibling::div//input")), Integer.toString(size));
            return this;
        }

        public CreateHotNodePoolAO addDockerImage(String registry, String group, String tool) {
            context().find(byText("Add docker image")).parent().click();
            context().find(byXpath(".//div[.='Docker image']")).click();
            SelenideElement el1=$(byClassName("dd-docker-registry-control__container")).find(byXpath(".//input"));
            setValue(el1, group);
            $(byClassName("ant-select-dropdown-menu"))
                    .shouldBe(enabled)
                    .findAll(byClassName("ant-select-dropdown-menu-item"))
                    .stream()
                    .filter(el -> el.findAll(byXpath(".//div/span")).texts()
                            .containsAll(Arrays.asList(registry, group, nameWithoutGroup(tool))))
                    .findFirst()
                    .orElseThrow(NoSuchElementException::new)
                    .click();
            context().find(byXpath("//div[@title='latest']")).waitUntil(visible, C.DEFAULT_TIMEOUT);
            sleep(5, SECONDS);
            return this;
        }

        public CreateHotNodePoolAO addFilter(String filter) {
            context().find(byText("Add filter")).parent().click();
            context().find(byXpath("//*[contains(@class, 'ant-select-selection__placeholder') and contains(., 'Select property')]")).click();
            $(byClassName("ilters-control__column")).$(byClassName("ant-select-dropdown-menu"))
                    .shouldBe(enabled)
                    .findAll(byClassName("ant-select-dropdown-menu-item"))
                    .stream()
                    .filter(el -> el.text()
                            .contains(filter))
                    .findFirst()
                    .orElseThrow(NoSuchElementException::new)
                    .click();
            return this;
        }

        public CreateHotNodePoolAO addRunOwnerFilterValue(String value) {
            context().find(By.xpath(".//div[.='Select owner']")).shouldBe(visible, enabled).click();
            context().$(byClassName("ilters-control__column")).find(byClassName("ant-select-search__field"))
                    .sendKeys(Keys.chord(Keys.CONTROL, "a"), value);
            $(By.xpath(String.format("//li[.='%s']", value))).click();
            return this;
        }

        @Override
        public HotNodePoolsAO ok() {
            sleep(1, SECONDS);
            click(CREATE);
            sleep(2, SECONDS);
            return this.parent();
        }

        @Override
        public Map<Primitive, SelenideElement> elements() {
            return elements;
        }

        @Override
        public SelenideElement context() {
            return $(PipelineSelectors.visible(byClassName("ant-modal-content")));
        }
    }
}
