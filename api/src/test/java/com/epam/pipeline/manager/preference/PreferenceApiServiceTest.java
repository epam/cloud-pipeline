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

package com.epam.pipeline.manager.preference;

import java.util.Collection;
import java.util.Collections;

import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.preference.PreferenceType;
import com.epam.pipeline.manager.AbstractManagerTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class PreferenceApiServiceTest extends AbstractManagerTest {
    @Autowired
    private PreferenceApiService preferenceApiService;

    @Autowired
    private PreferenceManager preferenceManager;

    private Preference visiblePreference;

    @Before
    public void setUp() throws Exception {
        visiblePreference = new Preference("visible.preference", "", "TEST", "", PreferenceType.STRING, true);
        preferenceManager.update(Collections.singletonList(visiblePreference));
    }

    @Test
    @WithMockUser
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testLoadPreferencesUser() {
        Collection<Preference> preferences = preferenceApiService.loadAll();
        Assert.assertFalse(preferences.isEmpty());
        Assert.assertTrue(preferences.stream().allMatch(Preference::isVisible));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testLoadPreferencesAdmin() {
        Collection<Preference> preferences = preferenceApiService.loadAll();
        Assert.assertFalse(preferences.isEmpty());
        Assert.assertTrue(preferences.stream().anyMatch(Preference::isVisible));
        Assert.assertTrue(preferences.stream().anyMatch(p -> !p.isVisible()));
    }
}