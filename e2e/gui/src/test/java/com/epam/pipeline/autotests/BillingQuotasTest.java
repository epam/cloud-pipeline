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

import com.epam.pipeline.autotests.ao.SettingsPageAO.PreferencesAO;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Tools;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.util.stream.IntStream;

import static com.epam.pipeline.autotests.ao.Primitive.QUOTA;
import static com.epam.pipeline.autotests.ao.Primitive.QUOTAS;
import static com.epam.pipeline.autotests.utils.Utils.DATE_PATTERN;
import static com.epam.pipeline.autotests.utils.Utils.getFile;
import static com.epam.pipeline.autotests.utils.Utils.randomSuffix;
import static java.lang.String.format;
import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.concurrent.TimeUnit.SECONDS;

public class BillingQuotasTest
        extends AbstractSeveralPipelineRunningTest
        implements Authorization, Tools {

    private final String tool = C.TESTING_TOOL_NAME;
    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private final String BILLING_REPORTS_ENABLED_ADMINS = "billing.reports.enabled.admins";
    private final String BILLING_QUOTAS_PERIOD_SECONDS = "billing.quotas.period.seconds";
    private final String dataStorage = format("billingTestData-%s", randomSuffix());
    private final String testStorage = format("testBilling-%s", randomSuffix());
    private final String testFsStorage = format("testBilling-%s", randomSuffix());
    private final String importScript = "/import_billing_data.py";
    private final String billingData = "/billing-test.txt";
    private final String billingCenter1 = "group1";
    private final String billingCenter2 = "group2";
    private String[] prefQuotasPeriodInitial;
    private boolean[] prefReportsEnabledAdminsInitial;
    private String[] runId;
    private String[] storageID;
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
        prefQuotasPeriodInitial = preferencesAO
                .getLinePreference(BILLING_QUOTAS_PERIOD_SECONDS);
        preferencesAO
                .setPreference(BILLING_QUOTAS_PERIOD_SECONDS, "120", true)
                .saveIfNeeded();
    }

    @BeforeClass
    public void prepareBillingValues() {
        IntStream.range(0, 2)
                .forEach(i -> runId[i] = launchTool());
        library()
                .createStorage(testStorage)
                .selectStorage(testStorage);
        storageID[0] = Utils.entityIDfromURL();
        library()
                .createNfsMount(format("/%s", testFsStorage), testFsStorage)
                .selectStorage(testFsStorage);
        fsStorageID = Utils.entityIDfromURL();
        library()
                .createStorage(dataStorage)
                .selectStorage(dataStorage)
                .uploadFile(getFile(importScript))
                .createFile(billingData)
                .editFile(dataStorage, updateDataBillingFile());
        storageID[1] = Utils.entityIDfromURL();
    }

    @AfterClass
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
                .setPreference(BILLING_QUOTAS_PERIOD_SECONDS,
                        prefQuotasPeriodInitial[0], Boolean.parseBoolean(prefQuotasPeriodInitial[1]))
                .saveIfNeeded();
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
                    .checkBillingVisible(false);
            logout();
            loginAs(admin)
                    .settings()
                    .switchToUserManagement()
                    .switchToUsers()
                    .searchForUserEntry(user.login)
                    .edit()
                    .addRoleOrGroup("ROLE_BILLING_MANAGER")
                    .sleep(2, SECONDS)
                    .ok();
            loginAs(user)
                    .settings()
                    .switchToMyProfile()
                    .validateUserName(user.login);
            navigationMenu()
                    .checkBillingVisible(true)
                    .billing()
                    .click(QUOTAS)
                    .getQuotasSection("Overall")
                    .addQuota()
                    .setValue(QUOTA, "10000")
                    .cancel();
        } finally {
            logout();
            loginAs(admin)
                    .settings()
                    .switchToUserManagement()
                    .switchToUsers()
                    .searchForUserEntry(user.login)
                    .edit()
                    .deleteRoleOrGroup("ROLE_BILLING_MANAGER")
                    .sleep(2, SECONDS)
                    .ok();
        }
        }

    private String updateDataBillingFile() {
        LocalDate currentDate = LocalDate.now();

        return Utils.readResourceFully(billingData)
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
                .replaceAll("<fsStorage>", storageID[1])
                .replaceAll("<storage1>", fsStorageID)
                ;
    }

    private String launchTool() {
        tools()
                .perform(registry, group, tool, tool -> tool.run(this));
        return getLastRunId();
    }
}
