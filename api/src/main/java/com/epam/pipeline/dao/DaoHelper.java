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

package com.epam.pipeline.dao;

import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.pipeline.Folder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.sql.Array;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class DaoHelper extends NamedParameterJdbcDaoSupport {

    private static final String[] EMPTY_STRING_ARRAY = new String[0];
    private static final Long[] EMPTY_LONG_ARRAY = new Long[0];

    public static final String POSTGRES_LIKE_CHARACTER = "%";
    public static final String POSTGRE_TYPE_TEXT = "TEXT";
    public static final String POSTGRE_TYPE_BIGINT = "BIGINT";
    public static final String UNDERSCORE = "_";
    public static final String UNDERSCORE_PARAMETER = "@_@";
    public static final String UNDERSCORE_ESCAPED = "\\\\_";
    public static final String IN_CLAUSE_PLACEHOLDER = "@in@";

    private String createIdQuery;
    private String createIdsQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    public Long createId(final String sequenceName) {
        Assert.isTrue(StringUtils.isNotBlank(sequenceName), "Sequence name is required");
        return getNamedParameterJdbcTemplate().queryForObject(createIdQuery,
                new MapSqlParameterSource(HelperParameters.SEQUENCE_NAME.name(), sequenceName), Long.class);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Long> createIds(final String sequenceName, int numberOfIds) {
        Assert.isTrue(StringUtils.isNotBlank(sequenceName), "Sequence name is required");
        MapSqlParameterSource source =
                new MapSqlParameterSource(HelperParameters.SEQUENCE_NAME.name(), sequenceName);
        source.addValue(HelperParameters.LIMIT.name(), numberOfIds);
        return getNamedParameterJdbcTemplate().queryForList(createIdsQuery, source, Long.class);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateLocks(String query, List<Long> entityIds, boolean isLocked) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("LOCKED", isLocked);
        params.addValue("IDS", entityIds);
        getNamedParameterJdbcTemplate().update(query, params);
    }

    /**
     * Replaces a IN clause placeholder (@in@) with a valid list of SQL query placeholders (?, ?, ...)
     * @param query a query to replace IN clause placeholder
     * @param paramsCount size of IN clause
     * @return an SQL query with replaced IN clause placeholder
     */
    public static String replaceInClause(String query, int paramsCount) {
        return query.replace(IN_CLAUSE_PLACEHOLDER, IntStream.range(0, paramsCount)
            .mapToObj(s -> "?")
            .collect(Collectors.joining(", ")));
    }

    /**
     * In PostgreSQL LIKE clause underscore symbol '_' matches any symbol, to match it's
     * actual value we need to escape it, but since DAO methos use regexp replacement for
     * query building at first we have to replace '_' with some temporary parameter
     * @param query search LIKE query
     * @return query where underscore is replaced with '@_@' parameter
     */
    public String replaceUnderscoreWithParam(String query) {
        return query.replaceAll(UNDERSCORE, UNDERSCORE_PARAMETER);
    }

    /**
     * Actually replaces dummy underscore param with escape backslash
     * @param query with '@_@' parameter
     * @return query where underscore is escaped with backslash
     */
    public String escapeUnderscoreParam(String query) {
        return query.replaceAll(UNDERSCORE_PARAMETER, UNDERSCORE_ESCAPED);
    }

    /**
     * Escapes underscore '_' symbol with backslash
     * @param query from LIKE clause
     * @return query where underscore is escaped with backslash
     */
    public String escapeUnderscore(String query) {
        return query.replaceAll(UNDERSCORE, UNDERSCORE_ESCAPED);
    }

    enum HelperParameters {
        SEQUENCE_NAME,
        LIMIT,
        LIST_ID,
        LIST_VALUE
    }

    public static Array mapListToSqlArray(List<String> list, Connection connection) {
        String[] javaStringArray = list != null ? list.toArray(EMPTY_STRING_ARRAY) : EMPTY_STRING_ARRAY;
        Array sqlArray;
        try {
            sqlArray = connection.createArrayOf(POSTGRE_TYPE_TEXT, javaStringArray);
        } catch (SQLException e) {
            throw new IllegalArgumentException("Cannot convert data to SQL Array");
        }
        return sqlArray;
    }

    public static Array mapListLongToSqlArray(List<Long> list, Connection connection) {
        Long[] javaStringArray = list != null ? list.toArray(EMPTY_LONG_ARRAY) : EMPTY_LONG_ARRAY;
        Array sqlArray;
        try {
            sqlArray = connection.createArrayOf(POSTGRE_TYPE_BIGINT, javaStringArray);
        } catch (SQLException e) {
            throw new IllegalArgumentException("Cannot convert data to SQL Array");
        }
        return sqlArray;
    }

    public static Folder fillFolder(Map<Long, Folder> folders, Folder currentFolder) {
        if (currentFolder == null) {
            return null;
        }
        Long parentId = currentFolder.getParentId();
        if (parentId == null) {
            return currentFolder;
        }
        Folder folder = folders.get(parentId);
        Folder newFolder = fillFolder(folders, folder);
        currentFolder.setParent(newFolder);
        return currentFolder;
    }

    public static <T extends AbstractSecuredEntity> ResultSetExtractor<Collection<T>> getFolderTreeExtractor(
            final String entityIdName,
            final String folderIdName,
            final String parentFolderIdName,
            final BiFunction<ResultSet, Folder, T> entityExtractor,
            final BiConsumer<T, Map<Long, Folder>> foldersAggregator) {
        return rs -> {
            Map<Long, Folder> folders = new HashMap<>();
            Set<T> entities = new HashSet<>();
            while (rs.next()) {
                Folder folder = null;

                Long folderId = rs.getLong(folderIdName);
                if (!rs.wasNull()) {
                    folder = new Folder(folderId);
                    folder.setParentId(rs.getLong(parentFolderIdName));
                    folders.putIfAbsent(folderId, folder);
                }

                rs.getLong(entityIdName);
                if (!rs.wasNull()) {
                    T entity = entityExtractor.apply(rs, folder);
                    entities.add(entity);
                }
            }
            entities.forEach(storage -> foldersAggregator.accept(storage, folders));
            return entities;
        };
    }

    @Required
    public void setCreateIdQuery(String createIdQuery) {
        this.createIdQuery = createIdQuery;
    }

    @Required
    public void setCreateIdsQuery(String createIdsQuery) {
        this.createIdsQuery = createIdsQuery;
    }
}
