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

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.*;
import static com.codeborne.selenide.Selenide.$$;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;

public class CreatePipelinePopupAO extends PopupAO<CreatePipelinePopupAO, PipelinesLibraryAO> {

    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            entry(CANCEL, context().find(button("CANCEL"))),
            entry(CREATE, context().find(byId("edit-pipeline-form-create-button"))),
            entry(NAME,  context().find(byId("name"))),
            entry(DESCRIPTION, context().find(byId("description"))),
            entry(REPOSITORY, context().find(byId("repository"))),
            entry(TOKEN, context().find(byId("token"))),
            entry(EDIT_REPOSITORY_SETTINGS, context().find(byText("Edit repository settings")))
    );

    @Override
    public SelenideElement context() {
        return $$(byClassName("ant-modal-content")).find(visible);
    }

    public CreatePipelinePopupAO() {
        super(new PipelinesLibraryAO());
    }

    public CreatePipelinePopupAO setName(String name) {
        return setValue(NAME, name);
    }

    public CreatePipelinePopupAO setDescription(String description) {
        return setValue(DESCRIPTION, description);
    }

    public CreatePipelinePopupAO setRepository(String repository) {
        return setValue(REPOSITORY, repository);
    }

    public CreatePipelinePopupAO setToken(String token) {
        return setValue(TOKEN, token);
    }

    public CreatePipelinePopupAO openRepositorySettings() {
        return click(EDIT_REPOSITORY_SETTINGS);
    }

    public PipelinesLibraryAO create() {
        return click(CREATE).parent();
    }

    public ConfirmationPopupAO<CreatePipelinePopupAO> createWithIncorrectData() {
        click(CREATE);
        return new ConfirmationPopupAO<>(this);
    }

    @Override
    public PipelinesLibraryAO ok() {
        return create();
    }

    @Override
    public PipelinesLibraryAO cancel() {
        return click(CANCEL).parent();
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }
}
