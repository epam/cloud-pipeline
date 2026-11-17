/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.security;

import com.epam.pipeline.entity.user.Role;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Session keeps the security context, and so this object, in the database with Java
 * serialization, which is why its round trip is worth asserting at all.
 * <p>
 * Note what is <em>not</em> covered: this class carries no explicit {@code serialVersionUID}, so a
 * change to its shape makes the sessions an earlier build wrote unreadable. That is handled on
 * upgrade by removing the stored sessions, not here — a test cannot deserialize a payload written
 * by a class that is no longer on the classpath.
 */
public class UserContextSerializationTest {

    private static final String USER_NAME = "JANE";
    private static final Long USER_ID = 1L;

    /**
     * A context built without a token is an ordinary one: renewable, not narrowed, not external.
     * The renewal flag is held as a negative so that this stays true of an instance whose fields
     * were never set, which is also the shape deserialization produces for a field the stream does
     * not carry.
     */
    @Test
    public void shouldTreatAContextWithNothingSetAsAnOrdinaryOne() {
        final UserContext bare = new UserContext();

        assertThat(bare.isNonRenewable()).isFalse();
        assertThat(bare.isAuthoritiesScoped()).isFalse();
        assertThat(bare.isExternal()).isFalse();
    }

    @Test
    public void shouldSurviveARoundTrip() throws Exception {
        final UserContext context = new UserContext(USER_ID, USER_NAME);
        context.setRoles(Collections.singletonList(new Role("ROLE_USER")));
        context.setGroups(Collections.singletonList("BIOINFORMATICS"));
        context.setNonRenewable(true);
        context.setAuthoritiesScoped(true);

        final UserContext read = roundTrip(context);

        assertThat(read.getUsername()).isEqualTo(USER_NAME);
        assertThat(read.getUserId()).isEqualTo(USER_ID);
        assertThat(read.isNonRenewable()).isTrue();
        assertThat(read.isAuthoritiesScoped()).isTrue();
        assertThat(read.getAuthorities()).hasSize(2);
    }

    @Test
    public void shouldSurviveARoundTripOfAnOrdinaryLogin() throws Exception {
        final UserContext read = roundTrip(new UserContext(USER_ID, USER_NAME));

        assertThat(read.isNonRenewable()).isFalse();
        assertThat(read.isAuthoritiesScoped()).isFalse();
    }

    private static UserContext roundTrip(final UserContext context)
            throws IOException, ClassNotFoundException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(context);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (UserContext) in.readObject();
        }
    }
}
