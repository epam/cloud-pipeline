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

import com.epam.pipeline.autotests.ao.Template;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.FolderHandling;
import com.epam.pipeline.autotests.mixins.StorageHandling;
import com.epam.pipeline.autotests.utils.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byText;
import static com.epam.pipeline.autotests.ao.PathAdditionDialogAO.switcher;
import static com.epam.pipeline.autotests.utils.Privilege.EXECUTE;
import static com.epam.pipeline.autotests.utils.Privilege.READ;

public class LaunchParametersRoleModelTest
        extends AbstractAutoRemovingPipelineRunningTest
        implements Authorization, StorageHandling, FolderHandling {

    private final String shellTemplate = "/fileKeeper.sh";
    private final String storage = "storage-" + Utils.randomSuffix();
    private final String folder = "folder-" + Utils.randomSuffix();
    private final String securedStorage = "secured-storage-" + Utils.randomSuffix();
    private final String securedFolder = "secured-folder-" + Utils.randomSuffix();

    @BeforeClass
    public void initEnvironment() {
        createFolder(folder)
                .createStorage(storage);

        addAccountToFolderPermissions(user, folder);
        addAccountToStoragePermissions(user, storage, folder);

        givePermissions(user,
                FolderPermission.allow(READ, folder),
                BucketPermission.allow(READ, storage, folder)
        );

        createFolder(securedFolder)
                .createStorage(securedStorage);
    }

    @BeforeClass
    public void createPipeline() {
        navigationMenu()
                .createPipeline(Template.SHELL, getPipelineName())
                .firstVersion()
                .codeTab()
                .clearAndFillPipelineFile(
                        getPipelineName().toLowerCase() + ".sh",
                        Utils.readResourceFully(shellTemplate)
                );

        addAccountToPipelinePermissions(user, getPipelineName());

        givePermissions(user,
                PipelinePermission.allow(EXECUTE, getPipelineName())
        );
    }

    @AfterClass(alwaysRun = true)
    public void deleteStorages() {
        loginAsAdminAndPerform(() -> {
            removeStorage(storage, folder);
            removeStorage(securedStorage, securedFolder);
            removeFolder(folder);
            removeFolder(securedFolder);
        });
    }

    @Override
    @AfterClass(alwaysRun = true, enabled = false)
    void removeNode() {
        loginAsAdminAndPerform(super::removeNode);
    }

    @Override
    @AfterClass(alwaysRun = true, enabled = false)
    void stopRun() {
        loginAsAdminAndPerform(super::stopRun);
    }

    @Override
    @AfterClass(alwaysRun = true, enabled = false)
    public void removePipeline() {
        loginAsAdminAndPerform(super::removePipeline);
    }

    @Test
    @TestCase({"EPMCMBIBPC-931"})
    public void userShouldSeeOnlyElementsThatHeHasAccessTo() {
        logout();
        loginAs(user)
                .library()
                .clickOnPipeline(getPipelineName())
                .firstVersion()
                .runPipeline()
                .clickAddOutputParameter()
                .setName("parameter")
                .openPathAdditionDialog()
                .ensure(byText(folder), visible)
                .performIf(switcher(folder).exists(), switcher -> switcher.openFolder(folder))
                .ensure(byText(storage), visible)
                .ensure(byText(securedFolder), not(visible));
    }
}
