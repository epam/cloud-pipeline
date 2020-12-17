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

import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.AbstractSeveralPipelineRunningTest;
import com.epam.pipeline.autotests.AbstractSinglePipelineRunningTest;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.PipelineSelectors;
import com.epam.pipeline.autotests.utils.SelenideElements;
import com.epam.pipeline.autotests.utils.Utils;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import org.openqa.selenium.By;

import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selectors.*;
import static com.codeborne.selenide.Selenide.*;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.openqa.selenium.By.className;
import static org.openqa.selenium.By.cssSelector;
import static org.openqa.selenium.By.tagName;
import static org.testng.Assert.assertEquals;
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
            entry(EXEC_ENVIRONMENT, context().find(byId("launch-pipeline-exec-environment-panel"))),
            entry(ADVANCED_PANEL, context().find(byId("launch-pipeline-advanced-panel"))),
            entry(PARAMETERS_PANEL, context().find(byId("launch-pipeline-parameters-panel"))),
            entry(PRICE_TYPE, context().find(byText("Price type")).closest(".launch-pipeline-form__form-item-row").find(byClassName("ant-select"))),
            entry(INSTANCE_TYPE, context().find(byXpath("//*[contains(text(), 'Node type')]")).closest(".ant-row").find(by("role", "combobox"))),
            entry(AUTO_PAUSE, context().find(byText("Auto pause:")).closest(".ant-row-flex").find(cssSelector(".ant-checkbox-wrapper"))),
            entry(DOCKER_IMAGE, context().find(byText("Docker image")).closest(".ant-row").find(tagName("input"))),
            entry(DEFAULT_COMMAND, context().find(byText("Cmd template")).parent().parent().find(byClassName("CodeMirror-line"))),
            entry(SAVE, $(byId("save-pipeline-configuration-button"))),
            entry(ADD_SYSTEM_PARAMETER, $(byId("add-system-parameter-button"))),
            entry(RUN_CAPABILITIES, context().find(byXpath("//*[contains(text(), 'Run capabilities')]")).closest(".ant-row").find(by("role", "combobox"))),
            entry(LIMIT_MOUNTS, context().find(byClassName("limit-mounts-input__limit-mounts-input"))),
            entry(FRIENDLY_URL, context().find(byId("advanced.prettyUrl")))
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

    public PipelineRunFormAO checkWarningMessage(String message, boolean isVisible) {
        sleep(5, SECONDS);
        screenshot("check" + Utils.randomSuffix());
        assertEquals(context().findAll(byClassName("ant-alert-warning"))
                .stream()
                .map(SelenideElement::getText)
                .filter(e -> e.contains(message))
                .count() == 1, isVisible);
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

    public PipelineRunFormAO ensurePriceTypeList(String... priceTypes) {
        click(PRICE_TYPE);
        context().find(byClassName("ant-select-dropdown")).shouldBe(visible);
        ElementsCollection list = context().find(byClassName("ant-select-dropdown-menu")).$$("li");
        assertEquals(priceTypes.length, list.size(), format("Expected: %d Actual: %d", priceTypes.length, list.size()));
        Arrays.stream(priceTypes).forEach(this::checkPriceTypeInList);
        return this;
    }

    private void checkPriceTypeInList(String priceType) {
        context().find(PipelineSelectors.visible(byClassName("ant-select-dropdown")))
                .find(byText(priceType))
                .shouldBe(visible);
    }

    public ConfigureClusterPopupAO enableClusterLaunch() {
        click(LAUNCH_CLUSTER);
        return new ConfigureClusterPopupAO(this);
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

    public PipelineRunFormAO launchWithError(final AbstractSinglePipelineRunningTest test, String errorMessage) {
        launch();
        messageShouldAppear(errorMessage);
        return this;
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
        $(button("Launch")).waitUntil(enabled, C.DEFAULT_TIMEOUT * 2);
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

    public PipelineRunFormAO checkLaunchWarningMessage(String message, boolean isVisible) {
        try {
            $$(byClassName("ant-btn")).filterBy(text("Launch")).first().shouldBe(visible).click();
            $$(byClassName("ant-modal-body"))
                    .findBy(text("Launch"))
                    .find(byClassName("ob-estimated-price-info__info"))
                    .shouldBe(visible);
            assertEquals(context().$(byClassName("ant-modal-body")).findAll(byClassName("ant-alert-warning"))
                    .stream()
                    .map(SelenideElement::getText)
                    .filter(e -> e.contains(message))
                    .count() == 1, isVisible);
        } finally {
            $$(byClassName("ant-modal-body")).findBy(text("Cancel"))
                    .find(button("Cancel"))
                    .shouldBe(enabled)
                    .click();
            return this;
        }
    }

    public PipelineRunFormAO checkLaunchItemName(String name) {
        context().find(launchItemName()).shouldHave(text(name));
        return this;
    }

    private By header() {
        return byClassName("launch-pipeline-form__layout-header");
    }

    private void launch() {
        $$(byClassName("ant-btn")).filterBy(text("Launch")).first().shouldBe(visible).click();
        $$(byClassName("ant-modal-body")).findBy(text("Launch")).find(byClassName("ob-estimated-price-info__info")).shouldBe(visible);
        $$(byClassName("ant-modal-body")).findBy(text("Launch")).find(button("Launch")).shouldBe(enabled).click();
    }

    public PipelineRunFormAO validateThereIsParameterOfType(String name, String value, ParameterType type, boolean required) {
        final String parameterNameClass = "launch-pipeline-form__parameter-name";
        final SelenideElement nameElement = $(byXpath(format(
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

    public SystemParameterPopupAO clickAddSystemParameter() {
        click(ADD_SYSTEM_PARAMETER);
        return new SystemParameterPopupAO(this);
    }

    public PipelineRunFormAO chooseConfiguration(final String profileName) {
        click(CONFIGURATION);
        $$(className("ant-select-dropdown")).findBy(visible)
                                            .find(withText(profileName))
                                            .shouldBe(visible)
                                            .click();
        return this;
    }

    public PipelineRunFormAO checkConfigureClusterLabel(String label) {
        context().find(byXpath(".//div[@class='ant-row-flex ant-row-flex-end']/a"))
                .shouldBe(visible).shouldHave(text(label));
        return this;
    }

    public PipelineRunFormAO inputSystemParameterValue(String parameter, String value) {
        String inputFieldID = $(byXpath(format("//input[@value='%s']", parameter))).attr("id")
                .replace(".name", ".value");
        $(byXpath(format("//input[@id='%s']", inputFieldID))).shouldBe(enabled).setValue(value);
        return this;
    }

    public String getCPU() {
        String cpu = $(byXpath(".//b[.='CPU']")).parent().getText();
        return cpu.substring(0, cpu.indexOf(" "));
    }

    public SelectLimitMountsPopupAO selectDataStoragesToLimitMounts() {
        click(LIMIT_MOUNTS);
        return new SelectLimitMountsPopupAO(this).sleep(2, SECONDS);
    }

    public int minNodeTypeRAM() {
        sleep(1, SECONDS);
        get(INSTANCE_TYPE).shouldBe(visible).click();
        return SelenideElements.of(byClassName("ant-select-dropdown-menu-item")).texts()
                .stream()
                .map(e -> e.substring(e.indexOf("RAM: ")))
                .map(e -> e.replaceAll("[^0-9 ]", ""))
                .map(e -> e.split(" ")[1])
                .mapToInt(Integer::parseInt)
                .min().getAsInt();
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    public static class SystemParameterPopupAO  extends PopupAO<SystemParameterPopupAO, PipelineRunFormAO> {

        private final Map<Primitive, SelenideElement> elements = initialiseElements(
                entry(PARAMETER_NAME, context().$(byXpath("//*[@placeholder='Parameter']"))),
                entry(ADD, context().find(byId("system-parameters-browser-ok-button"))),
                entry(CANCEL, context().find(byClassName("system-parameters-browser-cancel-button")))
        );

        public SystemParameterPopupAO(PipelineRunFormAO parentAO) {
            super(parentAO);
        }

        @Override
        public Map<Primitive, SelenideElement> elements() {
            return elements;
        }
        @Override
        public PipelineRunFormAO cancel() {
            return click(CANCEL).parent();
        }

        @Override
        public PipelineRunFormAO ok() {
            return click(ADD).parent();
        }

        private SystemParameterPopupAO selectSystemParameter(String parameter) {
            clear(PARAMETER_NAME);
            setValue(PARAMETER_NAME, parameter);
            $(byText(parameter)).shouldBe(visible).click();
            return this;
        }

        public SystemParameterPopupAO selectSystemParameters(String ... parameters) {
            Arrays.stream(parameters).forEach(this::selectSystemParameter);
            return this;
        }
    }

    public static class ConfigureClusterPopupAO  extends PopupAO<ConfigureClusterPopupAO, PipelineRunFormAO> {

        private final Map<Primitive, SelenideElement> elements = initialiseElements(
                entry(WORKERS_PRICE_TYPE, context().find(byText("Workers price type:")).parent().find(byClassName("ant-select-selection--single"))),
                entry(WORKING_NODES, context().find(byXpath("(.//*[@class = 'ant-input-number-input'])[1]"))),
                entry(DEFAULT_CHILD_NODES, context().find(byXpath("(.//*[@class = 'ant-input-number-input'])[last()]"))),
                entry(RESET, context().$(byXpath("//*[contains(text(), 'Reset')]")))
        );

        public ConfigureClusterPopupAO(PipelineRunFormAO parentAO) {
            super(parentAO);
        }

        @Override
        public Map<Primitive, SelenideElement> elements() {
            return elements;
        }

        public ConfigureClusterPopupAO clusterSettingsForm(String type){
            if (type.equals("Single node") || type.equals("Cluster") || type.equals("Auto-scaled cluster")) {
                context()
                        .find(byXpath(
                                format(".//*[contains(@class, 'ant-radio-button-wrapper') and .//text()='%s']", type)))
                        .click();
            } else {
                fail("Wrong type of cluster was selected");
            }
            return this;
        }

        public ConfigureClusterPopupAO setWorkingNodesCount(final String nodesCount) {
            return setValue(WORKING_NODES, nodesCount);
        }

        public ConfigureClusterPopupAO setDefaultChildNodes(final String nodesCount) {
            $(byText("Setup default child nodes count")).click();
            return setValue(DEFAULT_CHILD_NODES, nodesCount);
        }

        public ConfigureClusterPopupAO resetClusterChildNodes () {
            return click(RESET);
        }

        public ConfigureClusterPopupAO setWorkersPriceType(final String priceType) {
            click(WORKERS_PRICE_TYPE);
            context().find(PipelineSelectors.visible(byClassName("ant-select-dropdown"))).find(byText(priceType))
                    .shouldBe(visible)
                    .click();
            return this;
        }

        public ConfigureClusterPopupAO enableHybridClusterSelect () {
            $(byXpath(".//span[.='Enable Hybrid cluster']/preceding-sibling::span")).click();
            return this;
        }

        public ConfigureClusterPopupAO clusterEnableCheckboxSelect(String checkBox){
            if (checkBox.equals("Enable GridEngine")
                    || checkBox.equals("Enable Apache Spark")
                    || checkBox.equals("Enable Slurm")
                    || checkBox.equals("Enable Kubernetes")) {
                context()
                        .find(byXpath(
                                format(".//span[.='%s']/preceding-sibling::span[@class='ant-checkbox']", checkBox)))
                        .click();
            } else {
                fail("Wrong checkbox name was selected");
            }
            return this;
        }
    }
    }
