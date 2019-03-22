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

import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.RegistryPermission;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.io.File;

import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.utils.Privilege.*;
import static java.util.concurrent.TimeUnit.SECONDS;

public class RegistryTest
        extends AbstractBfxPipelineTest
        implements Navigation, Authorization {
    private final String registryPath = C.REGISTRY_PATH_FOR_TOOL;
    private final String invalidRegistryPath = C.INVALID_REGISTRY_PATH_FOR_TOOL;
    private final String testingRegistry = "Testing registry";
    private final String invalidRegistryDescription = "Invalid testing registry";
    private final String registryLogin = C.REGISTRY_LOGIN_FOR_TOOL;
    private final String registryPassword = C.REGISTRY_PASSWORD_FOR_TOOL;
    private final File certificate = Utils.createTempFile();

    @AfterClass(alwaysRun = true)
    public void deleteTestingRegistry() {
        open(C.ROOT_ADDRESS);
        tools().deleteRegistry(testingRegistry, registry ->
                registry.ensureTitleIs(String.format("Are you sure you want to delete registry %s?", testingRegistry)).ok()
        );
    }

    @Test
    @TestCase(value = "EPMCMBIBPC-667")
    public void checkEditCredentialsButton() {
        tools().addRegistry(registry ->
                registry.openCredentials()
                        .ensureVisible(NAME, PASSWORD, CERTIFICATE)
                        .cancel()
        );
    }

    @Test(dependsOnMethods = {"checkEditCredentialsButton"})
    @TestCase(value = "EPMCMBIBPC-673")
    public void checkEditRegistryAccessForNonAdminUser() {
        tools()
                .addRegistry(registry ->
                        registry.setPath(registryPath)
                                .setDescription(testingRegistry)
                                .openCredentials()
                                .setCredentials(registryLogin, registryPassword)
                                .uploadCertificate(certificate)
                                .ok()
                )
                .editRegistry(testingRegistry, registry ->
                        registry.permissions()
                                .addNewUser(user.login)
                                .closeAll()
                );
        givePermissions(user,
                RegistryPermission.allow(READ, testingRegistry),
                RegistryPermission.allow(WRITE, testingRegistry),
                RegistryPermission.allow(EXECUTE, testingRegistry)
        );
        logout();
        loginAs(user)
                .tools()
                .editRegistry(testingRegistry, registry ->
                        registry.ensureVisible(INFO, PATH, DESCRIPTION, DELETE, CANCEL, SAVE)
                                .ensureNotVisible(EDIT_CREDENTIALS)
                                .cancel()
                );
        logout();
        loginAs(admin);
        deleteTestingRegistry();
    }

    @Test(dependsOnMethods = {"checkEditRegistryAccessForNonAdminUser"})
    @TestCase(value = "EPMCMBIBPC-682")
    public void checkErrorOnInvalidCredentials() {
        final String userName = "invalid_login";
        final String password = "invalid_password";
        tools()
                .addRegistry(registry ->
                        registry.setPath(registryPath)
                                .setDescription(testingRegistry)
                                .openCredentials()
                                .setCredentials(userName, password)
                                .uploadCertificate(certificate)
                                .ok()
                )
                .messageShouldAppear(String.format("Failed to authenticate into docker registry '%s' with user name '%s'", registryPath, userName));
    }

    @Test(dependsOnMethods = {"checkErrorOnInvalidCredentials"})
    @TestCase(value = "EPMCMBIBPC-683")
    public void checkBehaviorIfUserSetValidCredentials() {
        tools()
                .addRegistry(registry ->
                        registry.setPath(registryPath)
                                .setDescription(testingRegistry)
                                .openCredentials()
                                .setCredentials(registryLogin, registryPassword)
                                .uploadCertificate(certificate)
                                .ok()
                )
                .messageShouldAppear(String.format("Adding registry %s...", testingRegistry));
    }

    @Test(dependsOnMethods = {"checkBehaviorIfUserSetValidCredentials"})
    @TestCase(value = "EPMCMBIBPC-961")
    public void validateAddNonExistingRegistry() {
        tools()
                .addRegistry(registry ->
                        registry.setPath(invalidRegistryPath)
                                .setDescription(invalidRegistryDescription)
                                .openCredentials()
                                .uploadCertificate(certificate)
                                .ok()
                )
                .messageShouldAppear(String.format("Failed to connect to docker registry '%s'. Error: 'I/O error on GET request", invalidRegistryPath), 35_000)
                .refresh()
                .click(REGISTRY).sleep(1, SECONDS).ensure(REGISTRIES_LIST, not(text(invalidRegistryDescription)));
    }
}
