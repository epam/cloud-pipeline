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

package com.epam.pipeline.dao.dts;

import com.epam.pipeline.entity.dts.DtsRegistry;
import com.epam.pipeline.entity.dts.DtsStatus;
import com.epam.pipeline.test.jdbc.AbstractJdbcTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.junit.Assert.assertEquals;

@Transactional
public class DtsRegistryDaoTest extends AbstractJdbcTest {
    private static final String TEST_URL = "token";
    private static final String TEST_PREFIX_1 = "prefix_1";
    private static final String TEST_PREFIX_2 = "prefix_2";
    private static final LocalDateTime TEST_DATETIME = LocalDateTime.now();
    private static final DtsStatus TEST_DTS_STATUS = DtsStatus.OFFLINE;
    private static final DtsStatus TEST_DTS_ANOTHER_STATUS = DtsStatus.ONLINE;
    private static final String DTS = "DTS";
    private static final String DTS_PREFERENCE_1 = "dts.preference1";
    private static final String DTS_PREFERENCE_2 = "dts.preference2";

    @Autowired
    private DtsRegistryDao dtsRegistryDao;

    @Test
    public void testCRUD() {
        DtsRegistry dtsRegistry = getDtsRegistry(TEST_URL,
                                                 Stream.of(TEST_PREFIX_1).collect(Collectors.toList()),
                                                 Collections.singletonMap(DTS_PREFERENCE_1, DTS));
        dtsRegistryDao.create(dtsRegistry);
        DtsRegistry loaded = dtsRegistryDao.loadById(dtsRegistry.getId()).orElse(null);
        assertEquals(dtsRegistry, loaded);
        DtsRegistry loadedByName = dtsRegistryDao.loadByName(DTS).orElse(null);
        assertEquals(dtsRegistry, loadedByName);
        dtsRegistry.setPrefixes(Stream.of(TEST_PREFIX_1, TEST_PREFIX_2).collect(Collectors.toList()));
        dtsRegistryDao.update(dtsRegistry);
        loaded = dtsRegistryDao.loadById(dtsRegistry.getId()).orElse(null);
        assertEquals(dtsRegistry, loaded);
        assertEquals(Stream.of(dtsRegistry).collect(Collectors.toList()), dtsRegistryDao.loadAll());
        dtsRegistryDao.delete(dtsRegistry.getId());
        assertEquals(0, dtsRegistryDao.loadAll().size());
    }

    @Test
    public void testDtsNameUniquenessRequirement() {
        dtsRegistryDao.create(getDtsRegistry(TEST_URL));

        assertThrows(() -> dtsRegistryDao.create(getDtsRegistry(TEST_URL)));
    }

    @Test
    public void testDtsPreferencesUpsert() {
        final DtsRegistry dtsRegistry = getDtsRegistry(TEST_URL,
                                                 Stream.of(TEST_PREFIX_1).collect(Collectors.toList()),
                                                 null);
        dtsRegistryDao.create(dtsRegistry);
        dtsRegistry.setPreferences(new HashMap<>());
        assertEqualsToEntityInDb(dtsRegistry);

        upsertPreferencesAndAssertState(dtsRegistry, Collections.singletonMap(DTS_PREFERENCE_1, DTS));
        upsertPreferencesAndAssertState(dtsRegistry, Collections.singletonMap(DTS_PREFERENCE_2, TEST_URL));
        upsertPreferencesAndAssertState(dtsRegistry, Collections.singletonMap(DTS_PREFERENCE_1, TEST_URL));
    }

    @Test
    public void testDtsPreferencesDelete() {
        final HashMap<String, String> preferences = new HashMap<>();
        preferences.put(DTS_PREFERENCE_1, TEST_PREFIX_1);
        preferences.put(DTS_PREFERENCE_2, TEST_PREFIX_2);
        final DtsRegistry dtsRegistry = getDtsRegistry(TEST_URL, Collections.emptyList(), preferences);
        dtsRegistryDao.create(dtsRegistry);
        assertEqualsToEntityInDb(dtsRegistry);

        dtsRegistryDao.deletePreferences(dtsRegistry.getId(), Arrays.asList(DTS_PREFERENCE_1, DTS_PREFERENCE_2));
        dtsRegistry.getPreferences().clear();
        assertEqualsToEntityInDb(dtsRegistry);
    }

    @Test
    public void testDtsHeartbeatUpdate() {
        final DtsRegistry dtsRegistry = getDtsRegistry(TEST_URL);
        dtsRegistryDao.create(dtsRegistry);
        assertEqualsToEntityInDb(dtsRegistry);

        dtsRegistryDao.updateHeartbeat(dtsRegistry.getId(), TEST_DATETIME, TEST_DTS_ANOTHER_STATUS);
        dtsRegistry.setHeartbeat(TEST_DATETIME);
        dtsRegistry.setStatus(TEST_DTS_ANOTHER_STATUS);
        assertEqualsToEntityInDb(dtsRegistry);
    }

    @Test
    public void testDtsStatusUpdate() {
        final DtsRegistry dtsRegistry = getDtsRegistry(TEST_URL);
        dtsRegistryDao.create(dtsRegistry);
        assertEqualsToEntityInDb(dtsRegistry);

        dtsRegistryDao.updateStatus(dtsRegistry.getId(), TEST_DTS_ANOTHER_STATUS);
        dtsRegistry.setStatus(TEST_DTS_ANOTHER_STATUS);
        assertEqualsToEntityInDb(dtsRegistry);
    }

    private void assertEqualsToEntityInDb(final DtsRegistry dtsRegistry) {
        final DtsRegistry loaded = dtsRegistryDao.loadById(dtsRegistry.getId()).orElse(null);
        assertEquals(dtsRegistry, loaded);
    }

    private void upsertPreferencesAndAssertState(final DtsRegistry dtsRegistry,
                                                 final Map<String, String> preferenceUpdate) {
        dtsRegistryDao.upsertPreferences(dtsRegistry.getId(), preferenceUpdate);
        dtsRegistry.getPreferences().putAll(preferenceUpdate);
        assertEqualsToEntityInDb(dtsRegistry);
    }

    private DtsRegistry getDtsRegistry(final String url) {
        return getDtsRegistry(url, Collections.emptyList());
    }

    private DtsRegistry getDtsRegistry(final String url, final List<String> prefixes) {
        return getDtsRegistry(url, prefixes, Collections.emptyMap());
    }

    private DtsRegistry getDtsRegistry(final String url, final List<String> prefixes,
                                       final Map<String, String> preferences) {
        DtsRegistry dtsRegistry = new DtsRegistry();
        dtsRegistry.setName(DTS);
        dtsRegistry.setUrl(url);
        dtsRegistry.setPrefixes(prefixes);
        dtsRegistry.setPreferences(preferences);
        dtsRegistry.setStatus(TEST_DTS_STATUS);
        return dtsRegistry;
    }
}
