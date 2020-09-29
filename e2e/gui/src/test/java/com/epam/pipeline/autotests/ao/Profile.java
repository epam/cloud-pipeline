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

import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.SelenideElement;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

import com.epam.pipeline.autotests.utils.Utils;
import org.openqa.selenium.By;

import static com.codeborne.selenide.Condition.and;
import static com.codeborne.selenide.Condition.attribute;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selectors.byAttribute;
import static com.codeborne.selenide.Selectors.withText;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.ADD_PARAMETER;
import static com.epam.pipeline.autotests.ao.Primitive.ADVANCED_PANEL;
import static com.epam.pipeline.autotests.ao.Primitive.DELETE;
import static com.epam.pipeline.autotests.ao.Primitive.DISK;
import static com.epam.pipeline.autotests.ao.Primitive.ESTIMATE_PRICE;
import static com.epam.pipeline.autotests.ao.Primitive.EXEC_ENVIRONMENT;
import static com.epam.pipeline.autotests.ao.Primitive.INSTANCE;
import static com.epam.pipeline.autotests.ao.Primitive.INSTANCE_TYPE;
import static com.epam.pipeline.autotests.ao.Primitive.LIMIT_MOUNTS;
import static com.epam.pipeline.autotests.ao.Primitive.NAME;
import static com.epam.pipeline.autotests.ao.Primitive.PARAMETERS;
import static com.epam.pipeline.autotests.ao.Primitive.SAVE;
import static com.epam.pipeline.autotests.ao.Primitive.SET_AS_DEFAULT;
import static com.epam.pipeline.autotests.ao.Primitive.TIMEOUT;
import static com.codeborne.selenide.Condition.visible;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.comboboxOf;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.fieldWithLabel;
import static java.util.concurrent.TimeUnit.SECONDS;

public class Profile implements AccessObject<Profile> {
    private final SelenideElement context;
    private final Map<Primitive, SelenideElement> elements;
    private final PipelineRunFormAO runForm = new PipelineRunFormAO();

    public static By execEnvironmentTab = byId("launch-pipeline-exec-environment-panel");
    public static By advancedTab = byId("launch-pipeline-advanced-panel");
    public static By parametersTab = byId("launch-pipeline-parameters-panel");

    public Profile() {
        this(new PipelineConfigurationTabAO("detached configuration"));
    }

    public Profile(final AccessObject parent) {
        context = parent.context().find(byClassName("ant-form"));
        elements = initialiseElements(
                entry(SET_AS_DEFAULT, context().find(byId("set-pipeline-configuration-as-default-button"))),
                entry(SAVE, context().find(byId("save-pipeline-configuration-button"))),
                entry(NAME, context().find(byId("configuration.name"))),
                entry(ESTIMATE_PRICE, context().find(byText("Estimated price per hour:"))),
                entry(INSTANCE, context().find(byId("launch-pipeline-advanced-panel"))),
                entry(DISK, context().find(byId("exec.disk"))),
                entry(TIMEOUT, context().find(byId("advanced.timeout"))),
                entry(EXEC_ENVIRONMENT, context().find(byId("launch-pipeline-exec-environment-panel"))),
                entry(ADVANCED_PANEL, context().find(byId("launch-pipeline-advanced-panel"))),
                entry(PARAMETERS, context().find(byId("launch-pipeline-parameters-panel"))),
                entry(DELETE, context().find(byId("remove-pipeline-configuration-button"))),
                entry(ADD_PARAMETER, context().find(byId("add-parameter-button"))),
                entry(INSTANCE_TYPE, context().find(comboboxOf(fieldWithLabel("Node type")))),
                entry(LIMIT_MOUNTS, context().find(byClassName("limit-mounts-input__limit-mounts-input")))
        );
    }

    public ParameterFieldAO getParameterByIndex(int parameterIndex) {
        return parameters()
                .skip(parameterIndex)
                .findFirst()
                .orElseThrow(NoSuchElementException::new);
    }

    public Profile addStringParameter(final String name, final String value) {
        clickAddStringParameter().setName(name).setValue(value).close();
        return this;
    }

    public Profile addPathParameter(final String name, final String value) {
        clickAddPathParameter().setName(name).setValue(value).close();
        return this;
    }

    public Profile addCommonParameter(final String name, final String value) {
        clickAddCommonParameter().setName(name).setValue(value).close();
        return this;
    }

    public Profile addInputParameter(final String name, final String value) {
        clickAddInputParameter().setName(name).setValue(value).close();
        return this;
    }

    public Profile addOutputParameter(final String name, final String value) {
        clickAddOutputParameter().setName(name).setValue(value).close();
        return this;
    }

    public Profile removeParameter(final ParameterFieldAO parameter) {
        parameter.click(parameter.removeButton);
        return this;
    }

    public static Stream<ParameterFieldAO> parameters() {
        return ParameterFieldAO.parameters();
    }

    public RunParameterAO clickAddStringParameter() {
        return runForm.clickAddStringParameter();
    }

    public RunParameterAO clickAddInputParameter() {
        return runForm.clickAddInputParameter();
    }

    public RunParameterAO clickAddPathParameter() {
        return runForm.clickAddPathParameter();
    }

    public RunParameterAO clickAddCommonParameter() {
        return runForm.clickAddCommonParameter();
    }

    private RunParameterAO clickAddOutputParameter() {
        return runForm.clickAddOutputParameter();
    }

    @Override
    public SelenideElement context() {
        return context;
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    public Profile waitUntilSaveEnding(final String name) {
        for (int i = 0; i < 2; i++) {
            if ($(withText(String.format("Updating '%s' configuration ...", name))).exists()) {
                sleep(3, SECONDS);
                break;
            }
        }
        return this;
    }

    public static By profileWithName(final String name) {
        return byXpath(String.format(".//*[@role = 'tab' and normalize-space() = '%s']", name));
    }

    public static By activeProfileTab() {
        return byXpath(".//*[@role = 'tab' and contains(@class, 'ant-tabs-tab-active')]");
    }

    public Profile setCommand(final String command) {
        Utils.clearTextField(cmdTemplate());
        Utils.clickAndSendKeysWithSlashes(cmdTemplate(), command);
        return this;
    }

    private SelenideElement cmdTemplate() {
        // TODO: 12/02/18 replace SelenideCollections call with SelenideElement with custom By
        return Selenide.$$(byAttribute("role", "presentation"))
                .findBy(and("first editor line", visible, attribute("style", "padding-right: 0.1px;")));
    }
}
