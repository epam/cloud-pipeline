/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import static com.codeborne.selenide.Selectors.withText;
import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.ao.PipelineGraphTabAO.ScatterPropertiesPopupAO.SectionRowAO;
import com.epam.pipeline.autotests.utils.Utils;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;

import static java.lang.String.format;

import static com.codeborne.selenide.ClickOptions.usingJavaScript;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.matchText;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.actions;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static com.epam.pipeline.autotests.utils.Utils.selectAllAndClearTextField;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.openqa.selenium.By.className;
import static org.openqa.selenium.By.cssSelector;
import static org.openqa.selenium.By.id;
import static org.openqa.selenium.By.tagName;
import static org.openqa.selenium.By.xpath;
import static org.testng.Assert.assertTrue;

public class PipelineGraphTabAO extends AbstractPipelineTabAO<PipelineGraphTabAO> {

    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            super.elements(),
            entry(SAVE, context().find(byId("wdl-graph-save-button"))),
            entry(REVERT, context().find(byId("wdl-graph-revert-button"))),
            entry(LAYOUT, context().find(byId("wdl-graph-layout-button"))),
            entry(FIT, context().find(byId("wdl-graph-fit-button"))),
            entry(SHOW_LINKS, context().find(byId("wdl-graph-show-links-button"))),
            entry(ADD_SCATTER, context().find(byId("wdl-graph-workflow-add-scatter-button"))),
            entry(CALL, $(byClassName("rc-menu-submenu-vertical"))),
            entry(PROPERTIES, context().find(button("PROPERTIES"))),
            entry(CREATE_PIPELINE, $(byClassName("create-pipeline-sub-menu-button"))),
            entry(EDIT_TASK, context().find(byId("wdl-graph-task-edit-button"))),
            entry(EDIT_WORKFLOW, context().find(byId("wdl-graph-workflow-edit-button"))),
            entry(CANVAS, context().find(tagName("canvas"))),
            entry(ZOOM_OUT, $(id("wdl-graph-zoom-out-button"))),
            entry(ZOOM_IN, $(id("wdl-graph-zoom-in-button"))),
            entry(FULLSCREEN, $(id("wdl-graph-fuulscreen-button")))
    );

    public PipelineGraphTabAO(String pipelineName) {
        super(pipelineName);
    }

    @Override
    protected PipelineGraphTabAO open() {
        changeTabTo(GRAPH_TAB);
        return this;
    }

    public WorkflowPropertiesPopupAO editWorkflow() {
        click(PROPERTIES);
        $(byClassName("visual-workflow")).$(byClassName("v-line")).click();
        return new WorkflowPropertiesPopupAO(this);
    }

    public TaskPropertiesPopupAO editTask(final String name) {
        $$(byClassName("visual-call")).filter(text(name)).first()
                .$(byClassName("v-line")).click();
        return new TaskPropertiesPopupAO(this);
    }

    public ScatterPropertiesPopupAO editScatter(final String name) {
        $$(byClassName("visual-call")).filter(text(name)).first()
                .$(byClassName("v-line")).click();
        return new ScatterPropertiesPopupAO(this);
    }

    public PipelineGraphTabAO revert() {
        return click(REVERT);
    }

    public PipelineGraphTabAO fit() {
        return click(FIT);
    }

    public PipelineGraphTabAO minimize() {
        return click(ZOOM_OUT);
    }

    public PipelineGraphTabAO searchLabel(String labelText) {
        $$(className("joint-element")).findBy(matchText(labelText)).shouldBe(visible);
        return this;
    }

    public PipelineGraphTabAO searchScatter(String labelText) {
        $$(byClassName("joint-port-label")).findBy(text(labelText)).shouldBe(visible);
        return this;
    }

    public PipelineGraphTabAO clickLabel(String name) {
        fit().minimize().minimize().minimize();
        SelenideElement task = $$(byClassName("joint-element")).findBy(text(name))
                .$(className("visual-element-body")).shouldBe(visible);
        int width = Math.round(task.getSize().width/2);
        task.shouldBe(visible).click(usingJavaScript().offset(width, 0));
        return this;
    }

    public TaskPropertiesPopupAO edit() {
        click(PROPERTIES);
        return new TaskPropertiesPopupAO(this);
    }

    public PipelineGraphTabAO saveAndCommitWithMessage(String message) {
        return openCommitDialog().typeInField(message).ok();
    }

    public PipelineGraphTabAO saveAndChangeJsonWithMessage(String message) {
        return openCommitDialog().typeInField(message).updateConfiguration().ok();
    }

    public CommitPopupAO<PipelineGraphTabAO> openCommitDialog() {
        ensure(SAVE, enabled).click(SAVE);
        return new CommitPopupAO<>(this);
    }

    public PipelineGraphTabAO verifyFullcreen() {
        final int zIndexGraph = Integer.parseInt(
                $(byClassName("graph__graph-container-full-screen")).getCssValue("z-index"));
        final String zIndexPipelineLibrary = $(byId("pipelines-library-content")).getCssValue("z-index");
        if ("auto".equals(zIndexPipelineLibrary)) {
            assertTrue(zIndexGraph > 1);
        } else {
            assertTrue(zIndexGraph > Integer.parseInt(zIndexPipelineLibrary));
        }
        return this;
    }

    @Override
    public SelenideElement context() {
        return $(className("graph__graph-container"));
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    public static class TaskPropertiesPopupAO extends PopupAO<TaskPropertiesPopupAO, PipelineGraphTabAO> {

        private final Map<Primitive, SelenideElement> elements = initialiseElements(
                entry(NAME, context().find(byId("name"))),
                entry(ALIAS, context().$(withText("Alias")).parent().sibling(0)),
                entry(INPUT_ADD, context()
                        .$(withText("Inputs")).parent().find(button("ADD"))),
                entry(OUTPUT_ADD, context()
                        .$(withText("Outputs")).parent().find(button("ADD"))),
                entry(RUNTIME, context().$(withText("Runtime"))),
                entry(ANOTHER_DOCKER_IMAGE, $(withText("add docker configuration"))),
                entry(ANOTHER_COMPUTE_NODE, $(withText("add compute node configuration"))),
                entry(DOCKER_IMAGE_COMBOBOX, context()
                        .find(xpath(".//*[contains(@class, 'dl-properties-form__property-title') and contains(., 'docker')]"))
                        .sibling(0).find(className("anticon-tool"))),
                entry(COMMAND, context().find(byClassName("CodeMirror-code"))),
                entry(DELETE, context().find(button("Remove")))
        );

        public TaskPropertiesPopupAO(final PipelineGraphTabAO parentAO) {
            super(parentAO);
        }

        @Override
        public Map<Primitive, SelenideElement> elements() {
            return elements;
        }
        @Override
        public PipelineGraphTabAO cancel() {
            return click(CANCEL).parent();
        }

        @Override
        public PipelineGraphTabAO ok() {
            return click(ADD).parent();
        }

        public SectionRowAO<TaskPropertiesPopupAO> clickInputSectionAddButton() {
            click(INPUT_ADD);
            return new SectionRowAO<>(this);
        }

        public SectionRowAO<TaskPropertiesPopupAO> clickOutputSectionAddButton() {
            click(OUTPUT_PANEL);
            click(OUTPUT_ADD);
            return new SectionRowAO<>(this);
        }

        public TaskPropertiesPopupAO setName(String name) {
            return setValue(NAME, name);
        }

        public TaskPropertiesPopupAO setCommand(String command) {
            actions().moveToElement($(byClassName("CodeMirror-line"))).click().perform();
            Utils.clickAndSendKeysWithSlashes($(byClassName("CodeMirror-line")), command);
            return this;
        }

        public TaskPropertiesPopupAO enableAnotherComputeNode() {
            return click(ANOTHER_COMPUTE_NODE);
        }

        public TaskPropertiesPopupAO enableAnotherDockerImage() {
            return click(ANOTHER_DOCKER_IMAGE);
        }

        public TaskPropertiesPopupAO deleteAdditionalConfiguration(String conf) {
            context()
                    .find(xpath(format(".//*[contains(@class, 'dl-properties-form__property-title') and contains(., '%s')]", conf)))
                    .parent().find(className("anticon-delete")).shouldBe(visible, enabled).click();
            return this;
        }

        public DockerImageSelection openDockerImagesCombobox() {
            sleep(1, SECONDS);
            click(DOCKER_IMAGE_COMBOBOX);
            return new DockerImageSelection(this);
        }
    }

    public static class WorkflowPropertiesPopupAO extends TaskPropertiesPopupAO {

        private Map<Primitive, SelenideElement> elements = initialiseElements(
                super.elements(),
                entry(ACTIONS, context().find(button("Actions"))),
                entry(ADD_CALL, context().$(byClassName("rc-menu-submenu-title"))),
                entry(ADD_SCATTER, context()
                        .$(xpath(".//*[contains(@class, 'rc-menu-item') and contains(., 'Add scatter')]")))
        );

        public WorkflowPropertiesPopupAO(PipelineGraphTabAO parentAO) {
            super(parentAO);
        }

        public TaskPropertiesPopupAO createNewTask() {
            resetMouse().click(ACTIONS).hover(ADD_CALL);
            $(byText("new task")).shouldBe(visible).click();
            return new TaskPropertiesPopupAO(parent());
        }

        public ScatterPropertiesPopupAO createNewScatter() {
            resetMouse().click(ACTIONS).click(ADD_SCATTER);
            return new ScatterPropertiesPopupAO(parent());
        }

        @Override
        public Map<Primitive, SelenideElement> elements() {
            return elements;
        }

        @Override
        public PipelineGraphTabAO ok() {
            return click(SAVE).parent();
        }
    }

    public static class ScatterPropertiesPopupAO extends WorkflowPropertiesPopupAO {

        private final Map<Primitive, SelenideElement> elements = initialiseElements(
                entry(DELETE, context().find(button("Remove"))),
                entry(INPUT_PANEL, context().find(byId("expand-panel-button")))
        );

        public ScatterPropertiesPopupAO(PipelineGraphTabAO parentAO) {
            super(parentAO);
        }

        @Override
        public Map<Primitive, SelenideElement> elements() {
            return elements;
        }

        @Override
        public PipelineGraphTabAO ok() {
            return click(SAVE).parent();
        }

        public TaskPropertiesPopupAO createNewTask() {
            resetMouse().click(ACTIONS).hover(ADD_CALL);
            $(byText("new task")).shouldBe(visible).click();
            return new TaskPropertiesPopupAO(parent());
        }

        public SectionRowAO<ScatterPropertiesPopupAO> clickInputSectionAddButton() {
            click(INPUT_PANEL);
            return new SectionRowAO<>(this);
        }

        public PipelineGraphTabAO cancel() {
            return parent().revert();
        }

    }

    @SuppressWarnings("unchecked")
    public static class SectionRowAO<PARENT_TYPE extends AccessObject<PARENT_TYPE>>
            implements AccessObject<SectionRowAO<PARENT_TYPE>> {

        private final Map<Primitive, SelenideElement> elements = initialiseElements(
                entry(NAME, context().find(byClassName("dl-parameter__wdl-parameter-name"))),
                entry(TYPE, context().find(byClassName("dl-parameter__wdl-parameter-type"))),
                entry(VALUE, context().find(byClassName("dl-parameter__wdl-parameter-value"))),
                entry(DELETE_ICON, context().find(byClassName("dl-parameter__wdl-parameter-delete-button")))
        );

        private final PARENT_TYPE parentAO;

        public SectionRowAO(PARENT_TYPE parentAO) {
            this.parentAO = parentAO;
        }

        @Override
        public Map<Primitive, SelenideElement> elements() {
            return elements;
        }

        public TypeCombobox openTypeCombobox() {
            selectAllAndClearTextField(get(TYPE));
            return new TypeCombobox(this);
        }

        public SectionRowAO<PARENT_TYPE> setName(String name) {
            return setValue(NAME, name);
        }

        public SectionRowAO<PARENT_TYPE> setType(String type) {
            selectAllAndClearTextField(get(TYPE));
            return openTypeCombobox().set(type).close();
        }

        public SectionRowAO<PARENT_TYPE> setValue(String value) {
            return setValue(VALUE, value);
        }

        private SelenideElement inputByColumn(SelenideElement context, int num) {
            return column(context, num).find(tagName("input"));
        }

        private SelenideElement column(SelenideElement context, int num) {
            return row(context).find(cssSelector(String.format("td:nth-child(%d)", num)));
        }

        private SelenideElement row(SelenideElement context) {
            return context.find(className("ant-table-row"));
        }

        public PARENT_TYPE close() {
            return parentAO;
        }

        public PARENT_TYPE dropCurrentRow() {
            click(DELETE_ICON);
            return parentAO;
        }
    }

    public static class TypeCombobox extends ComboboxAO<TypeCombobox, SectionRowAO> {
        private final SectionRowAO parentAO;

        public TypeCombobox(SectionRowAO parentAO) {
            super(parentAO);
            this.parentAO = parentAO;
        }

        @Override
        SelenideElement closingElement() {
            // any other place
            return parentAO.get(NAME);
        }

        public static Consumer<TypeCombobox> shouldContainTypes(final String... types) {
            return combobox -> Arrays.stream(types).forEach(type -> combobox.ensure(byText(type), visible));
        }
    }

}
