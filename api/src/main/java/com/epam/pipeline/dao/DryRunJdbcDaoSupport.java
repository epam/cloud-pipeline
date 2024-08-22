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

package com.epam.pipeline.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Enables ability to execute queries in read-only mode
 */
public class DryRunJdbcDaoSupport extends NamedParameterJdbcDaoSupport {

    @Autowired
    private JdbcTemplateDryRunWrapper jdbcTemplateDryRunWrapper;

    protected NamedParameterJdbcTemplate getNamedParameterJdbcTemplate(final boolean dryRun) {
        return dryRun ? jdbcTemplateDryRunWrapper : getNamedParameterJdbcTemplate();
    }
}
