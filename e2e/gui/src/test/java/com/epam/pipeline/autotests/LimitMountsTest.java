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

import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.text;
import static com.epam.pipeline.autotests.ao.Primitive.CLEAR_SELECTION;
import static com.epam.pipeline.autotests.ao.Primitive.LIMIT_MOUNTS;
import static com.epam.pipeline.autotests.ao.Primitive.OK;
import static com.epam.pipeline.autotests.ao.Primitive.SELECT_ALL;
import static com.epam.pipeline.autotests.ao.Primitive.SELECT_ALL_NON_SENSITIVE;

public class LimitMountsTest extends AbstractBfxPipelineTest implements Navigation, Authorization {

    private String storage1 = "limitMountsStorage" + Utils.randomSuffix();

    @BeforeClass(alwaysRun = true)
    public void setPreferences() {
        library()
                .createStorage(storage1);
    }

    @Test
    @TestCase(value = {"2210"})
    public void validatePerUserDefaultMountLimits() {
        navigationMenu()
                .settings()
                .switchToMyProfile()
                .limitMountsPerUser()
                .clearSelection()
                .ensureAll(enabled, SELECT_ALL, SELECT_ALL_NON_SENSITIVE, OK)
                .ensureNotVisible(CLEAR_SELECTION)
                .searchStorage(storage1)
                .selectStorage(storage1)
                .ensureVisible(CLEAR_SELECTION)
                .ensureAll(enabled, OK)
                .ok()
                .ensure(LIMIT_MOUNTS, text(storage1));
    }
}
