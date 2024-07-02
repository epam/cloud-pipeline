/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
package com.epam.pipeline.autotests.ao.settings;

import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.ao.PipelinesLibraryAO;
import com.epam.pipeline.autotests.ao.Primitive;
import com.epam.pipeline.autotests.ao.SettingsPageAO;

import java.util.Arrays;
import java.util.Map;

import static com.codeborne.selenide.Condition.matchesText;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byCssSelector;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.GIT_CLI;
import static com.epam.pipeline.autotests.ao.Primitive.GIT_COMMAND;
import static com.epam.pipeline.autotests.ao.Primitive.PIPE_CLI;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.menuitem;
import static org.openqa.selenium.By.tagName;

public class CliAO extends SettingsPageAO {
    public final Map<Primitive, SelenideElement> elements = initialiseElements(
            super.elements(),
            entry(PIPE_CLI, context().findAll("tr").find(text("Pipe CLI"))),
            entry(GIT_CLI, context().findAll("tr").find(text("Git CLI"))),
            entry(GIT_COMMAND, context().find(byCssSelector(".tyles__md-preview")))
    );

    public CliAO(final PipelinesLibraryAO parent) {
        super(parent);
    }

    public CliAO switchGitCLI() {
        click(GIT_CLI);
        return this;
    }

    public CliAO switchPipeCLI() {
        click(PIPE_CLI);
        return this;
    }

    public CliAO ensureCodeHasText(final String text) {
        ensure(GIT_COMMAND, matchesText(text));
        return this;
    }

    public CliAO selectOperationSystem(final OperationSystem operationSystem) {
        final String defaultSystem = $(tagName("b")).parent().find(byClassName("ant-select-selection__rendered"))
                .getText();
        selectValue(byText(defaultSystem), menuitem(operationSystem.getName()));
        return this;
    }

    public CliAO checkOperationSystemInstallationContent(final String content) {
        ensure(byId("pip-install-url-input"), text(content));
        return this;
    }

    public String getOperationSystemInstallationContent() {
        return $(byId("pip-install-url-input"))
                .getText()
                .replaceAll("#(.+)\n", "")
                .replaceAll("\n", " && ");
    }

    public CliAO generateAccessKey() {
        click(byId("generate-access-key-button"));
        return this;
    }

    public String getCLIConfigureCommand() {
        return $(byId("cli-configure-command-text-area")).getText();
    }

    public String getPipePathFromConfigureCommand() {
        final String cliConfigureCommand = getCLIConfigureCommand();
        return cliConfigureCommand.substring(0, cliConfigureCommand.indexOf(" configure"));
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    public enum OperationSystem {
        LINUX_BINARY("Linux-Binary"),
        LINUX_TARBALL("Linux-Tarball"),
        MACOS_BINARY("MacOS-Binary"),
        MACOS_TARBALL("MacOS-Tarball"),
        WINDOWS("Windows");

        public String getName() {
            return name;
        }

        public final String name;

        OperationSystem(String name) {
            this.name = name;
        }

        public OperationSystem getByName(final String name) {
            return Arrays.stream(values())
                    .filter(operationSystem -> operationSystem.getName().equals(name))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException(
                            String.format("%s is not a valid operation system name.", name)));
        }
    }
}
