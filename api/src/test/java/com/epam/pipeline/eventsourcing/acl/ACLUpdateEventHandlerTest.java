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

import com.epam.pipeline.eventsourcing.Event;
import com.epam.pipeline.eventsourcing.EventType;
import com.epam.pipeline.security.acl.DisabledAclCache;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.security.acls.domain.ObjectIdentityImpl;

import java.util.HashMap;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class ACLUpdateEventHandlerTest {

    private static final String TEST_VALUE = "test";
    private static final String ID_TEST_VALUE = "1";
    private static final String OTHER_TEST_VALUE = "test2";

    private ACLUpdateEventHandler eventHandler;

    @Spy
    DisabledAclCache aclCache;

    @Captor
    ArgumentCaptor<ObjectIdentityImpl> eventCapture;

    @Before
    public void setup() {
        eventHandler = new ACLUpdateEventHandler(TEST_VALUE, TEST_VALUE, aclCache);
    }

    @Test
    public void validateMethodShouldReturnFalseIfAppIdTheSame() {
        Event event = Event.builder()
                .applicationId(TEST_VALUE)
                .type(EventType.ACL.name())
                .data(
                        new HashMap<String, String>() {{
                            put(ACLUpdateEventHandler.ACL_CLASS_FIELD, TEST_VALUE);
                            put(ACLUpdateEventHandler.ID_FIELD, TEST_VALUE);
                        }}
                )
                .build();
        assertFalse(eventHandler.validateEvent(event));
    }

    @Test
    public void validateMethodShouldReturnFalseIfEventTypeDiffer() {
        Event event = Event.builder()
                .applicationId(OTHER_TEST_VALUE)
                .type(TEST_VALUE)
                .data(
                        new HashMap<String, String>() {{
                            put(ACLUpdateEventHandler.ACL_CLASS_FIELD, TEST_VALUE);
                            put(ACLUpdateEventHandler.ID_FIELD, TEST_VALUE);
                        }}
                )
                .build();
        assertFalse(eventHandler.validateEvent(event));
    }

    @Test
    public void validateMethodShouldReturnFalseIfEventDoesntHaveSpecificValues() {
        Event event = Event.builder()
                .applicationId(OTHER_TEST_VALUE)
                .type(TEST_VALUE)
                .data(
                        new HashMap<String, String>() {{
                            put(ACLUpdateEventHandler.ID_FIELD, TEST_VALUE);
                        }}
                )
                .build();
        assertFalse(eventHandler.validateEvent(event));

        event = Event.builder()
                .applicationId(OTHER_TEST_VALUE)
                .type(TEST_VALUE)
                .data(
                        new HashMap<String, String>() {{
                            put(ACLUpdateEventHandler.ACL_CLASS_FIELD, TEST_VALUE);
                        }}
                )
                .build();
        assertFalse(eventHandler.validateEvent(event));
    }

    @Test
    public void handleShouldCallEvictFromCache() {
        final Event event = Event.builder()
                .applicationId(OTHER_TEST_VALUE)
                .type(EventType.ACL.name())
                .data(
                        new HashMap<String, String>() {{
                            put(ACLUpdateEventHandler.ACL_CLASS_FIELD, TEST_VALUE);
                            put(ACLUpdateEventHandler.ID_FIELD, ID_TEST_VALUE);
                        }}
                )
                .build();
        eventHandler.handle(1L, event);
        Mockito.verify(aclCache).evictFromCache(eventCapture.capture());
        final ObjectIdentityImpl acl = eventCapture.getValue();
        assertEquals(acl.getType(), TEST_VALUE);
        assertEquals(acl.getIdentifier(), Long.valueOf(ID_TEST_VALUE));
    }

}
