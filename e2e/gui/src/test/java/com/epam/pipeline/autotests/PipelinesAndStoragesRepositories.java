/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.codeborne.selenide.Condition;
import com.epam.pipeline.autotests.ao.Template;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.utils.BucketPermission;
import com.epam.pipeline.autotests.utils.PipelinePermission;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.epam.pipeline.autotests.ao.Primitive.ALL_PIPELINES;
import static com.epam.pipeline.autotests.ao.Primitive.ALL_STORAGES;
import static com.epam.pipeline.autotests.utils.Privilege.EXECUTE;
import static com.epam.pipeline.autotests.utils.Privilege.READ;
import static com.epam.pipeline.autotests.utils.Privilege.WRITE;

public class PipelinesAndStoragesRepositories
        extends AbstractSeveralPipelineRunningTest
        implements Authorization {
    final String folder = "pipeStorRepositories-folder-" + Utils.randomSuffix();
    final String pipeline1 = "pipeStorRepositories-pipeline-" + Utils.randomSuffix();
    final String pipeline2 = "pipeStorRepositories-pipeline-" + Utils.randomSuffix();
    final String pipeline3= "pipeStorRepositories-pipeline-" + Utils.randomSuffix();
    final String storage1 = "pipeStorRepositories-storage-" + Utils.randomSuffix();
    final String storage2 = "pipeStorRepositories-storage-" + Utils.randomSuffix();
    final String fsMount = "pipeStorRepositories-fsMount-" + Utils.randomSuffix();
    final String file1 = "pipeStorRepositories-file-" + Utils.randomSuffix();
    final String file2 = "pipeStorRepositories-file-" + Utils.randomSuffix();

    @BeforeClass
    public void createEntities() {
        library()
                .createFolder(folder);
    }

    @AfterClass
    public void removeEntities() {
        logoutIfNeeded();
        loginAs(admin);
        library()
                .removeNotEmptyFolder(folder);
    }

    @Test
    @TestCase("1506_1")
    public void checkAllPipelinesRepository() {
        logoutIfNeeded();
        loginAs(admin);
        library()
                .cd(folder)
                .createPipeline(Template.SHELL, pipeline1);
        addAccountToPipelinePermissions(user, pipeline1);
        givePermissions(user,
                PipelinePermission.allow(READ, pipeline1),
                PipelinePermission.allow(EXECUTE, pipeline1),
                PipelinePermission.allow(WRITE, pipeline1)
        );
        library()
                .cd(folder)
                .createPipeline(Template.SHELL, pipeline2);
        addAccountToPipelinePermissions(user, pipeline2);
        givePermissions(user,
                PipelinePermission.allow(READ, pipeline2)
        );
        library()
                .cd(folder)
                .createPipeline(Template.SHELL, pipeline3);
        logout();
        loginAs(user);
        library()
                .ensure(ALL_PIPELINES, Condition.visible)
                .click(ALL_PIPELINES)
                .ensurePipelineOrStorageIsPresentInTable(pipeline1)
                .ensurePipelineOrStorageIsPresentInTable(pipeline2)
                .ensurePipelineOrStorageIsNotPresentInTable(pipeline3)
                .openPipelineFromTable(pipeline1)
                .assertPipelineName(pipeline1);
        library()
                .click(ALL_PIPELINES)
                .runPipelineFromTable(pipeline1)
                .checkLaunchItemName(pipeline1)
                .ensureLaunchButtonIsVisible();
        library()
                .click(ALL_PIPELINES)
                .openPipelineFromTable(pipeline2)
                .assertPipelineName(pipeline2)
                .assertRunButtonIsNotDisplayed();
        logout();
    }

    @Test
    @TestCase("1506_2")
    public void checkAllStoragesRepository() {
        logoutIfNeeded();
        loginAs(admin);
        library()
                .cd(folder)
                .createStorage(storage1);
        addAccountToStoragePermissions(user, storage1);
        givePermissions(user,
                BucketPermission.allow(READ, storage1)
        );
        library()
                .cd(folder)
                .selectStorage(storage1)
                .createFile(file1);
        library()
                .cd(folder)
                .createStorage(storage2)
                .createNfsMount("/" + fsMount, fsMount)
                .selectStorage(fsMount);
        addAccountToStoragePermissions(user, fsMount);
        givePermissions(user,
                BucketPermission.allow(READ, fsMount)
        );
        library()
                .cd(folder)
                .selectStorage(fsMount)
                .createFile(file2);
        logout();
        loginAs(user);
        library()
                .ensure(ALL_STORAGES, Condition.visible)
                .click(ALL_STORAGES)
                .ensurePipelineOrStorageIsPresentInTable(storage1)
                .ensurePipelineOrStorageIsNotPresentInTable(storage2)
                .ensurePipelineOrStorageIsPresentInTable(fsMount)
                .openStorageFromTable(storage1)
                .validateHeader(storage1);
        library()
                .click(ALL_STORAGES)
                .openStorageFromTable(fsMount)
                .validateHeader(fsMount);
    }
}
