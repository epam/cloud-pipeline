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

import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.AbstractSeveralPipelineRunningTest;
import com.epam.pipeline.autotests.AbstractSinglePipelineRunningTest;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.PipelineSelectors;
import com.epam.pipeline.autotests.utils.SelenideElements;
import com.epam.pipeline.autotests.utils.Utils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchWindowException;

import static com.codeborne.selenide.CollectionCondition.size;
import static com.codeborne.selenide.CollectionCondition.sizeGreaterThan;
import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selectors.*;
import static com.codeborne.selenide.Selenide.*;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.utils.C.DEFAULT_TIMEOUT;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.visible;
import static java.lang.String.format;
import static java.time.Duration.ofMillis;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.openqa.selenium.By.className;
import static org.openqa.selenium.By.cssSelector;
import static org.openqa.selenium.By.tagName;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class PipelineRunFormAO implements AccessObject<PipelineRunFormAO> {

    /**
     * A run with this parameter set to true gets a new node: the API never gives it a free node of an earlier run
     */
    public static final String CREATE_NEW_NODE = "CP_CREATE_NEW_NODE";

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
            entry(CONFIGURATION, context().find(byXpath(".//*[.//*[contains(text(), 'Configuration name')] and contains(@class, 'ant-select-selection')]"))),
            entry(LAUNCH_CLUSTER, context().find(byText("Configure cluster"))),
            entry(START_IDLE, context().find(byText("Start idle")).closest(".ant-checkbox-wrapper")),
            entry(EXEC_ENVIRONMENT, context().find(byId("launch-pipeline-exec-environment-panel"))),
            entry(ADVANCED_PANEL, context().find(byId("launch-pipeline-advanced-panel"))),
            entry(PARAMETERS_PANEL, context().find(byId("launch-pipeline-parameters-panel"))),
            entry(PRICE_TYPE, context().find(byText("Price type")).closest(".launch-pipeline-form__form-item-row").find(byClassName("ant-select"))),
            entry(INSTANCE_TYPE, context().find(byXpath(".//*[contains(text(), 'Node type')]")).closest(".ant-row").find(by("role", "combobox"))),
            entry(AUTO_PAUSE, context().find(byText("Auto pause:")).closest(".ant-row-flex").find(cssSelector(".ant-checkbox-wrapper"))),
            entry(DOCKER_IMAGE, context().find(byText("Docker image")).closest(".ant-row").find(tagName("input"))),
            entry(DEFAULT_COMMAND, context().find(byText("Cmd template")).parent().parent().find(byClassName("CodeMirror-line"))),
            entry(SAVE, $(byId("save-pipeline-configuration-button"))),
            entry(ADD_SYSTEM_PARAMETER, $(byId("add-system-parameter-button"))),
            // the run capabilities input opens a menu of an rc-dropdown (launch/form/utilities/run-capabilities.js)
            entry(RUN_CAPABILITIES, context().find(byXpath(".//*[contains(text(), 'Run capabilities')]")).closest(".ant-row").find(className("cp-run-capabilities-input"))),
            entry(LIMIT_MOUNTS, context().find(byClassName("limit-mounts-input__limit-mounts-input"))),
            entry(FRIENDLY_URL, context().find(byId("advanced.friendly_url"))),
            entry(DO_NOT_MOUNT_STORAGES, $(byXpath(".//span[.='Do not mount storages']/preceding-sibling::span"))),
            entry(LAUNCH_COMMANDS, context().find(byId("launch-command-button")))
    );
    private final String pipelineName;
    private int parameterIndex = 0;

    /**
     * Use no argument constructor instead.
     */
    @Deprecated
    public PipelineRunFormAO(final String pipelineName) {
        this.pipelineName = pipelineName;
        ensure(LAUNCH_COMMANDS, appears);
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
	return sleep(7, SECONDS)
                .setTypeValue(type)
                .sleep(2, SECONDS)
                .setDisk(disk)
                .setTimeOut(timeOut);
    }

    public PipelineRunFormAO setTypeValue(String type) {
        $(byText("Node type"))
                .closest(".launch-pipeline-form__form-item")
                .find(className("ant-select-selection"))
                .shouldBe(visible)
                .click();
        $(byClassName("ant-select-dropdown-hidden")).shouldNotBe(exist);
        $(byClassName("ant-select-dropdown-menu"))
                .shouldBe(enabled)
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
        assertEquals(context().findAll(byClassName("ant-alert-message"))
                .stream()
                .map(SelenideElement::getText)
                .filter(e -> e.contains(message))
                .count() == 1, isVisible, format("Message '%s' isn't [%s] visible", message, isVisible));
        return this;
    }

    public PipelineRunFormAO setDisk(String disk) {
        return setValue(DISK, disk);
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

    public PipelineRunFormAO selectRunCapability(final String optionQualifier) {
        get(RUN_CAPABILITIES).shouldBe(visible).click();
        $(visible(byClassName("rc-dropdown"))).find(byText(optionQualifier))
                .shouldBe(visible).click();
        $(byText("Run capabilities")).click();
        return this;
    }

    /**
     * Selects a run capability as {@link AccessObject#selectValue(Primitive, By)} selects an option of a combobox:
     * the run capabilities are a menu of an rc-dropdown, not of an ant-select-dropdown.
     */
    @Override
    public PipelineRunFormAO selectValue(final Primitive combobox, final By optionQualifier) {
        if (combobox != RUN_CAPABILITIES) {
            return AccessObject.super.selectValue(combobox, optionQualifier);
        }
        get(RUN_CAPABILITIES).shouldBe(visible).click();
        $(visible(byClassName("rc-dropdown"))).find(optionQualifier).shouldBe(visible).click();
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

    // a selected capability is a tag with the capability name, the title of the name is the description
    // (launch/form/utilities/capability.js)
    public PipelineRunFormAO checkTooltipText(String capability, String tooltip) {
        $(byXpath(format("(//span[text()='%s'])", capability)))
                .shouldHave(attribute("title", tooltip));
        return this;
    }

    public ConfigureClusterPopupAO enableClusterLaunch() {
        click(LAUNCH_CLUSTER);
        return new ConfigureClusterPopupAO(this);
    }

    public PipelineRunFormAO addOutputParameter(String name, String value) {
        final List<Integer> indexesBefore = ParameterFieldAO.parameterIndexes();
        resetMouse();
        $(byId("add-parameter-dropdown-button")).shouldBe(visible).hover().click();
        $(byText("Output path parameter")).shouldBe(visible).click();

        final ParameterFieldAO parameter = ParameterFieldAO.parameterByIndex(addedParameterIndex(indexesBefore));
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
        $(button("Launch")).shouldBe(enabled, ofMillis(C.DEFAULT_TIMEOUT * 2));
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

    public PipelineRunFormAO checkLaunchMessage(final String typeMessage,
                                                final String message,
                                                final boolean isVisible) {
        assertEquals(checkMessage(typeMessage, message), isVisible,
                format("%s '%s' should be %s on Launch form: ", typeMessage, message,
                        isVisible ? "visible" : "not visible"));
        return this;
    }

    private boolean checkMessage(final String typeMessage, final String message) {
        $$(byClassName("ant-btn")).filterBy(text("Launch")).first().shouldBe(visible).click();
        $$(byClassName("ant-modal-body"))
                .findBy(text("Launch"))
                .find(byClassName("ob-estimated-price-info__info"))
                .shouldBe(visible);
        boolean messageExist = context().$(byClassName("ant-modal-body"))
                .findAll(byClassName(format("ant-alert-%s", typeMessage)))
                .stream()
                .map(SelenideElement::getText)
                .filter(e -> e.contains(message))
                .count() == 1;
        if (messageExist) {
            screenshot("check_launch_message" + Utils.randomSuffix());
        }
        $$(byClassName("ant-modal-body")).findBy(text("Cancel"))
                .find(button("Cancel"))
                .shouldBe(enabled)
                .click();
        return messageExist;
    }

    public PipelineRunFormAO checkLaunchItemName(String name) {
        context().find(launchItemName()).shouldHave(text(name));
        return this;
    }

    private By header() {
        return byClassName("launch-pipeline-form__layout-header");
    }

    public PipelineRunFormAO launch() {
        ensure(byText("Estimated price per hour:"), visible);
        $$(byClassName("ant-btn")).filterBy(text("Launch")).first().shouldBe(visible).click();
        $$(byClassName("ant-modal-body")).findBy(text("Launch")).find(byClassName("ob-estimated-price-info__info")).shouldBe(visible);
        $$(byClassName("ant-modal-body")).findBy(text("Launch")).find(button("Launch")).shouldBe(enabled).click();
        return this;
    }

    /**
     * Launches as {@link #launch()} does, and checks before the confirmation that the launch pop-up shows
     * no error alert (e.g. "Permission issues"). The pop-up computes its errors before it opens.
     */
    public PipelineRunFormAO launchWithoutErrorMessages() {
        ensure(byText("Estimated price per hour:"), visible);
        $$(byClassName("ant-btn")).filterBy(text("Launch")).first().shouldBe(visible).click();
        final SelenideElement launchPopup = $$(byClassName("ant-modal-body")).findBy(text("Launch"));
        launchPopup.find(byClassName("ob-estimated-price-info__info")).shouldBe(visible);
        launchPopup.findAll(byClassName("ant-alert-error")).shouldHave(size(0));
        launchPopup.find(button("Launch")).shouldBe(enabled).click();
        return this;
    }

    /**
     * The launch form shows the name of a configuration parameter as a text, and has no delete icon for a required
     * parameter (launch/form/parameters/parameter/index.js).
     */
    public PipelineRunFormAO validateThereIsParameterOfType(String name, String value, ParameterType type, boolean required) {
        final RunParameterAO parameter = new RunParameterAO(this, ParameterFieldAO.parameterByName(name).index());
        final SelenideElement valueElement = parameter.get(PARAMETER_VALUE)
                .should(exist, have(attribute("value", value)));
        parameter.get(REMOVE_PARAMETER).should(required ? not(exist) : visible);

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

    public RunParameterAO clickAddBooleanParameter() {
        return clickAddParameter("Boolean parameter");
    }

    /**
     * Adds the string parameter {@link #CREATE_NEW_NODE} with the value "true", so that the run gets a new node and
     * the node's InitializeNode task.
     */
    public PipelineRunFormAO createNewNode() {
        return clickAddStringParameter()
                .setName(CREATE_NEW_NODE)
                .setValue("true")
                .close();
    }

    private RunParameterAO clickAddParameter(String parameterType) {
        final List<Integer> indexesBefore = ParameterFieldAO.parameterIndexes();
        resetMouse();
        $(byId("add-parameter-dropdown-button")).shouldBe(visible).hover();
        $(byText(parameterType)).shouldBe(visible).click();

        parameterIndex = addedParameterIndex(indexesBefore);
        return new RunParameterAO(this, parameterIndex);
    }

    /**
     * The form gives an added parameter the lowest key index it has free, so the added parameter is the row
     * with a key index the form didn't have before.
     */
    private int addedParameterIndex(final List<Integer> indexesBefore) {
        $$(ParameterFieldAO.parameterRows()).shouldHave(sizeGreaterThan(indexesBefore.size()));
        return ParameterFieldAO.parameterIndexes()
                .stream()
                .filter(index -> !indexesBefore.contains(index))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No parameter has been added to the launch form"));
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

    // the "Configure cluster" link is in the row below the disk field (launch/form/LaunchPipelineForm.js)
    public PipelineRunFormAO checkConfigureClusterLabel(String label) {
        context().find(byXpath(".//div[contains(concat(' ', @class, ' '), " +
                        "' launch-pipeline-form__form-item-container ')]/a"))
                .shouldBe(visible).shouldHave(text(label));
        return this;
    }

    public PipelineRunFormAO inputSystemParameterValue(String parameter, String value) {
        $(ParameterFieldAO.parameterByName(parameter).valueInput).shouldBe(enabled).setValue(value);
        return this;
    }

    public String getCPU() {
        String cpu = $(byXpath(".//b[.='CPU']")).parent().getText();
        return cpu.substring(0, cpu.indexOf(" "));
    }

    public SelectLimitMountsPopupAO<PipelineRunFormAO> selectDataStoragesToLimitMounts() {
        click(LIMIT_MOUNTS);
        return new SelectLimitMountsPopupAO<>(this).sleep(2, SECONDS);
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

    public String getNodeType(final int minRAM) {
        sleep(1, SECONDS);
        get(INSTANCE_TYPE).shouldBe(visible).click();
        return SelenideElements.of(byClassName("ant-select-dropdown-menu-item")).texts()
                .stream()
                .filter(n -> n.contains("RAM: " + minRAM))
                .findFirst()
                .orElseThrow(() -> new NoSuchWindowException(String.format(
                        "No such node type with RAM {%s}.", minRAM)));
    }

    public PipelineRunFormAO doNotMountStoragesSelect (boolean isSelected) {
        if ((!get(DO_NOT_MOUNT_STORAGES).has(cssClass("ant-checkbox-checked")) && isSelected) ||
                (get(DO_NOT_MOUNT_STORAGES).has(cssClass("ant-checkbox-checked")) && !isSelected)) {
            click(DO_NOT_MOUNT_STORAGES);
        }
        return this;
    }

    public PipelineRunFormAO assertDoNotMountStoragesIsChecked() {
        get(DO_NOT_MOUNT_STORAGES).shouldHave(cssClass("ant-checkbox-checked"));
        return this;
    }

    public PipelineRunFormAO waitUntilSaveEnding(final String name) {
        int attempt = 0;
        int maxAttempts = 3;
        while ($(withText(String.format("Updating '%s' configuration ...", name))).exists()
                && attempt < maxAttempts) {
            sleep(3, SECONDS);
            attempt++;
        }
        sleep(1, SECONDS);
        return this;
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    public static class SystemParameterPopupAO  extends PopupAO<SystemParameterPopupAO, PipelineRunFormAO> {

        private final Map<Primitive, SelenideElement> elements = initialiseElements(
                entry(PARAMETER_NAME, context().$(byXpath(".//*[@placeholder='Parameter']"))),
                entry(ADD, context().find(byId("system-parameters-browser-ok-button"))),
                entry(CANCEL, context().find(byId("system-parameters-browser-cancel-button")))
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

        // A selected parameter is marked with a check-circle icon in front of its name
        private SystemParameterPopupAO selectSystemParameter(String parameter) {
            clear(PARAMETER_NAME);
            setValue(PARAMETER_NAME, parameter);
            final SelenideElement name = $(byText(parameter)).shouldBe(visible);
            if (!name.$(byClassName("anticon-check-circle")).exists()) {
                name.click();
            }
            name.$(byClassName("anticon-check-circle")).should(exist.because(
                    format("system parameter %s shall be marked as selected after a click", parameter)));
            return this;
        }

        // The OK button shows the number of the selected parameters: it is checked, so that a selection lost by the
        // dialog is reported here, and not later as a missing parameter of the launch form
        public SystemParameterPopupAO selectSystemParameters(String ... parameters) {
            Arrays.stream(parameters).forEach(this::selectSystemParameter);
            get(ADD).shouldHave(text(format("(%d)", parameters.length)).because(
                    format("the system parameters dialog shall keep all the %d selected parameters: %s",
                            parameters.length, String.join(", ", parameters))));
            return this;
        }
    }

    public static class ConfigureClusterPopupAO  extends PopupAO<ConfigureClusterPopupAO, PipelineRunFormAO> {

        private final Map<Primitive, SelenideElement> elements = initialiseElements(
                entry(WORKERS_PRICE_TYPE, context().find(byText("Workers price type:")).parent().find(byClassName("ant-select-selection--single"))),
                entry(WORKING_NODES, context().find(byXpath("(.//*[@class = 'ant-input-number-input'])[1]"))),
                entry(DEFAULT_CHILD_NODES, context().find(byXpath("(.//*[@class = 'ant-input-number-input'])[last()]"))),
                entry(RESET, context().$(byXpath(".//*[contains(text(), 'Reset')]")))
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

        /**
         * Types into the "Default child nodes" field that is already shown, without clicking
         * "Setup default child nodes count" again.
         */
        public ConfigureClusterPopupAO setDefaultChildNodesCount(final String nodesCount) {
            return setValue(DEFAULT_CHILD_NODES, nodesCount);
        }

        public ConfigureClusterPopupAO ensureOpened() {
            $(byXpath(".//*[contains(@class, 'ant-modal-title') and " +
                    "starts-with(normalize-space(.), 'Configure cluster')]")).shouldBe(visible);
            return this;
        }

        /**
         * Checks the validation message shown in the row right below the field with the given label,
         * e.g. "Child nodes:", "Auto-scaled up to:" or "Default child nodes:".
         */
        public ConfigureClusterPopupAO fieldMessageShouldBe(final String fieldLabel, final String message) {
            context().find(byXpath(format(
                    ".//span[normalize-space(text()) = '%s']/ancestor::div[contains(@class, 'ant-row-flex')][1]" +
                            "/following-sibling::div[1][contains(@class, 'cp-error')]", fieldLabel)))
                    .shouldBe(visible)
                    .shouldHave(exactText(message));
            return this;
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
