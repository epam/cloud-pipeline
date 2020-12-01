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
package com.epam.pipeline.autotests.ao;

import com.codeborne.selenide.SelenideElement;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;

import static com.codeborne.selenide.Condition.appears;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.attributesMenu;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.showAttributes;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.showInstanceManagement;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;

public class ToolDescription extends ToolTab<ToolDescription> {

    private final Map<Primitive, SelenideElement> elements;

    public ToolDescription(final ToolGroup toolGroup, final String toolName) {
        super(toolGroup, toolName);
        elements = initialiseElements(super.elements(),
                entry(SHORT_DESCRIPTION, context().find(byId("short-description-text"))),
                entry(FULL_DESCRIPTION, context().find(byId("description-text-container"))),
                entry(SHOW_METADATA, $(byId("display-attributes")))
        );
    }

    @Override
    public ToolDescription open() {
        return click(DESCRIPTION);
    }

    public ToolDescription addShortDescription(final String description) {
        context().find(editButtonFor(SHORT_DESCRIPTION)).shouldBe(visible).click();
        context().find(byId("short-description-input")).shouldBe(visible).setValue(description);
        context().find(byId("short-description-edit-save-button")).shouldBe(visible).click();
        return this;
    }

    public ToolDescription addFullDescription(final String description) {
        context().find(editButtonFor(FULL_DESCRIPTION)).shouldBe(visible).click();
        context().find(byId("description-input")).shouldBe(visible).setValue(description);
        context().find(byId("description-edit-save-button")).shouldBe(visible).click();
        return this;
    }

    public ToolDescription showMetadata(final Consumer<MetadataSectionAO> action) {
        showMetadata(showAttributes);
        action.accept(new MetadataSectionAO(this));
        return this;
    }

    public ToolDescription showInstanceManagement(final Consumer<InstanceManagementSectionAO> action) {
        showMetadata(showInstanceManagement);
        action.accept(new InstanceManagementSectionAO(this));
        return this;
    }

    public PermissionTabAO permissions() {
        hover(TOOL_SETTINGS).click(PERMISSIONS);
        return new PermissionTabAO(() -> $(byClassName("ant-modal-close-x")).shouldBe(visible).click());
    }

    public static By editButtonFor(final Primitive primitive) {
        switch (primitive) {
            case SHORT_DESCRIPTION:
                return byId("short-description-edit-button");
            case FULL_DESCRIPTION:
                return byId("description-edit-button");
            default:
                throw new IllegalArgumentException(format("There is no edit button for %s.", primitive));
        }
    }

    private void showMetadata(final By attribute) {
        hover(SHOW_METADATA);
        ensure(attributesMenu, appears);
        performIf(showAttributes, visible,
                page -> click(attribute),
                page -> resetMouse()
        );
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    public class InstanceManagementSectionAO extends PopupAO<InstanceManagementSectionAO, AccessObject> {
        private final Map<Primitive, SelenideElement> elements = initialiseElements(
                entry(APPLY, $(byXpath("//button[.='APPLY']"))),
                entry(PRICE_TYPE, context().find(byXpath(
                        format("//div/b[text()='%s']/following::div/input", "Allowed price types"))))
        );

        public InstanceManagementSectionAO(AccessObject parentAO) {
            super(parentAO);
        }

        public InstanceManagementSectionAO setPriceType(final String priceType) {
            click(PRICE_TYPE);
            context().find(byClassName("ant-select-dropdown")).find(byText(priceType))
                    .shouldBe(visible)
                    .click();
            return this;
        }

        public InstanceManagementSectionAO clearAllowedPriceTypeField() {
            ensureVisible(PRICE_TYPE);
            SelenideElement type = context().$(byClassName("ant-select-selection__choice__remove"));
            while (type.isDisplayed()) {
                type.click();
                sleep(1, SECONDS);
            }
            if (get(APPLY).isEnabled()) {
                clickApply();
            }
            return this;
        }

        public InstanceManagementSectionAO addAllowedToolInstanceTypesMask(String mask) {
            By optionField = byXpath("//div/b[text()='Allowed tool instance types mask']/following::div/input");
            if (StringUtils.isBlank(mask)) {
                clearByKey(optionField);
            }
            setValue(optionField, mask);
            return this;
        }

        public InstanceManagementSectionAO clickApply() {
            click(APPLY);
            ensureDisable(APPLY);
            return this;
        }

        @Override
        public Map<Primitive, SelenideElement> elements() {
            return elements;
        }
    }
}
