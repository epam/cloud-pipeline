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

import com.epam.pipeline.autotests.ao.DocumentTabAO;
import com.epam.pipeline.autotests.ao.Template;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.Test;
import static com.epam.pipeline.autotests.ao.Primitive.CLOSE;
import static com.epam.pipeline.autotests.ao.Primitive.CREATE_FOLDER;
import static com.epam.pipeline.autotests.ao.Primitive.DELETE;
import static com.epam.pipeline.autotests.ao.Primitive.DOWNLOAD;
import static com.epam.pipeline.autotests.ao.Primitive.EDIT;
import static com.epam.pipeline.autotests.ao.Primitive.NEW_FILE;
import static com.epam.pipeline.autotests.ao.Primitive.RENAME;
import static com.epam.pipeline.autotests.ao.Primitive.UPLOAD;

public class PipelineReleaseTest extends AbstractAutoRemovingPipelineRunningTest {

    private static final String version = "version-1";

    @Test
    @TestCase({"EPMCMBIBPC-715"})
    public void validatePipelineRelease() {
        library()
                .createPipeline(Template.SHELL, getPipelineName())
                .clickOnPipeline(getPipelineName())
                .firstVersion()
                .codeTab()
                .ensureVisible(RENAME, DELETE);

        library()
                .clickOnPipeline(getPipelineName())
                .releaseFirstVersion(version)
                .version(version)
                .codeTab()
                .ensureVisible(CREATE_FOLDER, NEW_FILE, UPLOAD, RENAME, DELETE)
                .clickOnFile(Utils.getFileNameFromPipelineName(getPipelineName(), "sh"))
                .ensureVisible(EDIT, CLOSE)
                .close()
                .onTab(DocumentTabAO.class)
                .ensureVisible(UPLOAD, RENAME, DELETE, DOWNLOAD);
    }
}
