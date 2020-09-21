/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.metadata;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.controller.vo.MetadataVO;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.user.UserManager;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class CategoricalAttributeManagerTest extends AbstractSpringTest {

    private static final String KEY_1 = "key1";
    private static final String KEY_2 = "key2";
    private static final String SENSITIVE_KEY = "sensitive_metadata_key";
    private static final String VALUE_1 = "valueA";
    private static final String VALUE_2 = "valueB";
    private static final String TYPE = "string";

    private static final String TEST_USER = "TEST_USER";

    @Autowired
    private CategoricalAttributeManager categoricalAttributeManager;

    @Autowired
    private MetadataManager metadataManager;

    @Autowired
    private UserManager userManager;


    @Test
    @Transactional
    public void syncWithMetadata() {
        Assert.assertEquals(0, categoricalAttributeManager.loadAll().size());

        final PipelineUser testUser = userManager
            .createUser(TEST_USER, Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), null);
        final EntityVO entityVO = new EntityVO(testUser.getId(), AclClass.PIPELINE_USER);
        final Map<String, PipeConfValue> data = new HashMap<>();
        data.put(KEY_1, new PipeConfValue(TYPE, VALUE_1));
        data.put(KEY_2, new PipeConfValue(TYPE, VALUE_2));
        data.put(SENSITIVE_KEY, new PipeConfValue(TYPE, VALUE_2));
        final MetadataVO metadataVO = new MetadataVO();
        metadataVO.setEntity(entityVO);
        metadataVO.setData(data);
        metadataManager.updateMetadataItem(metadataVO);
        categoricalAttributeManager.syncWithMetadata();

        final Map<String, List<String>> categoricalAttributesAfterSync = categoricalAttributeManager.loadAll();
        Assert.assertEquals(2, categoricalAttributesAfterSync.size());
        Assert.assertThat(categoricalAttributesAfterSync.get(KEY_1),
                          CoreMatchers.is(Collections.singletonList(VALUE_1)));
        Assert.assertThat(categoricalAttributesAfterSync.get(KEY_2),
                          CoreMatchers.is(Collections.singletonList(VALUE_2)));
        Assert.assertFalse(categoricalAttributesAfterSync.containsKey(SENSITIVE_KEY));
    }
}
