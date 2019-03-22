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

package com.epam.pipeline.dao.metadata;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.entity.metadata.FireCloudClass;
import com.epam.pipeline.entity.metadata.MetadataClass;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

public class MetadataClassDaoTest extends AbstractSpringTest {

    private static final Long CLASS_ID_1 = 1L;
    private static final String CLASS_NAME_1 = "Sample";

    @Autowired
    private MetadataClassDao metadataClassDao;

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testCRUDMetadataClass() {
        MetadataClass metadataClass = new MetadataClass();
        metadataClass.setId(CLASS_ID_1);
        metadataClass.setName(CLASS_NAME_1);
        metadataClassDao.createMetadataClass(metadataClass);

        List<MetadataClass> expectedResult = Collections.singletonList(metadataClass);
        Assert.assertEquals(expectedResult, metadataClassDao.loadAllMetadataClasses());

        metadataClass.setFireCloudClassName(FireCloudClass.SAMPLE);
        metadataClassDao.updateMetadataClass(metadataClass);

        expectedResult = Collections.singletonList(metadataClass);
        Assert.assertEquals(expectedResult, metadataClassDao.loadAllMetadataClasses());

        metadataClassDao.deleteMetadataClass(metadataClass.getId());
        Assert.assertEquals(Collections.emptyList(), metadataClassDao.loadAllMetadataClasses());
    }
}
