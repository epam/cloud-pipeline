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
import com.epam.pipeline.autotests.AbstractSeveralPipelineRunningTest;
import com.epam.pipeline.autotests.AbstractSinglePipelineRunningTest;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.PipelineSelectors;
import com.epam.pipeline.autotests.utils.Utils;
import java.util.Map;
import java.util.Optional;
import org.openqa.selenium.By;

import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selectors.*;
import static com.codeborne.selenide.Selenide.*;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.openqa.selenium.By.className;
import static org.openqa.selenium.By.cssSelector;
import static org.openqa.selenium.By.tagName;
import static org.testng.Assert.fail;

public class PipelineRunFormAO implements AccessObject<PipelineRunFormAO> {

    public static final String DEFAULT_DISK = "15";
    public static final String DEFAULT_TYPE = C.DEFAULT_INSTANCE;

    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            entry(LAUNCH, context().find(byClassName("launch-pipeline-form__layout-header")).find(byText("Launch"))),
            entry(PIPELINE, context().find(byClassName("launch-pipeline-form__layout-header")).find(byId("launch-form-pipeline-name"))),
            entry(VERSION, context().find(byClassName("launch-pipeline-form__layout-header")).find(byId("launch-form-pipeline-version"))),
            entry(ESTIMATED_PRICE, $(byClassName("launch-pipeline-form__layout-header")).find(byText("Estimated price per hour:"))),
            entry(INFORMATION_ICON, $(byClassName("launch-pipeline-form__layout-header")).find(byClassName("launch-pipeline-form__hint"))),
            entry(PRICE_TABLE, $(byClassName("ant-popover-placement-bottom"))),
            entry(DISK, context().find(byId("exec.disk"))),
            entry(TIMEOUT, context().find(byId("advanced.timeout"))),
            entry(CONFIGURATION, context().find(byXpath("//*[.//*[contains(text(), 'Configuration name')] and contains(@class, 'ant-select-selection')]"))),
            entry(LAUNCH_CLUSTER, context().find(byText("Configure cluster"))),
            entry(START_IDLE, context().find(byText("Start idle")).closest(".ant-checkbox-wrapper")),
            entry(CLUSTER_DIALOG, context().find(byXpath("//*[@class='ant-modal-title' and //*[contains(text(), 'Configure cluster')]]"))),
            entry(EXEC_ENVIRONMENT, context().find(byId("launch-pipeline-exec-environment-panel"))),
            entry(ADVANCED_PANEL, context().find(byId("launch-pipeline-advanced-panel"))),
            entry(PARAMETERS_PANEL, context().find(byId("launch-pipeline-parameters-panel"))),
            entry(PRICE_TYPE, context().find(byText("Price type")).closest(".launch-pipeline-form__form-item-row").find(byClassName("ant-select"))),
            entry(INSTANCE_TYPE, context().find(byXpath("//*[contains(text(), 'Node type')]")).closest(".ant-row").find(by("role", "combobox"))),
            entry(AUTO_PAUSE, context().find(byText("Auto pause:")).closest(".ant-row-flex").find(cssSelector(".ant-checkbox-wrapper"))),
            entry(DOCKER_IMAGE, context().find(byText("Docker image")).closest(".ant-row").find(tagName("input"))),
            entry(DEFAULT_COMMAND, context().find(byText("Cmd template")).parent().parent().find(byClassName("CodeMirror-line"))),
            entry(SAVE, $(byId("save-pipeline-configuration-button")))
    );
    private final String pipelineName;
    private int parameterIndex = 0;

    /**
     * Use no argument constructor instead.
     */
    @Deprecated
    public PipelineRunFormAO(final String pipelineName) {
        this.pipelineName = pipelineName;
        expandTab(EXEC_ENVIRONMENT);
        expandTab(ADVANCED_PANEL);
        expandTab(PARAMETERS_PANEL);
    }

    public PipelineRunFormAO() {
        this.pipelineName = "";
    }

    public PipelineRunFormAO setDefaultLaunchOptions() {
        return setLaunchOptions(DEFAULT_DISK, DEFAULT_TYPE, null);
    }

    public PipelineRunFormAO setLaunchOptions(String disk, String type, String timeOut) {
        return setDisk(disk)
                .setTypeValue(type)
                .setTimeOut(timeOut);
    }

    public PipelineRunFormAO setTypeValue(String type) {
        $(byText("Node type"))
                .closest(".launch-pipeline-form__form-item")
                .find(className("ant-select-selection"))
                .shouldBe(visible)
                .doubleClick();

        $(byClassName("ant-select-dropdown-menu"))
                .findAll(byClassName("ant-select-dropdown-menu-item"))
                .find(text(type))
                .click();
        return this;
    }

    public PipelineRunFormAO setTimeOut(String timeOut) {
        inputByFieldName("Timeout (min)")
                .setValue(timeOut);
        return this;
    }

    public PipelineRunFormAO setDisk(String disk) {
        $(byId("exec.disk"))
                .shouldBe(enabled)
                .setValue(String.valueOf(disk));
        return this;
    }

    public PipelineRunFormAO setCommand(String command) {
        SelenideElement defaultCommand = get(DEFAULT_COMMAND);
        Utils.clearTextField(defaultCommand);
        Utils.clickAndSendKeysWithSlashes(defaultCommand, command);
        return this;
    }

    public PipelineRunFormAO setPriceType(final String priceType) {
        click(PRICE_TYPE);
        context().find(PipelineSelectors.visible(byClassName("ant-select-dropdown"))).find(byText(priceType))
                .shouldBe(visible)
                .click();
        return this;
    }

    public PipelineRunFormAO enableClusterLaunch() {
        return click(LAUNCH_CLUSTER)
                .ensure(CLUSTER_DIALOG, visible);
    }

    public PipelineRunFormAO clusterSettingsForm(String type){
        if (type.equals("Single node") || type.equals("Cluster") || type.equals("Auto-scaled cluster")) {
            context()
                .find(byXpath(
                    String.format(".//*[contains(@class, 'ant-radio-button-wrapper') and .//text()='%s']", type)))
                .click();
        } else {
            fail("Wrong type of cluster was selected");
        }
        return this;
    }

    public PipelineRunFormAO addOutputParameter(String name, String value) {
        resetMouse();
        $(byId("add-parameter-dropdown-button")).shouldBe(visible).hover().click();
        $(byText("Output path parameter")).shouldBe(visible).click();

        int paramIndex = $$(byClassName("launch-pipeline-form__parameter-name")).size();
        final ParameterFieldAO parameter = ParameterFieldAO.parameterByOrder(paramIndex);
        parameter
                .setValue(parameter.nameInput, name)
                .setValue(parameter.valueInput, value);
        return this;
    }

    public void launchAndWaitUntilFinished(AbstractSinglePipelineRunningTest test) {
        launch(test)
                .showLogForce(test.getRunId())
                .waitForCompletion();
    }

    public PipelineRunFormAO ensureLaunchButtonIsVisible() {
        $(button("Launch")).should(exist);
        return this;
    }

    /**
     * Launch pipeline and set runId to the given test.
     */
    public RunsMenuAO launch(final AbstractSinglePipelineRunningTest test) {
        final String launchItemName = getLaunchItemName();
        launch();
        test.setRunId(Utils.getPipelineRunId(launchItemName));
        return new RunsMenuAO();
    }

    /**
     * Launch pipeline and add runId to the given test.
     */
    public RunsMenuAO launch(final AbstractSeveralPipelineRunningTest test) {
        final String launchItemName = getLaunchItemName();
        launch();
        test.addRunId(Utils.getPipelineRunId(launchItemName));
        return new RunsMenuAO();
    }

    /**
     * Launch tool and and set runId to the given test.
     */
    public RunsMenuAO launchTool(final AbstractSinglePipelineRunningTest test, final String toolSelfName) {
        launch();
        test.setRunId(Utils.getToolRunId(toolSelfName));
        return new RunsMenuAO();
    }

    public PipelineRunFormAO waitUntilLaunchButtonAppear() {
        for (int i = 0; i < 3; i++) {
            refresh();
            if ($(button("Launch")).isEnabled()) {
                break;
            }
            sleep(1, SECONDS);
        }
        return this;
    }

    /**
     * Launch tool and add runId to the given test.
     */
    public RunsMenuAO launchTool(final AbstractSeveralPipelineRunningTest test, final String toolSelfName) {
        launch();
        test.addRunId(Utils.getToolRunId(toolSelfName));
        return new RunsMenuAO();
    }

    private String getLaunchItemName() {
        return Optional.ofNullable(pipelineName)
                .filter(name -> !name.equals(""))
                .orElseGet(() -> context().find(launchItemName()).getText());
    }

    private By launchItemName() {
        return PipelineSelectors.Combiners.confine(
                byId("launch-form-pipeline-name"),
                header(),
                "Not empty launch item name"
        );
    }

    private By header() {
        return byClassName("launch-pipeline-form__layout-header");
    }

    private void launch() {
        $$(byClassName("ant-btn")).filterBy(text("Launch")).first().shouldBe(visible).click();
        $$(byClassName("ant-modal-body")).findBy(text("Launch")).find(button("Launch")).shouldBe(visible).click();
    }

    public PipelineRunFormAO validateThereIsParameterOfType(String name, String value, ParameterType type, boolean required) {
        final String parameterNameClass = "launch-pipeline-form__parameter-name";
        final SelenideElement nameElement = $(byXpath(String.format(
            ".//input[contains(concat(' ', @class, ' '), ' %s ') and @value = '%s']",
            parameterNameClass, name
        )));
        final String parameterNameId = nameElement.should(exist, required ? disabled : enabled).getAttribute("id");
        final String parameterValueId = parameterNameId.replace("name", "value");
        final SelenideElement valueElement = $(byId(parameterValueId)).should(exist, have(attribute("value", value)));

        if (type == ParameterType.STRING) {
            valueElement.parent().shouldHave(cssClass("ant-form-item-control"));
        } else {
            valueElement.closest("span").find("i").shouldHave(cssClass(type.iconClass));
        }

        return this;
    }

    public void validateException(String exception) {
        $(".ant-alert-message").shouldHave(text(exception));
    }

    private SelenideElement inputByFieldName(String fieldName) {
        return $(byText(fieldName))
                .parent()
                .parent()
                .find(tagName("input"));
    }

    public RunParameterAO clickAddOutputParameter() {
        return clickAddParameter("Output path parameter");
    }

    public RunParameterAO clickAddInputParameter() {
        return clickAddParameter("Input path parameter");
    }

    public RunParameterAO clickAddPathParameter() {
        return clickAddParameter("Path parameter");
    }

    public RunParameterAO clickAddCommonParameter() {
        return clickAddParameter("Common path parameter");
    }

    public RunParameterAO clickAddStringParameter() {
        return clickAddParameter("String parameter");
    }

    private RunParameterAO clickAddParameter(String parameterType) {
        resetMouse();
        $(byId("add-parameter-dropdown-button")).shouldBe(visible).hover();
        $(byText(parameterType)).shouldBe(visible).click();

        parameterIndex += 1;
        return new RunParameterAO(this, parameterIndex);
    }

    public PipelineRunFormAO chooseConfiguration(final String profileName) {
        click(CONFIGURATION);
        $$(className("ant-select-dropdown")).findBy(visible)
                                            .find(withText(profileName))
                                            .shouldBe(visible)
                                            .click();
        return this;
    }

    public PipelineRunFormAO setWorkingNodesCount(final String nodesCount) {
        return setValue(getElement(byXpath("(.//*[@class = 'ant-input-number-input'])[1]")), nodesCount);
    }

    public PipelineRunFormAO setDefaultChildNodes(final String nodesCount) {
        $(byText("Setup default child nodes count")).click();
        return setValue(getElement(byXpath("(.//*[@class = 'ant-input-number-input'])[last()]")), nodesCount);
    }

    public PipelineRunFormAO resetClusterChildNodes () {
        $(byXpath("//*[contains(text(), 'Reset')]")).click();
        return this;
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

}
