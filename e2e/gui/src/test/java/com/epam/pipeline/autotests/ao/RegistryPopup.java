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
import java.io.File;
import java.util.Map;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selectors.withText;
import static com.epam.pipeline.autotests.ao.Primitive.CANCEL;
import static com.epam.pipeline.autotests.ao.Primitive.CERTIFICATE;
import static com.epam.pipeline.autotests.ao.Primitive.DELETE;
import static com.epam.pipeline.autotests.ao.Primitive.DESCRIPTION;
import static com.epam.pipeline.autotests.ao.Primitive.EDIT_CREDENTIALS;
import static com.epam.pipeline.autotests.ao.Primitive.INFO;
import static com.epam.pipeline.autotests.ao.Primitive.NAME;
import static com.epam.pipeline.autotests.ao.Primitive.PASSWORD;
import static com.epam.pipeline.autotests.ao.Primitive.PATH;
import static com.epam.pipeline.autotests.ao.Primitive.PERMISSIONS;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static org.openqa.selenium.By.tagName;

public abstract class RegistryPopup extends PopupAO<RegistryPopup, ToolsPage> {

    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            entry(PERMISSIONS, context().find(byClassName("ant-tabs-nav")).find(byText("Permissions"))),
            entry(INFO, context().find(byClassName("ant-tabs-nav")).find(byText("Info"))),
            entry(PATH, context().find(byId("path"))),
            entry(DESCRIPTION, context().find(byId("description"))),
            entry(EDIT_CREDENTIALS, context().find(byText("Edit credentials"))),
            entry(NAME, context().find(byId("userName"))),
            entry(PASSWORD, context().find(byId("password"))),
            entry(CERTIFICATE, context().find(withText("Certificate")).closest(".ant-row-flex").find(tagName("input"))),
            entry(CANCEL, context().find(button("CANCEL"))),
            entry(DELETE, context().find(button("DELETE")))
    );

    public RegistryPopup() {
        super(new ToolsPage());
    }

    public RegistryPopup setPath(final String registryPath) {
        return setValue(PATH, registryPath);
    }

    public RegistryPopup openCredentials() {
        return click(EDIT_CREDENTIALS);
    }

    public RegistryPopup setCredentials(final String login, final String password) {
        return setValue(NAME, login).setValue(PASSWORD, password);
    }

    public RegistryPopup setDescription(final String description) {
        return setValue(DESCRIPTION, description);
    }

    public RegistryPopup uploadCertificate(final File certificate) {
        get(CERTIFICATE).uploadFile(certificate);
        return this;
    }

    public PermissionTabAO permissions() {
        click(PERMISSIONS);
        return new PermissionTabAO(this);
    }

    @Override
    public ToolsPage cancel() {
        return click(CANCEL).parent();
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }
}
