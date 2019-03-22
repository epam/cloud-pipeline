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

import com.epam.pipeline.autotests.ao.ShellAO;
import com.epam.pipeline.autotests.mixins.Tools;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static com.codeborne.selenide.Selenide.open;

public class SshShellTest extends AbstractSinglePipelineRunningTest implements Tools {
    private final String tool = C.TESTING_TOOL_NAME;
    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private String sshLink;

    @BeforeClass
    @AfterClass(alwaysRun = true)
    public void fallBackToDefaultToolSettings() {
        open(C.ROOT_ADDRESS);
        fallbackToToolDefaultState(registry, group, tool);
    }

    @Test
    @TestCase({"EPMCMBIBPC-583"})
    public void sshLinkIsAvailable() {
        tools().perform(registry, group, tool, tool ->
                tool.settings().run(this)
        );
        saveSshLink();
    }

    @Test(dependsOnMethods = {"sshLinkIsAvailable"})
    @TestCase({"EPMCMBIBPC-584"})
    public void checkSshLink() {
        ShellAO.open(getSshLink())
                .assertPageContains(String.format("pipeline-%s", getRunId()));
    }

    @Test(dependsOnMethods = {"checkSshLink"})
    @TestCase({"EPMCMBIBPC-585"})
    public void checkShellFunctionality() {
        shell().execute("cd /home")
                .execute("mkdir test")
                .execute("ls")
                .assertOutputContains("test");
    }

    private ShellAO shell() {
        return new ShellAO();
    }

    private String getSshLink() {
        return sshLink;
    }

    private void saveSshLink() {
        sshLink =
                navigationMenu()
                        .runs()
                        .activeRuns()
                        .showLog(getRunId())
                        .waitForSshLink()
                        .getSshLink();
    }
}
