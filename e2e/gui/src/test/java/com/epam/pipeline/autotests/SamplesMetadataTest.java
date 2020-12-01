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

import com.epam.pipeline.autotests.ao.*;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.SelenideElements;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.openqa.selenium.By;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static com.codeborne.selenide.CollectionCondition.sizeGreaterThanOrEqual;
import static com.codeborne.selenide.CollectionCondition.sizeLessThanOrEqual;
import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selenide.*;
import static com.epam.pipeline.autotests.ao.Configuration.*;
import static com.epam.pipeline.autotests.ao.Configuration.name;
import static com.epam.pipeline.autotests.ao.Configuration.title;
import static com.epam.pipeline.autotests.ao.DetachedConfigurationCreationPopup.templatesList;
import static com.epam.pipeline.autotests.ao.MetadataKeyAO.numberOfSamples;
import static com.epam.pipeline.autotests.ao.MetadataKeyAO.samplesForKey;
import static com.epam.pipeline.autotests.ao.MetadataSamplesAO.*;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.ao.Profile.*;
import static com.epam.pipeline.autotests.ao.RunsMenuAO.runOf;
import static com.epam.pipeline.autotests.utils.Conditions.*;
import static com.epam.pipeline.autotests.utils.Conditions.contains;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.*;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public class SamplesMetadataTest
        extends AbstractSeveralPipelineRunningTest {

    private final String defaultRegistryId = C.DEFAULT_REGISTRY_IP;
    private final String testingTool = C.TESTING_TOOL_NAME;
    private final String dockerImage = String.format("%s/%s", defaultRegistryId, testingTool);
    private final String dataStorage = "samples-metadata-" + Utils.randomSuffix();
    private final String fastq = "fastq";
    private final String subfolder1 = "3_replicates_of_NA12878";
    private final String subfolder2 = "11_replicates_of_NA12878";
    private final String human = "human";
    private final String bed = "gencode.v27.genes.bed";

    private final String sampleFolder = "Sample [14]";
    private final String sampleSetFolder = "SampleSet";
    private final String metadataFolder = "Metadata";

    private final String wes3repsamples = "wes-3-rep-samples.csv";
    private final String wes3repset = "wes-3-rep-set.csv";
    private final String wes11repsamples = "wes-11-rep-samples.csv";
    private final String wes11repset = "wes-11-rep-set.csv";
    private final String reference = "reference";
    private final String bwa = "bwa";
    private final String project = "samples-metadata-project-" + Utils.randomSuffix();
    private final String pipeline = "samplesMetadataPipeline" + Utils.randomSuffix();

    private final String instanceDisk = "20";
    private final String instanceType = C.DEFAULT_INSTANCE;
    private final String priceType = C.DEFAULT_INSTANCE_PRICE_TYPE;

    private final String configJson = "/sample-metadata-config.json";
    private final String launchScript = "singlesamplevariantcalling.sh";

    private final String type = "type";
    private final String grch38bwa = "GRCh38_BWA";
    private final String exomePanel = "Exome_Panel";
    private final String projectOutput = "Project_Output";
    private final String referenceGenomePath = "REFERENCE_GENOME_PATH";
    private final String panel = "PANEL";
    private final String fastqR1 = "FASTQ_R1";
    private final String fastqR2 = "FASTQ_R2";
    private final String sampleName = "SAMPLE_NAME";
    private final String resultDir = "RESULT_DIR";
    private final String cpCapNfs = "CP_CAP_NFS";

    private final String idField = "ID";
    private final String nameField = "Name";
    private final String samplesField = "Samples";
    private final String createDateField = "Created Date";

    private final String fastqR1D710 = "NA12878_D710_L001_R1_001.fastq.gz";
    private final String fastqR1D711 = "NA12878_D711_L001_R1_001.fastq.gz";
    private final String fastqR1D712 = "NA12878_D712_L001_R1_001.fastq.gz";
    private final String fastqR2D710 = "NA12878_D710_L001_R2_001.fastq.gz";
    private final String fastqR2D711 = "NA12878_D711_L001_R2_001.fastq.gz";
    private final String fastqR2D712 = "NA12878_D712_L001_R2_001.fastq.gz";

    private final String[] subfolder1Files = {
            fastqR1D710,
            fastqR1D711,
            fastqR1D712,
            fastqR2D710,
            fastqR2D711,
            fastqR2D712
    };
    private final String[] subfolder2Files = {
            "NA12878_D702_L001_R1_001.fastq.gz",
            "NA12878_D703_L001_R1_001.fastq.gz",
            "NA12878_D704_L001_R1_001.fastq.gz",
            "NA12878_D705_L001_R1_001.fastq.gz",
            "NA12878_D706_L001_R1_001.fastq.gz",
            "NA12878_D707_L001_R1_001.fastq.gz",
            "NA12878_D708_L001_R1_001.fastq.gz",
            "NA12878_D709_L001_R1_001.fastq.gz",
            "NA12878_D710_L001_R1_001.fastq.gz",
            "NA12878_D711_L001_R1_001.fastq.gz",
            "NA12878_D712_L001_R1_001.fastq.gz",
            "NA12878_D702_L001_R2_001.fastq.gz",
            "NA12878_D703_L001_R2_001.fastq.gz",
            "NA12878_D704_L001_R2_001.fastq.gz",
            "NA12878_D705_L001_R2_001.fastq.gz",
            "NA12878_D706_L001_R2_001.fastq.gz",
            "NA12878_D707_L001_R2_001.fastq.gz",
            "NA12878_D708_L001_R2_001.fastq.gz",
            "NA12878_D709_L001_R2_001.fastq.gz",
            "NA12878_D710_L001_R2_001.fastq.gz",
            "NA12878_D711_L001_R2_001.fastq.gz",
            "NA12878_D712_L001_R2_001.fastq.gz"
    };

    private final String configuration = "samples-configuration-" + Utils.randomSuffix();
    private final String rootEntityTypeSample = "Sample";
    private final String rootEntityTypeSampleSet = "SampleSet";

    private List<String> selectedSampleName = new ArrayList<>();

    @AfterClass(alwaysRun = true)
    public void cleanUp() {
        open(C.ROOT_ADDRESS);
        library()
                .removePipeline(pipeline)
                .removeStorage(dataStorage)
                .cd(project)
                .removeConfiguration(configuration)
                .removeFolder(project);
    }

    @Test
    @TestCase({"EPMCMBIBPC-1519"})
    public void testDataPreparation() {
        library()
                .createStorage(dataStorage)
                .selectStorage(dataStorage)
                .createFolder(fastq)
                .cd(fastq)
                .createFolder(subfolder1)
                .createFolder(subfolder2)
                .cd(subfolder1)
                .also(loadFiles(subfolder1Files))
                .cd("..")
                .cd(subfolder2)
                .also(loadFiles(subfolder2Files))
                .cd("..")
                .cd("..")
                .createFolder(human)
                .cd(human)
                .also(loadFiles(bed))
                .cd("..")
                .createFolder(reference)
                .cd(reference)
                .createFolder(bwa);
    }

    @Test
    @TestCase({"EPMCMBIBPC-1411"})
    public void createProjectFolder() {
        refresh();
        library()
                .createFolder(project)
                .clickOnFolder(project)
                .showMetadata()
                .addKeyWithValue("type", "project");
    }

    @Test(priority = 1, dependsOnMethods = {"testDataPreparation", "createProjectFolder"})
    @TestCase({"EPMCMBIBPC-1520"})
    public void setProjectParameters() {
        library()
                .clickOnFolder(project);

        metadataContent()
                .addKeyWithValue(exomePanel, path(dataStorage, human, bed))
                .addKeyWithValue(grch38bwa, path(dataStorage, reference, bwa))
                .addKeyWithValue(projectOutput, path(dataStorage, "${RUN_ID}"));
    }

    @Test(priority = 1, dependsOnMethods = {"setProjectParameters"})
    @TestCase({"EPMCMBIBPC-1412"})
    public void metadataUploading() {
        library()
                .clickOnFolder(project)
                .uploadMetadata(getFile(wes3repsamples))
                .uploadMetadata(getFile(wes3repset))
                .uploadMetadata(getFile(wes11repsamples))
                .uploadMetadata(getFile(wes11repset))
                .sleep(2, SECONDS)
                .ensure(byText(metadataFolder), visible);
    }

    @Test(priority = 1, dependsOnMethods = {"metadataUploading"})
    @TestCase({"EPMCMBIBPC-1570"})
    public void metadataEditAndPrepare() {
        final String nonExistingPath = "prefix://unexisting-storage-path";
        library()
                .cd(project)
                .cd(metadataFolder)
                .metadataSamples(sampleFolder)
                .performIf(hideMetadata, visible, ms -> ms.click(hideMetadata))
                .click(columnHeader(idField))
                .sleep(5, SECONDS)
                .performForEachRow(row -> {

                            sleep(500, MILLISECONDS);

                            final MetadataSectionAO metadataSection = row.clickOnRow();

                            final MetadataKeyAO r1key = metadataSection.selectKey("R1_Fastq");
                            final String r1value = r1key.getValue();
                            r1key.changeValue(r1value.replace(nonExistingPath, path(dataStorage)));

                            sleep(2, SECONDS);

                            final MetadataKeyAO r2key = metadataSection.selectKey("R2_Fastq");
                            final String r2value = r2key.getValue();
                            r2key.changeValue(r2value.replace(nonExistingPath, path(dataStorage)))
                                    .close();

                            sleep(500, MILLISECONDS);

                            row.getCell("R1_Fastq")
                                    .ensureCellContainsHyperlink();
                            row.getCell("R2_Fastq")
                                    .ensureCellContainsHyperlink();
                        }
                );
    }

    @Test(priority = 1, dependsOnMethods = {"metadataUploading"})
    @TestCase({"EPMCMBIBPC-1413"})
    public void viewMetadata() {
        library()
                .cd(project)
                .cd(metadataFolder)
                .metadataSamples(sampleSetFolder, metadata -> {
                            metadata.validateFields(idField, createDateField, nameField, samplesField)
                                    .getRow(1)
                                    .clickOnRow()
                                    .assertKeysArePresent(idField, nameField, samplesField);
                            metadata
                                    .getRowByCellValue("NA12878_11_rep")
                                    .getCell(samplesField)
                                    .clickOnHyperlink()
                                    .assertKeyIsPresent(samplesField)
                                    .assertKeysAreNotPresent(idField, nameField)
                                    .ensure(samplesForKey(samplesField), numberOfSamples(11));
                            libraryContent()
                                    .metadataSamples(sampleFolder)
                                    .validateFields("ID", createDateField, "R1_Fastq", "R2_Fastq", "SampleName");
                        }
                );
    }

    @Test(priority = 1, dependsOnMethods = {"metadataUploading"})
    @TestCase({"EPMCMBIBPC-1414"})
    public void navigateToStorageFromMetadataEntity() {
        library()
                .cd(project)
                .cd(metadataFolder)
                .metadataSamples(sampleFolder)
                .getRow(1)
                .getCell("R1_Fastq")
                .clickOnHyperlink();
        storageContent()
                .ensure(ADDRESS_BAR, text(path(dataStorage)));
    }

    @Test(priority = 1, dependsOnMethods = {"metadataUploading"})
    @TestCase({"EPMCMBIBPC-1415"})
    public void sortMetadataTable() {
        final By idHeader = columnHeader(idField);
        library()
                .cd(project)
                .cd(metadataFolder)
                .metadataSamples(sampleFolder)
                .click(idHeader)
                .ensure(idHeader, contains(decreaseOrderIcon))
                .validateSortedByDecrease(idField)
                .click(idHeader)
                .ensure(idHeader, contains(increaseOrderIcon))
                .validateSortedByIncrease(idField)
                .click(idHeader)
                .ensure(idHeader, not(contain(increaseOrderIcon)), not(contain(decreaseOrderIcon)));
    }

    @Test(priority = 1, dependsOnMethods = {"metadataUploading"})
    @TestCase({"EPMCMBIBPC-1416"})
    public void searchByMetadataAttributeValues() {
        final String substring = "D70";
        library()
                .cd(project)
                .cd(metadataFolder)
                .metadataSamples(sampleFolder)
                .setValue(searchMetadata, substring)
                .also($(searchMetadata)::pressEnter)
                .ensureNumberOfRowsIs(8)
                .performForEachRow(row ->
                        row.getCell(idField)
                                .ensureCellContains(substring)
                );
    }

    @Test(priority = 1, dependsOnMethods = {"metadataUploading"})
    @TestCase({"EPMCMBIBPC-1507", "EPMCMBIBPC-1508"})
    public void columnsListCustomization() {
        library()
                .cd(project)
                .cd(metadataFolder)
                .metadataSamples(sampleFolder)
                .showColumnsMenu()
                .clickCheckboxForField(idField)
                .close()
                .ensure(columnHeader(idField), not(visible))
                .showColumnsMenu()
                .clickCheckboxForField(idField)
                .close()
                .ensure(columnHeader(idField), visible);
    }

    @Test(priority = 2, dependsOnMethods = {"createProjectFolder", "metadataUploading"})
    @TestCase({"EPMCMBIBPC-1588"})
    public void addDetachedConfigurationToProjectFolder() {
        library()
                .cd(project)
                .createConfiguration(configuration)
                .configurationWithin(configuration, profile ->
                        profile.ensure(title(), text(configuration))
                                .ensure(activeProfileTab(), text("default"))
                                .ensure(add(), visible)
                                .ensure(name(), visible)
                                .ensure(run(), visible)
                                .ensure(save(), visible)
                                .ensure(execEnvironmentTab, expandedTab)
                                .ensure(pipeline(), visible)
                                .ensure(image(), visible)
                                .ensure(instanceType(), visible)
                                .ensure(disk(), visible)
                                .ensure(advancedTab, collapsedTab)
                                .ensure(parametersTab, expandedTab)
                                .ensure(rootEntityType(), visible)
                                .ensure(addParameter(), visible)
                );
    }

    @Test(priority = 2, dependsOnMethods = {"addDetachedConfigurationToProjectFolder"})
    @TestCase({"EPMCMBIBPC-1591"})
    public void preparePipeline() {
        library()
                .createPipeline(Template.SHELL, pipeline)
                .clickOnPipeline(pipeline)
                .firstVersion()
                .codeTab()
                .clickOnFile("config.json")
                .editFile(configuration -> {
                    final boolean isSpot = Objects.equals(priceType, "Spot");
                    return Utils.readResourceFully(configJson)
                            .replace("{{docker_image}}", dockerImage)
                            .replace("{{instance_type}}", C.DEFAULT_INSTANCE)
                            .replace("{{is_spot}}", String.valueOf(isSpot));
                })
                .deleteExtraBrackets(110)
                .saveAndCommitWithMessage("test: sample metadata")
                .click(DELETE)
                .click(button("OK"))
                .uploadFile(getFile(launchScript))
                .ensure(byText(launchScript), visible);
        sleep(20, SECONDS);
        refresh();
        library()
                .cd(project)
                .configurationWithin(configuration, profile ->
                        profile.expandTabs(execEnvironmentTab, advancedTab, parametersTab)
                                .selectPipeline(pipeline)
                                .click(save())
                                .ensure(pipeline(), valueContains(pipeline))
                                .ensure(image(), not(empty))
                                .ensure(instanceType(), text(instanceType))
                                .ensure(comboboxOf(priceType()), text(priceType))
                                .ensure(disk(), value(instanceDisk))
                                .validateParameters(
                                        referenceGenomePath,
                                        panel,
                                        fastqR1,
                                        fastqR2,
                                        sampleName,
                                        resultDir
                                )
                );
    }

    @Test(priority = 2, dependsOnMethods = {"preparePipeline"})
    @TestCase({"EPMCMBIBPC-1593"})
    public void validateSetParametersFromProjectMetadata() {
        library()
                .cd(project)
                .configurationWithin(configuration, profile ->
                        profile.expandTabs(parametersTab)
                                .resetMouse()
                                .validateParameters(referenceGenomePath)
                                .addToParameter(referenceGenomePath, "project.")
                                .setParameter(referenceGenomePath, "project.")
                                .ensure(
                                        templatesList(),
                                        contains(
                                                byText(type),
                                                byText(grch38bwa),
                                                byText(exomePanel),
                                                byText(projectOutput)
                                        )
                                )
                                .click(byText(grch38bwa), in(comboboxDropdown()))
                                .setParameter(panel, "project.")
                                .ensure(
                                        templatesList(),
                                        contains(
                                                byText(type),
                                                byText(grch38bwa),
                                                byText(exomePanel),
                                                byText(projectOutput)
                                        )
                                )
                                .click(byText(exomePanel), in(comboboxDropdown()))
                                .setParameter(resultDir, "project.")
                                .ensure(
                                        templatesList(),
                                        contains(
                                                byText(type),
                                                byText(grch38bwa),
                                                byText(exomePanel),
                                                byText(projectOutput)
                                        )
                                )
                                .click(byText(projectOutput), in(comboboxDropdown()))
                                .click(save())
                );
    }

    @Test(priority = 2, dependsOnMethods = {"validateSetParametersFromProjectMetadata"})
    @TestCase({"EPMCMBIBPC-1594", "EPMCMBIBPC-1623"})
    public void validateEntitySelection() {
        library()
                .cd(project)
                .configurationWithin(configuration, profile ->
                        profile.expandTabs(parametersTab)
                                .selectValue(rootEntityType(), rootEntityTypeSample)
                                .ensure(rootEntityType(), text(rootEntityTypeSample))
                                .selectValue(rootEntityType(), rootEntityTypeSampleSet)
                                .ensure(rootEntityType(), text(rootEntityTypeSampleSet))
                );
    }

    @Test(priority = 3, dependsOnMethods = {"addDetachedConfigurationToProjectFolder"})
    @TestCase({"EPMCMBIBPC-1595"})
    public void setParametersFromMetadataFiles() {
        final String fastR1Autocomplete = "R1_Fastq";
        final String fastR2Autocomplete = "R2_Fastq";
        final String sampleNameAutocomplete = "SampleName";

        library()
                .cd(project)
                .configurationWithin(configuration, profile ->
                        profile.selectValue(rootEntityType(), rootEntityTypeSample)
                                .addToParameter(fastqR1, "this.")
                                .ensure(
                                        templatesList(),
                                        contains(
                                                byText(fastR1Autocomplete),
                                                byText(fastR2Autocomplete),
                                                byText(sampleNameAutocomplete)
                                        )
                                )
                                .click(byText(fastR1Autocomplete), in(comboboxDropdown()))
                                .addToParameter(fastqR2, "this.")
                                .ensure(
                                        templatesList(),
                                        contains(
                                                byText(fastR1Autocomplete),
                                                byText(fastR2Autocomplete),
                                                byText(sampleNameAutocomplete)
                                        )
                                ).click(byText(fastR2Autocomplete), in(comboboxDropdown()))
                                .addToParameter(sampleName, "this.")
                                .ensure(
                                        templatesList(),
                                        contains(
                                                byText(fastR1Autocomplete),
                                                byText(fastR2Autocomplete),
                                                byText(sampleNameAutocomplete)
                                        )
                                ).click(byText(sampleNameAutocomplete), in(comboboxDropdown()))
                                .click(save())
                                .sleep(1, SECONDS)
                );
    }

    @Test(priority = 4, dependsOnMethods = {"addDetachedConfigurationToProjectFolder", "setParametersFromMetadataFiles"})
    @TestCase({"EPMCMBIBPC-1596"})
    public void selectMetadataPopup() {
        library()
                .cd(project)
                .configuration(configuration, profile -> profile.click(run(), MetadataSelection::new))
                .ensure(byText(project), visible)
                .ensure(byText(metadataFolder), visible)
                .ensure(MetadataSelection.header, text(project))
                .ensure(MetadataSelection.folders, text(metadataFolder))
                .ensure(MetadataSelection.defineExpression, visible)
                .ensure(MetadataSelection.clearSelection, visible)
                .ensure(MetadataSelection.cancel, visible)
                .ensure(MetadataSelection.ok, visible)
                .cd(metadataFolder)
                .ensure(byText("SampleSet"), visible)
                .ensure(byText("Sample"), visible)
                .cd("Sample")
                .also(ensureSamplesCountIs(subfolder1Files.length / 2 + subfolder2Files.length / 2));
    }

    @Test(priority = 4, dependsOnMethods = {"selectMetadataPopup"})
    @TestCase({"EPMCMBIBPC-1599"})
    public void runPerSampleValidation() {
        metadataSelection()
                .samples(samples -> {
                    samples.selectRows(1, 2, 3).performForEachRow(sampleRow ->
                            selectedSampleName.add(sampleRow.getCell(idField).getCellContent()));
                })
                .launch(this, pipeline)
                .ensureAll(runOf(pipeline), sizeGreaterThanOrEqual(3));
    }

    @Test(priority = 4, dependsOnMethods = {"runPerSampleValidation"})
    @TestCase({"EPMCMBIBPC-1605"})
    public void validatePipelineParametersForSample() {
        runsMenu()
                .showLog(getLastRunId())
                .expandTab(PARAMETERS)
                .ensureParameterIsPresent(panel, path(dataStorage, human, bed))
                .ensureParameterIsPresent(fastqR2, path(dataStorage))
                .ensureParameterIsPresent(fastqR1, path(dataStorage))
                .ensureOnOfManyParametersIsPresent(sampleName, selectedSampleName)
                .ensureParameterIsPresent(cpCapNfs, "true")
                .ensureParameterIsPresent(referenceGenomePath, path(dataStorage, reference, bwa))
                .ensureParameterIsPresent(resultDir, path(dataStorage, getLastRunId()));
        runsMenu()
                .stopRun(getLastRunId());
    }

    @Test(priority = 5, dependsOnMethods = {"validateSetParametersFromProjectMetadata"})
    @TestCase({"EPMCMBIBPC-1624"})
    public void setParametersFromMetadataFilesSampleSet() {
        final String fastSamplesAutocomplete = "Samples";
        final String fastNameAutocomplete = "Name";
        final String fastR1Autocomplete = "R1_Fastq";
        final String fastR2Autocomplete = "R2_Fastq";
        final String sampleNameAutocomplete = "SampleName";

        library()
                .cd(project)
                .configurationWithin(configuration, profile ->
                        profile.expandTab(parametersTab)
                                .selectValue(rootEntityType(), rootEntityTypeSampleSet)
                                .setParameter(fastqR1, "this.")
                                .ensure(
                                        templatesList(),
                                        contains(byText(fastSamplesAutocomplete), byText(fastNameAutocomplete))
                                )
                                .click(byText(fastSamplesAutocomplete), in(comboboxDropdown()))
                                .addToParameter(fastqR1, ".")
                                .ensure(
                                        templatesList(),
                                        contains(
                                                byText(fastR1Autocomplete),
                                                byText(fastR2Autocomplete),
                                                byText(sampleNameAutocomplete))
                                )
                                .click(byText(fastR1Autocomplete), in(comboboxDropdown()))
                                .setParameter(fastqR2, "this.")
                                .ensure(
                                        templatesList(),
                                        contains(byText(fastSamplesAutocomplete), byText(fastNameAutocomplete))
                                )
                                .click(byText(fastSamplesAutocomplete), in(comboboxDropdown()))
                                .addToParameter(fastqR2, ".")
                                .ensure(
                                        templatesList(),
                                        contains(
                                                byText(fastR1Autocomplete),
                                                byText(fastR2Autocomplete),
                                                byText(sampleNameAutocomplete))
                                )
                                .click(byText(fastR2Autocomplete), in(comboboxDropdown()))
                                .setParameter(sampleName, "this.")
                                .click(byText(fastSamplesAutocomplete), in(comboboxDropdown()))
                                .addToParameter(sampleName, ".")
                                .ensure(
                                        templatesList(),
                                        contains(
                                                byText(fastR1Autocomplete),
                                                byText(fastR2Autocomplete),
                                                byText(sampleNameAutocomplete))
                                )
                                .click(byText(sampleNameAutocomplete), in(comboboxDropdown()))
                                .click(save())
                );
    }

    @Test(priority = 5, dependsOnMethods = {"setParametersFromMetadataFilesSampleSet"})
    @TestCase({"EPMCMBIBPC-1625"})
    public void validateSampleSetPopup() {
        library()
                .cd(project)
                .configuration(
                        configuration, profile ->
                                profile.sleep(1, SECONDS)
                                        .click(run(), MetadataSelection::new)
                )
                .ensure(byText(project), visible)
                .cd(project)
                .ensure(byText(metadataFolder), visible)
                .ensure(MetadataSelection.header, text(project))
                .ensure(MetadataSelection.folders, text(metadataFolder))
                .ensure(MetadataSelection.defineExpression, visible)
                .ensure(MetadataSelection.clearSelection, visible)
                .ensure(MetadataSelection.cancel, visible)
                .ensure(MetadataSelection.ok, visible)
                .cd(metadataFolder)
                .ensure(byText("SampleSet"), visible)
                .ensure(byText("Sample"), visible)
                .cd("SampleSet")
                .samples(metadata -> {
                            metadata.ensureNumberOfRowsIs(2)
                                    .getRowByCellValue("NA12878_11_rep")
                                    .ensureCell(idField, text("NA12878_11_rep"))
                                    .ensureCell(nameField, text("11 replicates of NA12878"))
                                    .ensureCell(samplesField, contain(byXpath(String.format(
                                            ".//*[contains(@class, '%s') and text() = '%s']",
                                            "browser__action-link", "11 Sample(s)"
                                    ))));
                            metadata.getRowByCellValue("NA12878_3_rep")
                                    .ensureCell(idField, text("NA12878_3_rep"))
                                    .ensureCell(nameField, text("3 replicates of NA12878"))
                                    .ensureCell(samplesField, contain(byXpath(String.format(
                                            ".//*[contains(@class, '%s') and text() = '%s']",
                                            "browser__action-link", "3 Sample(s)"
                                    ))));
                        }
                )
                .click(MetadataSelection.cancel);
    }

    @Test(priority = 5, dependsOnMethods = {"validateSampleSetPopup", "validatePipelineParametersForSample"})
    @TestCase({"EPMCMBIBPC-1626"})
    public void runSampleSetValidation() {
        library()
                .cd(project)
                .configuration(
                        configuration, profile ->
                                profile.sleep(1, SECONDS)
                                        .click(run(), MetadataSelection::new)
                )
                .cd(project)
                .cd(metadataFolder)
                .cd("SampleSet")
                .samples(samples -> samples.getRowByCellValue("NA12878_3_rep").selectRow())
                .launch(this, pipeline)
                .ensureAll(runOf(pipeline), sizeLessThanOrEqual(3));
        // 3 runs were started and one was stopped in test case EPMCMBIBPC-1599
        // thus there are no more than three runs
    }

    @Test(priority = 5, dependsOnMethods = {"runSampleSetValidation"})
    @TestCase({"EPMCMBIBPC-1627"})
    public void validatePipelineParametersForSampleSet() {
        final List<String> fastqR1FilesList = Arrays.asList(path(dataStorage, fastq, subfolder1, fastqR1D710),
                path(dataStorage, fastq, subfolder1, fastqR1D711), path(dataStorage, fastq, subfolder1, fastqR1D712));
        final List<String> fastqR2FilesList = Arrays.asList(path(dataStorage, fastq, subfolder1, fastqR2D710),
                path(dataStorage, fastq, subfolder1, fastqR2D711), path(dataStorage, fastq, subfolder1, fastqR2D712));
        final List<String> samples = Arrays.asList("NA12878_D710_3", "NA12878_D711_3", "NA12878_D712_3");
        runsMenu()
                .showLog(getLastRunId())
                .expandTab(PARAMETERS)
                .ensureParameterIsPresent(panel, path(dataStorage, human, bed))
                .ensureParameterIsPresent(referenceGenomePath, path(dataStorage, reference, bwa))
                .ensureOnOfManyParametersIsPresent(sampleName, samples)
                .ensureOnOfManyParametersIsPresent(fastqR1,fastqR1FilesList)
                .ensureOnOfManyParametersIsPresent(fastqR2,fastqR2FilesList);
        runsMenu()
                .stopRun(getLastRunId());
    }

    private Runnable ensureSamplesCountIs(final int count) {
        return () -> SelenideElements.of(rows).shouldHaveSize(count);
    }

    private File getFile(String filename) {
        try {
            return Paths.get(ClassLoader.getSystemResource(filename).toURI()).toFile();
        } catch (URISyntaxException e) {
            throw new RuntimeException("Unable to get resource file");
        }
    }

    private Consumer<StorageContentAO> loadFiles(final String... files) {
        return storage -> Arrays.stream(files)
                .map(Utils::createTempFileWithExactName)
                .forEach(storage::uploadFile);
    }

    private String path(final String... folders) {
        return String.format("%s://%s", C.STORAGE_PREFIX, String.join("/", folders));
    }

    private MetadataSelection metadataSelection() {
        return new MetadataSelection();
    }

    private PipelinesLibraryAO libraryContent() {
        return new PipelinesLibraryAO();
    }

    private StorageContentAO storageContent() {
        return new StorageContentAO();
    }

    private MetadataSectionAO metadataContent() { return new MetadataSectionAO(new PipelinesLibraryAO());}
}
