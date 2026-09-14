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

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SessionImplementor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;

public class JsonDataUserType extends AbstractJsonUserType<Map> {

    @Override
    @SuppressWarnings("rawtypes")
    protected Class<Map> jsonType() {
        return Map.class;
    }

    /** Returns an empty map instead of {@code null} when the DB column is {@code NULL}. */
    @Override
    public Object nullSafeGet(final ResultSet rs, final String[] names, final SessionImplementor session,
                              final Object owner) throws HibernateException, SQLException {
        final Object value = super.nullSafeGet(rs, names, session, owner);
        return value != null ? value : Collections.emptyMap();
    }
}
