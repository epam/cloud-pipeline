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

import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.AbstractSeveralPipelineRunningTest;
import com.epam.pipeline.autotests.utils.PipelineSelectors;
import com.epam.pipeline.autotests.utils.SelenideElements;
import com.epam.pipeline.autotests.utils.Utils;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.openqa.selenium.By;

import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.value;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byCssSelector;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.ADD;
import static com.epam.pipeline.autotests.ao.Primitive.EDIT;
import static com.epam.pipeline.autotests.ao.Primitive.IMAGE;
import static com.epam.pipeline.autotests.ao.Primitive.INSTANCE_TYPE;
import static com.epam.pipeline.autotests.ao.Primitive.NAME;
import static com.epam.pipeline.autotests.ao.Primitive.PARAMETER_NAME;
import static com.epam.pipeline.autotests.ao.Primitive.PIPELINE;
import static com.epam.pipeline.autotests.ao.Primitive.PRICE_TYPE;
import static com.epam.pipeline.autotests.ao.Primitive.RUN;
import static com.epam.pipeline.autotests.ao.Primitive.SAVE;
import static com.epam.pipeline.autotests.ao.Primitive.START_IDLE;
import static com.epam.pipeline.autotests.ao.Primitive.TEMPLATE;
import static com.epam.pipeline.autotests.ao.Primitive.ADD_PARAMETER;
import static com.epam.pipeline.autotests.ao.Profile.profileWithName;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.comboboxOf;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.fieldWithLabel;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.inputOf;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.settingsButton;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static java.util.Objects.isNull;
import static java.util.concurrent.TimeUnit.SECONDS;

public class Configuration implements AccessObject<Configuration> {

    public static final int FIRST_PARAMETER_INDEX = 1;

    private final Map<Primitive, SelenideElement> elements;
    private final Profile profile = new Profile(this);
    private List<String> parameters;

    public static By title() {
        return byClassName("browser__item-header");
    }

    public static By name() {
        return byId("configuration.name");
    }

    public static By add() {
        return byId("add-configuration-button");
    }

    public static By run() {
        return byId("run-configuration-button");
    }

    public static By save() {
        return byId("save-pipeline-configuration-button");
    }

    public static By pipeline() {
        return inputOf(fieldWithLabel("Pipeline"));
    }

    public static By image() {
        return inputOf(fieldWithLabel("Docker image"));
    }

    public static By instanceType() {
        return comboboxOf(fieldWithLabel("Node type"));
    }

    public static By cpu() {
        return byId("exec.cpu");
    }

    public static By ram() {
        return byId("exec.ram");
    }

    public static By disk() {
        return byId("exec.disk");
    }

    public static By rootEntityType() {
        return PipelineSelectors.Combiners.confine(
                byClassName("ant-select-selection"),
                byClassName("launch-pipeline-form__root-entity-type-container"),
                "root entity type"
        );
    }

    public static By addParameter() {
        return byId("add-parameter-button");
    }

    public static By priceType() {
        return fieldWithLabel("Price type");
    }

    public static By timeout() {
        return fieldWithLabel("Timeout (min)");
    }

    public static By startIdle() {
        return fieldWithLabel("Cmd template");
    }

    public static By template() {
        return byClassName("launch-pipeline-form__code-editor");
    }

    public static By parameterName() {
        return byClassName("launch-pipeline-form__parameter-name");
    }

    public Configuration() {
        final SelenideElement actionsButtons = context().find(byClassName("detached-configuration__action-buttons"));
        this.elements = initialiseElements(profile.elements(),
                entry(NAME, context().find(byId("configuration.name"))),
                entry(PIPELINE, context().find(inputOf(fieldWithLabel("Pipeline")))),
                entry(IMAGE, context().find(inputOf(fieldWithLabel("Docker image")))),
                entry(INSTANCE_TYPE, context().find(comboboxOf(fieldWithLabel("Node type")))), //shall be changed after UI changes on CONFIGURATION and LAUNCH forms
                entry(PRICE_TYPE, context().find(comboboxOf(fieldWithLabel("Price type")))),
                entry(START_IDLE, context().find(inputOf(fieldWithLabel("Cmd template")))),
                entry(TEMPLATE, context().find(byClassName("launch-pipeline-form__code-editor"))),
                entry(EDIT, actionsButtons.find(settingsButton())),
                entry(SAVE, context().find(byId("save-pipeline-configuration-button"))),
                entry(RUN, context().find(byId("run-configuration-button"))),
                entry(ADD, context().find(byId("add-configuration-button"))),
                entry(ADD_PARAMETER, context().find(byId("add-parameter-button")))
        );
    }

    public Configuration edit(final Consumer<DetachedConfigurationEditionPopup> edition) {
        click(EDIT);
        edition.accept(new DetachedConfigurationEditionPopup(this));
        return this;
    }

