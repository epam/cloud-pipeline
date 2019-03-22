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

package com.epam.pipeline.manager.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.utils.DefaultSystemParameter;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class UtilsManagerTest extends AbstractManagerTest {
    @Autowired
    private PreferenceManager preferenceManager;

    @Autowired
    private UtilsManager utilsManager;

    @Autowired
    private ApplicationContext context;

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testGetSystemParameters() throws IOException {
        File parametersJson = context.getResource("classpath:templates/default_parameters.json").getFile();
        Preference pref = SystemPreferences.LAUNCH_SYSTEM_PARAMETERS.toPreference();
        pref.setValue(Files.readAllLines(parametersJson.toPath()).stream().collect(Collectors.joining()));
        preferenceManager.update(Collections.singletonList(pref));

        List<DefaultSystemParameter> params = utilsManager.getSystemParameters();
        Assert.assertFalse(params.isEmpty());
    }
}