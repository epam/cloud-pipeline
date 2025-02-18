/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dao.datastorage.permissions;

import com.epam.pipeline.dao.DaoHelper;
import com.epam.pipeline.dto.datastorage.permissions.StoragePathPermissions;
import com.epam.pipeline.entity.user.SidImpl;
import joptsimple.internal.Strings;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class StoragePathPermissionsDao extends NamedParameterJdbcDaoSupport {
    private static final Pattern WHERE_PATTERN = Pattern.compile("@WHERE@");
    private static final Pattern LIMIT_PATTERN = Pattern.compile("@LIMIT@");
    private static final int STRING_BUFFER_SIZE = 20;

    private final String insertStoragePathPermissionsQuery;
    private final String deleteStoragePathPermissionsQuery;
    private final String loadStoragePathPermissionsQuery;
    private final String deleteStoragePathPermissionsByStorageIdQuery;
    private final String findStoragePathPermissionsByPrefixQuery;
    private final String countStoragePathPermissionsQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    public void batchInsert(final List<StoragePathPermissions> entities, final Long storageId,
                            final String sidId, final boolean principal) {
        final MapSqlParameterSource[] parameters = entities.stream()
                .map(entity -> Parameters.getParameters(entity, storageId, sidId, principal))
                .toArray(MapSqlParameterSource[]::new);
        getNamedParameterJdbcTemplate().batchUpdate(insertStoragePathPermissionsQuery, parameters);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteForStorageAndSid(final Long storageId, final String sidIdentifier, final boolean principal) {
        getNamedParameterJdbcTemplate().update(deleteStoragePathPermissionsQuery,
                Parameters.getParameters(storageId, sidIdentifier, principal));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteForStorageId(final Long storageId) {
        getJdbcTemplate().update(deleteStoragePathPermissionsByStorageIdQuery, storageId);
    }

    public List<StoragePathPermissions> findByStorageAndSids(final Long storageId, final List<SidImpl> sids) {
        final String query = emptyLimit(WHERE_PATTERN.matcher(loadStoragePathPermissionsQuery)
                .replaceFirst(buildWhere(sids, null, false)));
        return getJdbcTemplate().query(query, Parameters.getRowMapper(), storageId);
    }

    public Optional<StoragePathPermissions> findClosestFolderPermission(final Long storageId,
                                                                        final List<SidImpl> sids,
                                                                        final List<String> paths) {
        final String query = limitFirst(
                WHERE_PATTERN.matcher(loadStoragePathPermissionsQuery)
                        .replaceFirst(buildWhere(sids, paths, true)));
        return getJdbcTemplate().query(query, Parameters.getRowMapper(), storageId).stream()
                .findFirst();
    }

    public Optional<StoragePathPermissions> findClosestFilePermission(final Long storageId,
                                                                      final List<SidImpl> sids,
                                                                      final List<String> tokens,
                                                                      final String folderPath,
                                                                      final String fileName) {
        final String query = limitFirst(WHERE_PATTERN.matcher(loadStoragePathPermissionsQuery)
                .replaceFirst(buildWhereForFiles(sids, tokens, folderPath, fileName)));
        return getJdbcTemplate().query(query, Parameters.getRowMapper(), storageId).stream()
                .findFirst();
    }

    public int countParentFoldersByStorageAndSids(final Long storageId, final List<SidImpl> sids,
                                                  final List<String> parentFolders) {
        final String query = WHERE_PATTERN.matcher(countStoragePathPermissionsQuery)
                .replaceFirst(buildWhere(sids, parentFolders, true));
        return getJdbcTemplate().queryForObject(query, Integer.class, storageId);
    }

    public List<StoragePathPermissions> findByPrefix(final Long storageId, final List<SidImpl> sids,
                                                     final String prefix) {
        final MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue(Parameters.STORAGE_ID.name(), storageId)
                .addValue(Parameters.PATH.name(), prefix + DaoHelper.POSTGRES_LIKE_CHARACTER);
        final String query = WHERE_PATTERN.matcher(findStoragePathPermissionsByPrefixQuery)
                .replaceFirst(buildWhere(sids, null, false));
        return getNamedParameterJdbcTemplate()
                .query(query, parameters, Parameters.getRowMapper());
    }

    private String buildWhere(final List<SidImpl> sids, final List<String> prefixes, final boolean foldersOnly) {
        final StringBuilder whereBuilder = new StringBuilder(STRING_BUFFER_SIZE);
        if (CollectionUtils.isNotEmpty(sids)) {
            andSidsInClause(sids, whereBuilder);
        }
        if (CollectionUtils.isNotEmpty(prefixes)) {
            andPathInClause(prefixes, whereBuilder);
        }
        if (foldersOnly) {
            whereBuilder.append(" AND p.file_name IS NULL ");
        }
        return whereBuilder.toString();
    }

    private String buildWhereForFiles(final List<SidImpl> sids, final List<String> prefixes, final String folderPath,
                                      final String fileName) {
        final StringBuilder whereBuilder = new StringBuilder(STRING_BUFFER_SIZE);
        if (CollectionUtils.isNotEmpty(sids)) {
            andSidsInClause(sids, whereBuilder);
        }
        if (CollectionUtils.isNotEmpty(prefixes)) {
            whereBuilder.append(" AND (");
            filePathExactMatchClause(folderPath, fileName, whereBuilder);
            whereBuilder.append(" OR ");
            folderInClause(prefixes, whereBuilder);
            whereBuilder.append(')');
        }
        return whereBuilder.toString();
    }

    private void folderInClause(final List<String> prefixes, final StringBuilder whereBuilder) {
        whereBuilder.append("p.file_name IS NULL ");
        andPathInClause(prefixes, whereBuilder);
    }

    private void filePathExactMatchClause(final String folderPath, final String fileName,
                                          final StringBuilder whereBuilder) {
        whereBuilder.append("p.path = '")
                .append(folderPath)
                .append("' AND p.file_name = '")
                .append(fileName)
                .append("' ");
    }

    private void andPathInClause(final List<String> prefixes, final StringBuilder whereBuilder) {
        whereBuilder.append(" AND p.path IN (")
                .append(prefixes.stream().map(s -> "'" + s + "'").collect(Collectors.joining(", ")))
                .append(')');
    }

    private void andSidsInClause(final List<SidImpl> sids, final StringBuilder whereBuilder) {
        whereBuilder.append(" AND (")
                .append(sids.stream()
                        .map(sid -> "p.sid_id = '" + sid.getName() +
                                "' AND p.principal = " + (sid.isPrincipal() ? "TRUE" : "FALSE"))
                        .collect(Collectors.joining(" OR ")))
                .append(')');
    }

    public enum Parameters {
        STORAGE_ID,
        PATH,
        FILE_NAME,
        SID_ID,  // TODO: choose better name
        PRINCIPAL,
        MASK;

        static MapSqlParameterSource getParameters(final StoragePathPermissions entity, final Long storageId,
                                                   final String sidId, final boolean principal) {
            return new MapSqlParameterSource()
                    .addValue(STORAGE_ID.name(), storageId)
                    .addValue(PATH.name(), entity.getFolderPath())
                    .addValue(FILE_NAME.name(), entity.getFileName())
                    .addValue(SID_ID.name(), sidId)
                    .addValue(PRINCIPAL.name(), principal)
                    .addValue(MASK.name(), entity.getMask());
        }

        static MapSqlParameterSource getParameters(final Long storageId, final String sidIdentifier,
                                                   final boolean principal) {
            return new MapSqlParameterSource()
                    .addValue(STORAGE_ID.name(), storageId)
                    .addValue(SID_ID.name(), sidIdentifier)
                    .addValue(PRINCIPAL.name(), principal);
        }

        static RowMapper<StoragePathPermissions> getRowMapper() {
            return (rs, rowNum) -> StoragePathPermissions.builder()
                    .folderPath(rs.getString(PATH.name()))
                    .fileName(rs.getString(FILE_NAME.name()))
                    .mask(rs.getInt(MASK.name()))
                    .build();
        }
    }

    private String emptyLimit(final String query) {
        return LIMIT_PATTERN.matcher(query).replaceFirst(Strings.EMPTY);
    }

    private String limitFirst(final String query) {
        return LIMIT_PATTERN.matcher(query).replaceFirst(" LIMIT 1");
    }
}
