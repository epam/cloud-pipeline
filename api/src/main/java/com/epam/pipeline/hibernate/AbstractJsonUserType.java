/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.hibernate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.type.SerializationException;
import org.hibernate.usertype.UserType;

import java.io.IOException;
import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Objects;

/**
 * Base Hibernate {@link UserType} that maps any Java object to a PostgreSQL {@code jsonb} column
 * using Jackson for serialization. Subclasses only need to provide the target Java type via
 * {@link #jsonType()}.
 *
 * @param <T> the Java type stored in the column
 */
public abstract class AbstractJsonUserType<T> implements UserType {

    protected final ObjectMapper objectMapper = new ObjectMapper();

    /** Returns the Java type this user type maps to. */
    protected abstract Class<T> jsonType();

    @Override
    public Class<?> returnedClass() {
        return jsonType();
    }

    @Override
    public int[] sqlTypes() {
        return new int[]{Types.JAVA_OBJECT};
    }

    @Override
    public boolean equals(final Object x, final Object y) throws HibernateException {
        return Objects.equals(x, y);
    }

    @Override
    public int hashCode(final Object x) throws HibernateException {
        return x == null ? 0 : x.hashCode();
    }

    @Override
    public boolean isMutable() {
        return true;
    }

    @Override
    public Object replace(final Object original, final Object target, final Object owner)
            throws HibernateException {
        return deepCopy(original);
    }

    @Override
    public void nullSafeSet(final PreparedStatement st, final Object value, final int index,
                            final SessionImplementor session) throws HibernateException, SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
            return;
        }
        try {
            st.setObject(index, objectMapper.writeValueAsString(value), Types.OTHER);
        } catch (JsonProcessingException e) {
            throw new HibernateException(
                    "Failed to serialize " + jsonType().getSimpleName() + " to JSONB", e);
        }
    }

    @Override
    public Object nullSafeGet(final ResultSet rs, final String[] names, final SessionImplementor session,
                              final Object owner) throws HibernateException, SQLException {
        final Object raw = rs.getObject(names[0]);
        if (raw == null) {
            return null;
        }
        try {
            return objectMapper.readValue(raw.toString(), jsonType());
        } catch (IOException e) {
            throw new HibernateException(
                    "Failed to deserialize " + jsonType().getSimpleName() + " from JSONB", e);
        }
    }

    @Override
    public T deepCopy(final Object value) throws HibernateException {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(objectMapper.writeValueAsString(value), jsonType());
        } catch (IOException e) {
            throw new HibernateException(
                    "Failed to deep copy " + jsonType().getSimpleName(), e);
        }
    }

    @Override
    public Serializable disassemble(final Object value) throws HibernateException {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new SerializationException(
                    "Cannot serialize " + jsonType().getSimpleName(), e);
        }
    }

    @Override
    public Object assemble(final Serializable cached, final Object owner) throws HibernateException {
        try {
            return objectMapper.readValue((String) cached, jsonType());
        } catch (IOException e) {
            throw new HibernateException(
                    "Cannot deserialize " + jsonType().getSimpleName(), e);
        }
    }
}
