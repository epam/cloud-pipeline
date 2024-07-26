/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.eventsourcing.acl;

import com.epam.pipeline.entity.security.acl.AclClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Map;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class ACLUpdateEventProducerTest {

    private static final long ID = 1L;

    private ACLUpdateEventProducer aclUpdateEventProducer;

    @Spy
    private TestInnerEventProducer innerProviderSpy;

    @Captor
    private ArgumentCaptor<Map<String, String>> eventCapture;

    @Before
    public void setup() {
        aclUpdateEventProducer = new ACLUpdateEventProducer();
        aclUpdateEventProducer.init(innerProviderSpy);
    }

    @Test
    public void putShouldAddAllNecessaryInformation() {
        aclUpdateEventProducer.put(ID, AclClass.FOLDER);
        Mockito.verify(innerProviderSpy).put(eventCapture.capture());
        final Map<String, String> putValue = eventCapture.getValue();
        assertEquals(putValue.get(ACLUpdateEventProducer.ACL_CLASS_FIELD), AclClass.FOLDER.name());
        assertEquals(putValue.get(ACLUpdateEventProducer.ENTITY_ID_FIELD), String.valueOf(ID));
    }

    @Test
    public void putReturnMinusOneIfInnerProducerIsNull() {
        aclUpdateEventProducer.init(null);
        assertEquals(-1, aclUpdateEventProducer.put(ID, AclClass.FOLDER));
    }
}
