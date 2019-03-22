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
package com.epam.pipeline.autotests;

import com.epam.pipeline.autotests.ao.ToolsPage;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.util.function.Consumer;

import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.ao.Primitive.REGISTRIES_LIST;
import static com.epam.pipeline.autotests.ao.Primitive.REGISTRY;

public class RegistryAdditionDeletionTest
        extends AbstractBfxPipelineTest
        implements Navigation {

    private final String registryPath = C.REGISTRY_PATH_FOR_TOOL;
    private final String registryLogin = C.REGISTRY_LOGIN_FOR_TOOL;
    private final String registryPassword = C.REGISTRY_PASSWORD_FOR_TOOL;
    private final File certificate = Utils.createTempFile("certificate");

    @BeforeClass
    @AfterClass(alwaysRun = true)
    public void deleteRegistryIfExists() {
        open(C.ROOT_ADDRESS);
        tools().performIf(REGISTRY, text(registryPath), deleteRegistry(registryPath))
                .click(REGISTRY).performIf(REGISTRIES_LIST, text(registryPath), deleteRegistry(registryPath));
    }

    @Test
    @TestCase({"EPMCMBIBPC-439"})
    public void registryAddition() {
        tools()
                .addRegistry(registry ->
                        registry.setPath(registryPath)
                                .openCredentials()
                                .setCredentials(registryLogin, registryPassword)
                                .uploadCertificate(certificate)
                                .ok()
                )
                .ensure(byText("Personal tool group was not found in registry."), visible);
    }

    @Test(dependsOnMethods = {"registryAddition"})
    @TestCase({"EPMCMBIBPC-438"})
    public void registryDeletion() {
        tools()
                .deleteRegistry(registryPath, registry ->
                        registry.ensureTitleIs(String.format("Are you sure you want to delete registry %s?", registryPath)).ok()
                )
                .click(REGISTRY).also(registryIsNotPresented(registryPath));
    }

    private Consumer<ToolsPage> registryIsNotPresented(final String registryName) {
        return registry -> registry.get(REGISTRIES_LIST).shouldNotHave(text(registryName));
    }

    private Consumer<ToolsPage> deleteRegistry(final String registryPath) {
        return tools -> tools.deleteRegistry(registryPath, registry ->
                registry.ensureTitleIs(String.format("Are you sure you want to delete registry %s?", registryPath)).ok()
        );
    }
}
