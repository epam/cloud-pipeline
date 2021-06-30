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
import com.epam.pipeline.test.jdbc.AbstractJdbcTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

@Transactional
public class DtsRegistryDaoTest extends AbstractJdbcTest {
    private static final String TEST_URL = "token";
    private static final String TEST_PREFIX_1 = "prefix_1";
    private static final String TEST_PREFIX_2 = "prefix_2";
    private static final String DTS = "DTS";
    private static final String DTS_PREFERENCE_1 = "dts.preference";

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
        dtsRegistry.setPrefixes(Stream.of(TEST_PREFIX_1, TEST_PREFIX_2).collect(Collectors.toList()));
        dtsRegistryDao.update(dtsRegistry);
        loaded = dtsRegistryDao.loadById(dtsRegistry.getId()).orElse(null);
        assertEquals(dtsRegistry, loaded);
        assertEquals(Stream.of(dtsRegistry).collect(Collectors.toList()), dtsRegistryDao.loadAll());
        dtsRegistryDao.delete(dtsRegistry.getId());
        assertEquals(0, dtsRegistryDao.loadAll().size());
    }

    private DtsRegistry getDtsRegistry(final String url, final List<String> prefixes,
                                       final Map<String, String> preferences) {
        DtsRegistry dtsRegistry = new DtsRegistry();
        dtsRegistry.setName(DTS);
        dtsRegistry.setUrl(url);
        dtsRegistry.setPrefixes(prefixes);
        dtsRegistry.setPreferences(preferences);
        return dtsRegistry;
    }
}
