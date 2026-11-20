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

package com.epam.pipeline.dao.dts;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.entity.dts.DtsRegistry;
<<<<<<< ours
import org.junit.Test;
||||||| base
import com.epam.pipeline.entity.dts.DtsStatus;
import com.epam.pipeline.test.jdbc.AbstractJdbcTest;
import org.junit.Test;
=======
import com.epam.pipeline.entity.dts.DtsStatus;
import com.epam.pipeline.test.jdbc.AbstractJdbcTest;
import org.junit.jupiter.api.Test;
>>>>>>> theirs
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

<<<<<<< ours
||||||| base
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
=======
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
>>>>>>> theirs
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

<<<<<<< ours
import static org.junit.Assert.assertEquals;
||||||| base
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.junit.Assert.assertEquals;
=======
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
>>>>>>> theirs

@Transactional
public class DtsRegistryDaoTest extends AbstractSpringTest {
    private static final String TEST_URL = "token";
    private static final String TEST_PREFIX_1 = "prefix_1";
    private static final String TEST_PREFIX_2 = "prefix_2";
<<<<<<< ours
||||||| base
    private static final LocalDateTime TEST_DATETIME = LocalDateTime.now();
    private static final DtsStatus TEST_DTS_STATUS = DtsStatus.OFFLINE;
    private static final DtsStatus TEST_DTS_ANOTHER_STATUS = DtsStatus.ONLINE;
=======
    private static final LocalDateTime TEST_DATETIME = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    private static final DtsStatus TEST_DTS_STATUS = DtsStatus.OFFLINE;
    private static final DtsStatus TEST_DTS_ANOTHER_STATUS = DtsStatus.ONLINE;
>>>>>>> theirs
    private static final String DTS = "DTS";

    @Autowired
    private DtsRegistryDao dtsRegistryDao;

    @Test
    public void testCRUD() {
        DtsRegistry dtsRegistry = getDtsRegistry(TEST_URL,
                Stream.of(TEST_PREFIX_1).collect(Collectors.toList()));
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

    private DtsRegistry getDtsRegistry(String url, List<String> prefixes) {
        DtsRegistry dtsRegistry = new DtsRegistry();
        dtsRegistry.setName(DTS);
        dtsRegistry.setUrl(url);
        dtsRegistry.setPrefixes(prefixes);
        return dtsRegistry;
    }
}