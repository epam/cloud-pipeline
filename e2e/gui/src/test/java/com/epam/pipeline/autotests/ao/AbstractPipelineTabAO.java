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
import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.Objects;
import static com.codeborne.selenide.Condition.attribute;
import static com.codeborne.selenide.Condition.exactText;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.CANCEL;
import static com.epam.pipeline.autotests.ao.Primitive.CODE_TAB;
import static com.epam.pipeline.autotests.ao.Primitive.COMMIT;
import static com.epam.pipeline.autotests.ao.Primitive.CONFIGURATION_TAB;
import static com.epam.pipeline.autotests.ao.Primitive.DOCUMENTS_TAB;
import static com.epam.pipeline.autotests.ao.Primitive.GRAPH_TAB;
import static com.epam.pipeline.autotests.ao.Primitive.HISTORY_TAB;
import static com.epam.pipeline.autotests.ao.Primitive.MESSAGE;
import static com.epam.pipeline.autotests.ao.Primitive.RUN;
import static com.epam.pipeline.autotests.ao.Primitive.STORAGE_RULES_TAB;
import static com.epam.pipeline.autotests.ao.Primitive.UPDATE_CONFIGURATION;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.openqa.selenium.By.className;

public abstract class AbstractPipelineTabAO<TAB_AO extends AbstractPipelineTabAO<TAB_AO>>
        implements AccessObject<TAB_AO> {

    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            entry(CODE_TAB, $(".pipeline-details__tabs-menu").findAll("li").findBy(text("CODE"))),
            entry(DOCUMENTS_TAB, $(".pipeline-details__tabs-menu").findAll("li").findBy(text("DOCUMENTS"))),
            entry(CONFIGURATION_TAB, $(".pipeline-details__tabs-menu").findAll("li").findBy(text("CONFIGURATION"))),
            entry(GRAPH_TAB, $(".pipeline-details__tabs-menu").findAll("li").findBy(text("GRAPH"))),
            entry(HISTORY_TAB, $(".pipeline-details__tabs-menu").findAll("li").findBy(text("HISTORY"))),
            entry(STORAGE_RULES_TAB, $(".pipeline-details__tabs-menu").findAll("li").findBy(text("STORAGE RULES"))),
            entry(RUN, $(byId("launch-pipeline-button")))
    );
    private final String pipelineName;

    public AbstractPipelineTabAO(String pipelineName) {
        this.pipelineName = pipelineName;
    }

    public <TAB_AO extends AbstractPipelineTabAO> TAB_AO onTab(final Class<TAB_AO> tabClass) {
        Objects.requireNonNull(tabClass);
        try {
            final Constructor<TAB_AO> constructor = tabClass.getConstructor(String.class);
            final TAB_AO tab = constructor.newInstance(pipelineName);
            tab.open();
            return tab;
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException("Unable to instantiate ", exception);
        }
    }

    public DocumentTabAO documentsTab() {
        return onTab(DocumentTabAO.class);
    }

    public PipelineCodeTabAO codeTab() {
        return onTab(PipelineCodeTabAO.class);
    }

    public PipelineConfigurationTabAO configurationTab() {
        return onTab(PipelineConfigurationTabAO.class);
    }

    public PipelineGraphTabAO graphTab() {
        return onTab(PipelineGraphTabAO.class);
    }

    public PipelineHistoryTabAO historyTab() {
        return onTab(PipelineHistoryTabAO.class);
    }

    public StorageRulesTabAO storageRulesTab() {
        return onTab(StorageRulesTabAO.class);
    }

    protected void changeTabTo(Primitive tab) {
        sleep(2, SECONDS).click(tab).tabShouldBeActive(tab);
    }

    protected abstract TAB_AO open();

    public TAB_AO tabShouldBeActive(Primitive primitive) {
        return ensure(primitive, visible, attribute("aria-selected", "true"));
    }

    public PipelineRunFormAO runPipeline() {
        sleep(2, SECONDS);
        click(RUN);
        sleep(1, SECONDS);
        return new PipelineRunFormAO(pipelineName);
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    public static class CommitPopupAO<PARENT_AO> extends PopupWithStringFieldAO<CommitPopupAO<PARENT_AO>, PARENT_AO> {

        private final Map<Primitive, SelenideElement> elements = initialiseElements(
                entry(MESSAGE, context().find(byId("message"))),
                entry(COMMIT, context().findAll(className("ant-btn")).find(exactText("Commit"))),
                entry(CANCEL, context().findAll(className("ant-btn")).find(exactText("Cancel"))),
                entry(UPDATE_CONFIGURATION, context().find(byClassName("ant-checkbox-wrapper")))
        );

        public CommitPopupAO(PARENT_AO parentAO) {
            super(parentAO);
        }

        @Override
        public SelenideElement context() {
            return Utils.getPopupByTitle("Commit");
        }

        @Override
        public PARENT_AO cancel() {
            click(CANCEL);
            return parent();
        }

        @Override
        public PARENT_AO ok() {
            click(COMMIT);
            return parent();
        }

        @Override
        public CommitPopupAO<PARENT_AO> typeInField(String value) {
            return setValue(MESSAGE, value);
        }

        @Override
        public Map<Primitive, SelenideElement> elements() {
            return elements;
        }

        public CommitPopupAO<PARENT_AO> updateConfiguration() {
            click(UPDATE_CONFIGURATION);
            return this;
        }
    }

}
