/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.autotests.ao.BillingTabAO;
import com.epam.pipeline.autotests.ao.SettingsPageAO.PreferencesAO;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Tools;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.stream.IntStream;

import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Selenide.refresh;
import static com.epam.pipeline.autotests.ao.BillingTabAO.BillingQuotaPeriod;
import static com.epam.pipeline.autotests.ao.BillingTabAO.BillingQuotaPeriod.*;
import static com.epam.pipeline.autotests.ao.BillingTabAO.BillingQuotaType;
import static com.epam.pipeline.autotests.ao.BillingTabAO.BillingQuotaType.*;
import static com.epam.pipeline.autotests.ao.Primitive.ACTIONS;
import static com.epam.pipeline.autotests.ao.Primitive.ADVANCED_PANEL;
import static com.epam.pipeline.autotests.ao.Primitive.CLOSE;
import static com.epam.pipeline.autotests.ao.Primitive.COMPUTE_INSTANCES;
import static com.epam.pipeline.autotests.ao.Primitive.PERIOD;
import static com.epam.pipeline.autotests.ao.Primitive.QUOTA;
import static com.epam.pipeline.autotests.ao.Primitive.QUOTAS;
import static com.epam.pipeline.autotests.ao.Primitive.RECIPIENTS;
import static com.epam.pipeline.autotests.ao.Primitive.REMOVE;
import static com.epam.pipeline.autotests.ao.Primitive.SAVE;
import static com.epam.pipeline.autotests.ao.Primitive.STORAGES;
import static com.epam.pipeline.autotests.ao.Primitive.THRESHOLD;
import static com.epam.pipeline.autotests.ao.Primitive.TITLE;
import static com.epam.pipeline.autotests.utils.Utils.DATE_PATTERN;
import static com.epam.pipeline.autotests.utils.Utils.getFile;
import static com.epam.pipeline.autotests.utils.Utils.randomSuffix;
import static java.lang.String.format;
import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.Locale.ENGLISH;
import static java.util.concurrent.TimeUnit.SECONDS;

