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
import com.epam.pipeline.autotests.utils.Utils;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;
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
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.openqa.selenium.By.className;
import static org.openqa.selenium.By.cssSelector;
import static org.openqa.selenium.By.id;
import static org.openqa.selenium.By.tagName;
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
            entry(PROPERTIES, context().find(byClassName("graph__properties-button"))),
            entry(ADD_TASK, context().find(byId("wdl-graph-workflow-add-task-button"))),
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

    public TaskAdditionPopupAO openAddTaskDialog() {
        click(PROPERTIES);
        click(ADD_TASK);
        return new TaskAdditionPopupAO(this);
    }

    public ScatterAdditionPopupAO openAddScatterDialog() {
        click(PROPERTIES);
        click(ADD_SCATTER);
        return new ScatterAdditionPopupAO(this);
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
        $$(byClassName("label")).findBy(matchText(labelText)).shouldBe(visible);
        return this;
    }

    public PipelineGraphTabAO searchScatter(String labelText) {
        $$(byClassName("port-label")).findBy(text(labelText)).shouldBe(visible);
        return this;
    }

    public PipelineGraphTabAO clickLabel(String name) {
        fit().minimize().minimize().minimize();
        $$(byClassName("label")).findBy(text(name)).shouldBe(visible).click();
        return this;
    }

    public TaskAdditionPopupAO clickTask(final String name) {
        clickLabel(name);
        return new TaskEditionPopupAO(this);
    }

    public ScatterAdditionPopupAO clickScatter(final String name) {
        clickLabel(name);
        return new ScatterAdditionPopupAO(this);
    }

    public TaskEditionPopupAO edit() {
        click(PROPERTIES);
        return new TaskEditionPopupAO(this);
    }

    public WorkflowEditionPopupAO editWorkflow() {
        click(PROPERTIES);
        return new WorkflowEditionPopupAO(this);
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
        if (zIndexPipelineLibrary.equals("auto")) {
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

    public static class TaskAdditionPopupAO extends PopupAO<TaskAdditionPopupAO, PipelineGraphTabAO> {

        private final Map<Primitive, SelenideElement> elements = initialiseElements(
                entry(NAME, context().find(byId("name"))),
                entry(ALIAS, context().find(byId("alias"))),
                entry(INPUT_PANEL, context().findAll(byId("expand-panel-button")).find(text("Inputs"))),
                entry(INPUT_ADD, context().find(".edit-wdl-form-inputs-container").find(byId("add-variable-button"))),
                entry(OUTPUT_PANEL, context().findAll(byId("expand-panel-button")).find(text("Outputs"))),
                entry(OUTPUT_ADD, context().find(".edit-wdl-form-outputs-container").find(byId("add-variable-button"))),
                entry(ANOTHER_DOCKER_IMAGE, Utils.getFormRowByLabel(context(), "Use another docker image").find(byClassName("ant-checkbox-wrapper"))),
                entry(ANOTHER_COMPUTE_NODE, Utils.getFormRowByLabel(context(), "Use another compute node").find(byClassName("ant-checkbox-wrapper"))),
                entry(DOCKER_IMAGE_COMBOBOX, context().find(byId("docker-image-input"))),
                entry(COMMAND, context().find(byClassName("w-d-l-item-properties__code-editor"))),
                entry(ADD, context().find(byId("edit-wdl-form-add-button"))),
                entry(CANCEL, context().find(byClassName("anticon-close"))),
                entry(DELETE, context().find(byId("wdl-graph-task-delete-button")))
        );

        public TaskAdditionPopupAO(final PipelineGraphTabAO parentAO) {
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

        public SectionRowAO<TaskAdditionPopupAO> clickInputSectionAddButton() {
            click(INPUT_PANEL);
            click(INPUT_ADD);
            return new SectionRowAO<>(this);
        }

        public SectionRowAO<TaskAdditionPopupAO> clickOutputSectionAddButton() {
            click(OUTPUT_PANEL);
            click(OUTPUT_ADD);
            return new SectionRowAO<>(this);
        }

        public TaskAdditionPopupAO setName(String name) {
            return setValue(NAME, name);
        }

        public TaskAdditionPopupAO setCommand(String command) {
            actions().moveToElement($(byClassName("CodeMirror-line"))).click().perform();
            Utils.clickAndSendKeysWithSlashes($(byClassName("CodeMirror-line")), command);
            return this;
        }

        public TaskAdditionPopupAO enableAnotherDockerImage() {
            return click(ANOTHER_DOCKER_IMAGE);
        }

        public TaskAdditionPopupAO disableAnotherDockerImage() {
            return enableAnotherDockerImage();
        }

        public DockerImageSelection openDockerImagesCombobox() {
            sleep(1, SECONDS);
            click(DOCKER_IMAGE_COMBOBOX);
            return new DockerImageSelection(this);
        }
    }

    public static class TaskEditionPopupAO extends TaskAdditionPopupAO {

        private Map<Primitive, SelenideElement> elements = initialiseElements(
                super.elements(),
                entry(ALIAS, Utils.getFormRowByLabel(context(), "Alias").find(byId("alias"))),
                entry(SAVE, context().find(byId("wdl-graph-save-button"))),
                entry(REVERT, context().find(byId("wdl-graph-revert-button")))
        );

        public TaskEditionPopupAO(PipelineGraphTabAO parentAO) {
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
    }

    public static class WorkflowEditionPopupAO extends TaskEditionPopupAO {

        private Map<Primitive, SelenideElement> elements = initialiseElements(
                super.elements()
        );

        public WorkflowEditionPopupAO(PipelineGraphTabAO parentAO) {
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
    }

    public static class ScatterAdditionPopupAO extends PopupAO<ScatterAdditionPopupAO, PipelineGraphTabAO> {

        private final Map<Primitive, SelenideElement> elements = initialiseElements(
                entry(ADD_TASK, context().find(byId("wdl-graph-scatter-add-task-button"))),
                entry(DELETE, context().find(byId("wdl-graph-scatter-delete-button"))),
                entry(INPUT_PANEL, context().find(byId("expand-panel-button")))
        );

        public ScatterAdditionPopupAO(PipelineGraphTabAO parentAO) {
            super(parentAO);
        }

        @Override
        public Map<Primitive, SelenideElement> elements() {
            return elements;
        }

        @Override
        public PipelineGraphTabAO ok() {
            return click(ADD).parent();
        }

        public SectionRowAO<ScatterAdditionPopupAO> clickInputSectionAddButton() {
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
                entry(NAME, context().find(byClassName("variable-name"))),
                entry(TYPE, context().find(byClassName("ant-select-search__field"))),
                entry(VALUE, context().find(byClassName("variable-value"))),
                entry(DELETE_ICON, context().find(byId("remove-variable-button")))
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
            click(TYPE);
            return new TypeCombobox(this);
        }

        public SectionRowAO<PARENT_TYPE> setName(String name) {
            return setValue(NAME, name);
        }

        public SectionRowAO<PARENT_TYPE> setType(String type) {
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
