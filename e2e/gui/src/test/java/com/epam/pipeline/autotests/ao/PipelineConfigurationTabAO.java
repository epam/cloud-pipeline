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

import com.codeborne.selenide.SelenideElement;
import java.util.Map;
import java.util.function.Consumer;
import static com.codeborne.selenide.Condition.appear;
import static com.codeborne.selenide.Condition.disappear;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.ADD_CONFIGURATION;
import static com.epam.pipeline.autotests.ao.Primitive.CANCEL;
import static com.epam.pipeline.autotests.ao.Primitive.CLOSE;
import static com.epam.pipeline.autotests.ao.Primitive.CONFIGURATION;
import static com.epam.pipeline.autotests.ao.Primitive.CONFIGURATION_TAB;
import static com.epam.pipeline.autotests.ao.Primitive.CREATE;
import static com.epam.pipeline.autotests.ao.Primitive.DELETE;
import static com.epam.pipeline.autotests.ao.Primitive.DESCRIPTION;
import static com.epam.pipeline.autotests.ao.Primitive.INSTANCE;
import static com.epam.pipeline.autotests.ao.Primitive.NAME;
import static com.epam.pipeline.autotests.ao.Primitive.PARAMETERS;
import static com.epam.pipeline.autotests.ao.Primitive.TEMPLATE;
import static com.epam.pipeline.autotests.ao.Profile.profileWithName;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.buttonByIconClass;
import static org.openqa.selenium.By.className;

public class PipelineConfigurationTabAO extends AbstractPipelineTabAO<PipelineConfigurationTabAO> {
    private final SelenideElement context = $(byClassName("pipeline-details__full-height-container"));

    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            super.elements(),
            entry(CONFIGURATION, context().find(byId("edit-pipeline-configuration-panel"))),
            entry(ADD_CONFIGURATION, context().find(buttonByIconClass("anticon-plus")))
    );

    public PipelineConfigurationTabAO(final String pipelineName) {
        super(pipelineName);
    }

    @Override
    protected PipelineConfigurationTabAO open() {
        changeTabTo(CONFIGURATION_TAB);
        return this;
    }

    public PipelineConfigurationTabAO createConfiguration(final String profileName) {
        return createConfiguration(popup -> popup.setValue(NAME, profileName).ok());
    }

    public PipelineConfigurationTabAO createConfiguration(final Consumer<PipelineConfigurationProfilePopup> action) {
        click(ADD_CONFIGURATION);
        final PipelineConfigurationProfilePopup popup = new PipelineConfigurationProfilePopup(this);
        popup.context().should(appear);
        action.accept(popup);
        popup.context().should(disappear);
        return this;
    }

    public PipelineConfigurationTabAO editConfiguration(final String profileName, final Consumer<Profile> action) {
        click(profileWithName(profileName));
        final Profile profile = new Profile();
        profile.expandTab(INSTANCE);
        profile.expandTab(PARAMETERS);
        profile.context().shouldBe(visible);
        action.accept(profile);
        profile.context().shouldBe(visible);
        return this;
    }

    public PipelineConfigurationTabAO deleteConfiguration(final String profileName, final Consumer<ConfirmationPopupAO<PipelineConfigurationTabAO>> action) {
        return editConfiguration(profileName, profile -> {
            profile.click(DELETE);
            final ConfirmationPopupAO<PipelineConfigurationTabAO> popup = new ConfirmationPopupAO<>(this);
            popup.context().should(appear);
            action.accept(popup);
            popup.context().should(disappear);
        });
    }

    @Override
    public SelenideElement context() {
        return context;
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    public class PipelineConfigurationProfilePopup extends PopupAO<PipelineConfigurationProfilePopup, PipelineConfigurationTabAO> {
        private final SelenideElement context = $(byXpath("//*[contains(@role, 'dialog') and .//*[contains(@class, 'ant-modal-title') and text() = 'Create configuration']]"));
        private final Map<Primitive, SelenideElement> elements = initialiseElements(
                entry(CLOSE, context().find(byClassName("ant-modal-close"))),
                entry(CANCEL, context().find(button("CANCEL"))),
                entry(CREATE, context().find(button("CREATE"))),
                entry(NAME, context().find(byId("name"))),
                entry(DESCRIPTION, context().find(byId("description"))),
                entry(TEMPLATE, context().find(byClassName("ant-select-selection__rendered")))
        );

        public PipelineConfigurationProfilePopup(final PipelineConfigurationTabAO parentAO) {
            super(parentAO);
        }

        @Override
        public PipelineConfigurationTabAO cancel() {
            return click(CANCEL).parent();
        }

        @Override
        public void closeAll() {
            click(CLOSE);
        }

        @Override
        public PipelineConfigurationTabAO ok() {
            return click(CREATE).parent();
        }

        @Override
        public Map<Primitive, SelenideElement> elements() {
            return elements;
        }

        @Override
        public SelenideElement context() {
            return context;
        }
    }
}