    /**
     * Launch cluster and add runId to the given test.
     */
    public RunsMenuAO runCluster(AbstractSeveralPipelineRunningTest test, String pipelineName) {
        click(RUN);
        $(byText("Run cluster")).shouldBe(visible).click();
        $(PipelineSelectors.visible(byClassName("ant-modal-body"))).find(button("OK")).click();
        test.addRunId(Utils.getPipelineRunId(pipelineName));
        return new RunsMenuAO();
    }

    /**
     * Launch pipeline and add runId to the given test.
     */
    public RunsMenuAO runSelected(AbstractSeveralPipelineRunningTest test, String pipelineName) {
        click(RUN);
        $(byText("Run selected")).shouldBe(visible).click();
        $(byClassName("ant-modal-body")).find(button("OK")).click();
        test.addRunId(Utils.getPipelineRunId(pipelineName));
        return new RunsMenuAO();
    }

    public PipelinesLibraryAO delete() {
        edit(DetachedConfigurationEditionPopup::delete);
        return new PipelinesLibraryAO();
    }

    public Configuration selectPipeline(final Consumer<PipelineSelection> pipeline) {
        click(PIPELINE);
        pipeline.accept(new PipelineSelection(this));
        return this;
    }

    public Configuration selectPipeline(final String pipeline, final String pipelineConfiguration) {
        return selectPipeline(selection ->
                selection.selectPipeline(pipeline)
                        .sleep(2, SECONDS)
                        .selectFirstVersion()
                        .selectConfiguration(pipelineConfiguration)
                        .ok()
                        .also(confirmConfigurationChange())
        );
    }

    public Configuration selectPipeline(final String pipeline) {
        return selectPipeline(selection ->
                selection.selectPipeline(pipeline)
                        .sleep(2, SECONDS)
                        .selectFirstVersion()
                        .ok()
                        .also(confirmConfigurationChange())
        );
    }

    public Configuration selectDockerImage(final Consumer<DockerImageSelection> dockerImage) {
        click(IMAGE);
        dockerImage.accept(new DockerImageSelection(this));
        return this;
    }

    public Configuration addProfile(final String profileName) {
        return addProfile(profile -> profile.setName(profileName)
                .setTemplate(this.profile.elements().get(NAME).name()).ok());
    }

    public Configuration addProfile(final Consumer<DetachedConfigurationProfilePopup> profile) {
        click(ADD);
        profile.accept(new DetachedConfigurationProfilePopup(this));
        return this;
    }

    public Configuration selectProfile(final String profile) {
        context().find(profileWithName(profile)).shouldBe(visible).click();
        return this;
    }

    public Configuration ensureThereIsNoParameters() {
        $(byCssSelector(".ant-select-lg.ant-select-combobox")).shouldNot(exist);
        return this;
    }

    public Configuration validateParameters(final String... parameters) {
        final ElementsCollection actualParameters = SelenideElements.of(parameterName());
        IntStream.range(0, parameters.length)
                .forEach(i -> actualParameters.get(i).shouldHave(value(parameters[i])));
        return this;
    }

    public DetachedConfigurationParameterAO getParameterByIndex(int parameterIndex) {
        return new DetachedConfigurationParameterAO(this, parameterIndex);
    }

    public Configuration addStringParameter(final String name, final String value) {
        profile.clickAddStringParameter()
                .setName(name)
                .also(parameter -> parameter.get(PARAMETER_NAME)
                        .closest(".ant-row")
                        .closest(".ant-row")
                        .find(byClassName("ant-select-search__field"))
                        .setValue(value)
                );
        return this;
    }

    public Configuration setParameter(final String name, final String value) {
        final ParameterFieldAO parameter = ParameterFieldAO.parameterByName(name);
        getParameterByIndex(parameter.index()).typeValue(value);
        return this;
    }

    public Configuration addToParameter(final String name, final String value) {
        final ParameterFieldAO parameter = ParameterFieldAO.parameterByName(name);
        getParameterByIndex(parameter.index()).addToValue(value);
        return this;
    }

    public Configuration resetChanges() {
        click(SAVE);
        sleep(2, SECONDS);
        $(button("Yes")).shouldBe(visible).click();
        return this;
    }

    private List<String> getParameters() {
        if (isNull(parameters)) {
            parameters = SelenideElements.of(parameterName())
                    .stream()
                    .map(SelenideElement::getValue)
                    .collect(Collectors.toList());
        }
        return parameters;
    }

    @Override
    public SelenideElement context() {
        return $(byId("pipelines-library-content"));
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    public static Runnable confirmConfigurationChange() {
        return () -> new ConfirmationPopupAO<>(new Object()).ensureTitleIs("Are you sure you want to change configuration?").ok();
    }

}
