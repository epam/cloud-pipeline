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

package com.epam.pipeline.dao.preference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.List;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.preference.PreferenceType;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class PreferenceDaoTest extends AbstractSpringTest {

    private static final String TEST_VALUE = "test value";
    private static final String TEST_VALUE_2 = "test value2";
    private static final String TEST_NAME = "test.parameter.one";
    private static final String TEST_NAME_2 = "test.parameter.one2";
    private static final String TEST_DESCRIPTION = "Test description";
    private static final String TEST_GROUP = "test group";
    private static final boolean TEST_VISIBLE = true;
    private static final PreferenceType TEST_TYPE = PreferenceType.STRING;

    @Autowired
    private PreferenceDao preferenceDao;

    private Preference preference;
    private Preference preference2;

    @Before
    public void setup() {
        preference = new Preference(TEST_NAME, TEST_VALUE, TEST_GROUP,
                TEST_DESCRIPTION, TEST_TYPE, TEST_VISIBLE);
        preference2 = new Preference(TEST_NAME_2, TEST_VALUE_2, TEST_GROUP,
                                     TEST_DESCRIPTION, TEST_TYPE, TEST_VISIBLE);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testCRUDPreference() {
        Preference savedPreference = preferenceDao.upsertPreference(preference);
        assertNotNull(savedPreference);
        assertEquals(preference, savedPreference);

        preferenceDao.upsertPreference(preference2);

        String newValue = "new value";
        String newDescription = "new description";
        String newGroup = "new group";
        PreferenceType newType = PreferenceType.FLOAT;

        preference.setValue(newValue);
        preference.setDescription(newDescription);
        preference.setPreferenceGroup(newGroup);
        preference.setType(newType);

        Preference updatedPreference = preferenceDao.upsertPreference(preference);
        assertEquals(preference, updatedPreference);

        Preference loadedByName = preferenceDao.loadPreferenceByName(TEST_NAME);
        assertEquals(preference, loadedByName);
        assertEquals(updatedPreference, loadedByName);

        Preference loadedPref2 = preferenceDao.loadPreferenceByName(preference2.getName());
        assertNotNull(loadedPref2);
        assertEquals(TEST_VALUE_2, loadedPref2.getValue());

        List<Preference> preferenceList = preferenceDao.loadAllPreferences();
        assertEquals(2, preferenceList.size());

        preferenceDao.deletePreference(savedPreference.getName());
        assertNull(preferenceDao.loadPreferenceByName(savedPreference.getName()));
    }
}