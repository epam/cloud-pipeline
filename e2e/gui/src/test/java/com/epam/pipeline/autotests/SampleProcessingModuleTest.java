/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.autotests.ao.MetadataSamplesAO;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.text.SimpleDateFormat;
import java.util.Date;

import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byText;
import static com.epam.pipeline.autotests.utils.Utils.getFile;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;

public class SampleProcessingModuleTest extends AbstractBfxPipelineTest implements Navigation {
    private final String folder = "sampleProcModuleTest-" + Utils.randomSuffix();
    private final String file1 = "3-samples.csv";
    private final String file2 = "3-samples-set.csv";
    private final String file3 = "1-sample.csv";
    private final String metadataFolder = "Metadata";
    private final String sampleSetFolder = "SampleSet";
    private final String sampleFolder = "Sample";
    private final String createDateField = "Created Date";
    private final String instanceID = "instanceID";
    private final String key1 = "key1";
    private final String value1 = "value1";
    private String errorMessage = "Metadata entity with external id '%s' already exists.";

    @AfterClass(alwaysRun = true)
    public void removeEntities() {
        library()
                .removeFolder(folder);
    }

    @Test
    @TestCase(value = {"1589"})
    public void checkTheCreatedDateFieldForMetadataEntities() {
        String currentDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        library()
                .createFolder(folder)
                .clickOnFolder(folder)
                .uploadMetadata(getFile(file1))
                .sleep(1, SECONDS)
                .uploadMetadata(getFile(file2))
                .ensure(byText(metadataFolder), visible);
        String instanceDate = library()
                .cd(folder)
                .cd(metadataFolder)
                .metadataSamples(format("%s [", sampleSetFolder))
                .validateFields("ID", createDateField, "Name", "Samples")
                .performForEachRow(sampleRow ->
                        sampleRow.ensureCell(createDateField, text(currentDate)))
                .returnToMetadata()
                .metadataSamples(format("%s [", sampleFolder))
                .validateFields("ID", createDateField, "SampleName")
                .performForEachRow(sampleRow ->
                        sampleRow.ensureCell(createDateField, text(currentDate)))
                .addInstance(instanceID)
                .performForEachRow(sampleRow ->
                        sampleRow.ensureCell(createDateField, text(currentDate)))
                .getRowByCellValue(instanceID)
                .getCell(createDateField)
                .getCellContent();
        new MetadataSamplesAO()
                .getRowByCellValue(instanceID)
                .clickOnRow()
                .addKeyWithValue(key1, value1);
        new MetadataSamplesAO()
                .validateFields("ID", createDateField, key1, "SampleName")
                .getRowByCellValue(instanceID)
                .getCell(createDateField)
                .ensureCellContains(instanceDate);
    }

    @Test(dependsOnMethods = "checkTheCreatedDateFieldForMetadataEntities")
    @TestCase(value = {"1613"})
    public void checkAnErrorOnDuplicatedMetadataEntitiesIDs() {
        String id = library()
                .cd(folder)
                .cd(metadataFolder)
                .metadataSamples(format("%s [", sampleFolder))
                .getRow(1)
                .getCell("ID")
                .getCellContent();
        new MetadataSamplesAO()
                .addInstance(id)
                .messageShouldAppear(format(errorMessage, id))
                .ensure(byClassName("ant-modal-title"), visible)
                .cancelAddInstance();
    }

    @Test(dependsOnMethods = "checkTheCreatedDateFieldForMetadataEntities")
    @TestCase(value = {"1569"})
    public void checkTheMetadataEntitiesIDsAutogeneration() {
        String sampleName = "NA12878_D702_4";
        library()
                .clickOnFolder(folder)
                .uploadMetadata(getFile(file3))
                .ensure(byText(metadataFolder), visible);
        library()
                .cd(folder)
                .cd(metadataFolder)
                .metadataSamples(format("%s [", sampleFolder))
                .performForEachRow(sampleRow ->
                        sampleRow.ensureCell("ID", exist))
                .addInstanceWithValue("SampleName", sampleName)
                .getRowByCellValue(sampleName)
                .ensureCell("ID", exist);
    }
}