public class BillingQuotasTest
        extends AbstractSeveralPipelineRunningTest
        implements Authorization, Tools {

    private final String tool = C.TESTING_TOOL_NAME;
    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private final String ELASTIC_URL = C.ELASTIC_URL;
    private final String ROLE_BILLING_MANAGER = "ROLE_BILLING_MANAGER";
    private final String BILLING_REPORTS_ENABLED_ADMINS = "billing.reports.enabled.admins";
    private final String BILLING_REPORTS_ENABLED = "billing.reports.enabled";
    private final String BILLING_QUOTAS_PERIOD_SECONDS = "billing.quotas.period.seconds";
    private final String NOTIFY = "Notify";
    private final String READ_ONLY_MODE = "Read-only mode";
    private final String DISABLE_NEW_JOBS = "Disable new jobs";
    private final String STOP_ALL_JOBS = "Stop all jobs";
    private final String BLOCK = "Block";
    private final String dataStorage = format("billingTestData-%s", randomSuffix());
    private final String testStorage = format("testBilling-%s", randomSuffix());
    private final String testFsStorage = format("testBilling-%s", randomSuffix());
    private final String importScript = "import_billing_data.py";
    private final String billingData = "billing-test.txt";
    private final String billingCenter1 = "group3";
    private final String billingCenter2 = "group2";
    private final String[] billing = {"70", "80", "100", "80", "90", "120"};
    private final String[] quota = {"10000", "20000", billing[3]};
    private final String[] threshold = {"90", "80"};
    private String[] prefQuotasPeriodInitial;
    private boolean[] prefReportsEnabledAdminsInitial;
    private boolean[] prefReportsEnabledInitial;
    private String[] runId = new String[4];
    private String[] storageID = new String[2];
    private String fsStorageID;

    @BeforeClass
    public void setPreferencesValue() {
        final PreferencesAO preferencesAO = navigationMenu()
                .settings()
                .switchToPreferences();
        prefReportsEnabledAdminsInitial = preferencesAO
                .getCheckboxPreferenceState(BILLING_REPORTS_ENABLED_ADMINS);
        preferencesAO
                .setCheckboxPreference(BILLING_REPORTS_ENABLED_ADMINS, true,true)
                .saveIfNeeded();
        prefReportsEnabledInitial = preferencesAO
                .getCheckboxPreferenceState(BILLING_REPORTS_ENABLED);
        preferencesAO
                .setCheckboxPreference(BILLING_REPORTS_ENABLED, true,true)
                .saveIfNeeded();
        prefQuotasPeriodInitial = preferencesAO
                .getLinePreference(BILLING_QUOTAS_PERIOD_SECONDS);
        preferencesAO
                .setPreference(BILLING_QUOTAS_PERIOD_SECONDS, "120", true)
                .saveIfNeeded();
    }

    @BeforeClass
    public void prepareBillingValues() {
        IntStream.range(0, 3)
                .forEach(i -> {
                        runId[i] = launchTool();
                        runsMenu()
                                .stopRunIfPresent(runId[i]);
                });
        library()
                .createStorage(testStorage)
                .selectStorage(testStorage);
        storageID[0] = Utils.entityIDfromURL();
        library()
                .createStorage(dataStorage)
                .selectStorage(dataStorage);
        storageID[1] = Utils.entityIDfromURL();
        library()
                .createNfsMount(format("/%s", testFsStorage), testFsStorage)
                .selectStorage(testFsStorage);
        Utils.entityIDfromURL();
        library()
                .selectStorage(dataStorage)
                .uploadFile(getFile(importScript))
                .uploadFile(updateDataBillingFile());
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .expandTab(ADVANCED_PANEL)
                .selectDataStoragesToLimitMounts()
                .clearSelection()
                .searchStorage(dataStorage)
                .selectStorage(dataStorage)
                .ok()
                .launch(this)
                .showLog(runId[3] = getLastRunId())
                .waitForSshLink()
                .ssh(shell -> shell
                        .waitUntilTextAppears(runId[3])
                        .execute(format("cd /cloud-data/%s", dataStorage.toLowerCase()))
                        .execute(format("python import_billing_data.py --operation add --data-file billing-test.txt --elastic-url %s", ELASTIC_URL))
                        .waitForLog(format("root@pipeline-%s:~/cloud-data/%s#", runId[3], dataStorage.toLowerCase()))
                        .close());
    }

    @AfterClass(alwaysRun=true)
    public void resetPreferencesValue() {
        logoutIfNeeded();
        loginAs(admin);
        final PreferencesAO preferencesAO = navigationMenu()
                .settings()
                .switchToPreferences();
        preferencesAO
                .setCheckboxPreference(BILLING_REPORTS_ENABLED_ADMINS,
                        prefReportsEnabledAdminsInitial[0],prefReportsEnabledAdminsInitial[1])
                .saveIfNeeded();
        preferencesAO
                .setCheckboxPreference(BILLING_REPORTS_ENABLED,
                        prefReportsEnabledInitial[0],prefReportsEnabledInitial[1])
                .saveIfNeeded();
        preferencesAO
                .setPreference(BILLING_QUOTAS_PERIOD_SECONDS,
                        prefQuotasPeriodInitial[0], Boolean.parseBoolean(prefQuotasPeriodInitial[1]))
                .saveIfNeeded();
        runsMenu()
                .showLog(runId[3])
                .waitForSshLink()
                .ssh(shell -> shell
                        .waitUntilTextAppears(runId[3])
                        .execute(format("cd /cloud-data/%s", dataStorage.toLowerCase()))
                        .execute(format("python import_billing_data.py --operation remove --data-file billing-test.txt --elastic-url %s", ELASTIC_URL))
                        .waitForLog(format("root@pipeline-%s:~/cloud-data/%s#", runId[3], dataStorage.toLowerCase()))
                        .close());
    }

    @AfterClass(alwaysRun=true)
    public void removeEntities() {
        Utils.removeStorages(this, testStorage);   //, dataStorage
        library()
                .selectStorage(testFsStorage)
                .clickEditStorageButton()
                .editForNfsMount()
                .clickDeleteStorageButton()
                .clickDelete();
    }

    @Test
    @TestCase(value = {"762_1"})
    public void runToolThatHaveNoNginxEndpoint() {
        try {
            logout();
            loginAs(user)
                    .settings()
                    .switchToMyProfile()
                    .validateUserName(user.login);
            navigationMenu()
                    .checkBillingVisible(true)
                    .billing()
                    .ensureNotVisible(QUOTAS, STORAGES, COMPUTE_INSTANCES);
            logout();
            loginAs(admin)
                    .settings()
                    .switchToUserManagement()
                    .switchToUsers()
                    .searchForUserEntry(user.login)
                    .edit()
                    .addRoleOrGroup(ROLE_BILLING_MANAGER)
                    .sleep(2, SECONDS)
                    .ok();
            logout();
            loginAs(user)
                    .settings()
                    .switchToMyProfile()
                    .validateUserName(user.login);
            navigationMenu()
                    .checkBillingVisible(true)
                    .billing()
                    .ensureVisible(QUOTAS, STORAGES, COMPUTE_INSTANCES)
                    .click(QUOTAS)
                    .getQuotasSection(OVERALL)
                    .addQuota()
                    .ensureVisible(QUOTA, ACTIONS, THRESHOLD)
                    .ensure(PERIOD, text(PER_MONTH.period))
                    .ensureNotVisible(RECIPIENTS)
                    .ensureDisable(SAVE)
                    .setValue(QUOTA, quota[0])
                    .ensureActionsList(NOTIFY, READ_ONLY_MODE, DISABLE_NEW_JOBS,
                            STOP_ALL_JOBS, BLOCK)
                    .setAction(threshold[0], NOTIFY)
                    .ensureVisible(RECIPIENTS)
                    .addRecipient(user.login)
                    .ok()
                    .openQuotaEntry("", quotaEntry(quota[0], PER_MONTH))
                    .ensure(TITLE, text("Global quota"))
                    .ensureDisable(QUOTA, THRESHOLD)
                    .ensureComboboxFieldDisabled(ACTIONS, PERIOD, RECIPIENTS)
                    .ensureNotVisible(SAVE)
                    .ensureVisible(CLOSE, REMOVE)
                    .close()
                    .getQuotaEntry("", quotaEntry(quota[0], PER_MONTH))
                    .checkEntryActions(format("%s%%: %s", threshold[0], NOTIFY.toLowerCase()));
        } finally {
            refresh();
            logout();
            loginAs(admin)
                    .settings()
                    .switchToUserManagement()
                    .switchToUsers()
                    .searchForUserEntry(user.login)
                    .edit()
                    .deleteRoleOrGroup(ROLE_BILLING_MANAGER)
                    .sleep(2, SECONDS)
                    .ok();
        }
        }

    @Test(dependsOnMethods = "runToolThatHaveNoNginxEndpoint")
    @TestCase(value = {"762_2"})
    public void checkCreationDeletionGlobalQuotaWithTheSameAndDifferentQuotaPeriod() {
        billingMenu()
                .click(QUOTAS)
                .getQuotasSection(OVERALL)
                .addQuota()
                .setValue(QUOTA, quota[1])
                .setAction(threshold[1], NOTIFY, READ_ONLY_MODE)
                .addRecipient(admin.login)
                .click(SAVE)
                .errorMessageShouldAppear(OVERALL, PER_MONTH)
                .selectValue(PERIOD, PER_YEAR.period)
                .ok()
                .getQuotaEntry("", quotaEntry(quota[0], PER_MONTH))
                .removeQuota()
                .openQuotaEntry("", quotaEntry(quota[1], PER_YEAR))
                .removeQuota();
    }

    @Test//(dependsOnMethods = "runToolThatHaveNoNginxEndpoint")
    @TestCase(value = {"762_3"})
    public void checkOverallComputeInstancesQuota() {
        billingMenu()
                .click(COMPUTE_INSTANCES)
                .checkQuotasSections(OVERALL, BILLING_CENTERS, GROUPS, USERS)
                .getQuotasSection(OVERALL)
                .addQuota()
                .ensure(TITLE, text("Create compute instances quota"));

    }

    private File updateDataBillingFile() {
        LocalDate currentDate = LocalDate.now();
        String result = Utils.readResourceFully(format("/%s", billingData))
                .replaceAll("<user_name1>", admin.login)
                .replaceAll("<user_name2>", user.login)
                .replaceAll("<user_name3>", userWithoutCompletedRuns.login)
                .replaceAll("<group1>", C.ROLE_USER)
                .replaceAll("<billing_center1>", billingCenter1)
                .replaceAll("<billing_center2>", billingCenter2)
                .replaceAll("<start_data1>", currentDate.format(ofPattern("yyyy-MM-01")))
                .replaceAll("<start_data2>", currentDate.format(ofPattern("yyyy-01-01")))
                .replaceAll("<end_data>", currentDate.format(ofPattern(DATE_PATTERN)))
                .replaceAll("<runId1>", runId[0])
                .replaceAll("<runId2>", runId[1])
                .replaceAll("<runId3>", runId[2])
                .replaceAll("<storage1>", storageID[0])
                .replaceAll("<fsStorage>", fsStorageID)
                .replaceAll("<storage2>", storageID[1])
                .replaceAll("<billing1>", billing[0])
                .replaceAll("<billing2>", billing[1])
                .replaceAll("<billing3>", billing[2])
                .replaceAll("<billing4>", billing[3])
                .replaceAll("<billing5>", billing[4])
                .replaceAll("<billing6>", billing[5]);
        return Utils.createTempFileWithContent(billingData, result);
    }

    private String launchTool() {
        tools()
                .perform(registry, group, tool, tool -> tool.run(this));
        return getLastRunId();
    }

    private String quotaEntry(String quota, BillingQuotaPeriod period) {
        return format("%s$ %s",
                NumberFormat.getInstance(ENGLISH).format(Integer.valueOf(quota)),
                period.period);
    }

    private String quotaPopupTitle(BillingQuotaType type) {
        return format("%s quota", type.type);
    }

    private String createQuotaPopupTitle(BillingQuotaType type) {
        return format("Create %s quota", type.type.toLowerCase());
    }
}
