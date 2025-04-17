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
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
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
    private final String findSidsWithStoragePathPermissionsQuery;
    private final String findClosestStorageFolderPermissionQuery;
    private final String loadClosetsStoragePathPermissionsByPrefixQuery;
    private final String deleteStoragePathPermissionsByFilePathQuery;
    private final String deleteStoragePathPermissionsByFolderPathQuery;
    private final String loadStoragePathPermissionsByPathQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    public void batchInsert(final List<StoragePathPermissions> entities, final Long storageId,
                            final String sidId, final boolean principal) {
        final MapSqlParameterSource[] parameters = entities.stream()
                .map(entity -> Parameters.getParameters(entity, storageId, sidId, principal))
                .toArray(MapSqlParameterSource[]::new);
        getNamedParameterJdbcTemplate().batchUpdate(insertStoragePathPermissionsQuery, parameters);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void batchInsert(final List<StoragePathPermissions> entities, final Long storageId) {
        final MapSqlParameterSource[] parameters = entities.stream()
                .map(entity -> Parameters.getParameters(entity, storageId, entity.getSidName(), entity.isPrincipal()))
                .toArray(MapSqlParameterSource[]::new);
        getNamedParameterJdbcTemplate().batchUpdate(insertStoragePathPermissionsQuery, parameters);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void batchDeleteByPath(final List<StoragePathPermissions> entities, final Long storageId) {
        final MapSqlParameterSource[] filesParams = entities.stream()
                .filter(entity -> StringUtils.isNotBlank(entity.getFileName()))
                .map(entity ->  new MapSqlParameterSource()
                        .addValue(Parameters.STORAGE_ID.name(), storageId)
                        .addValue(Parameters.FOLDER_PATH.name(), entity.getFolderPath())
                        .addValue(Parameters.FILE_NAME.name(), entity.getFileName()))
                .toArray(MapSqlParameterSource[]::new);
        if (ArrayUtils.isNotEmpty(filesParams)) {
            getNamedParameterJdbcTemplate().batchUpdate(deleteStoragePathPermissionsByFilePathQuery, filesParams);
        }
        final MapSqlParameterSource[] foldersParams = entities.stream()
                .filter(entity -> StringUtils.isBlank(entity.getFileName()))
                .map(entity ->  new MapSqlParameterSource()
                        .addValue(Parameters.STORAGE_ID.name(), storageId)
                        .addValue(Parameters.FOLDER_PATH.name(), entity.getFolderPath()))
                .toArray(MapSqlParameterSource[]::new);
        if (ArrayUtils.isNotEmpty(foldersParams)) {
            getNamedParameterJdbcTemplate().batchUpdate(deleteStoragePathPermissionsByFolderPathQuery, foldersParams);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteForStorageAndSids(final Long storageId, final List<SidImpl> sids) {
        final String query = WHERE_PATTERN.matcher(deleteStoragePathPermissionsQuery)
                .replaceFirst(buildWhere(sids, null, false));
        getJdbcTemplate().update(query, storageId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteForStorageId(final Long storageId) {
        getJdbcTemplate().update(deleteStoragePathPermissionsByStorageIdQuery, storageId);
    }

    public List<StoragePathPermissions> loadByPath(final Long storageId, final String folderPath,
                                                   final String fileName) {
        final MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(Parameters.STORAGE_ID.name(), storageId)
                .addValue(Parameters.FOLDER_PATH.name(), folderPath);
        final String query = pathWhere(loadStoragePathPermissionsByPathQuery, fileName);
        return getNamedParameterJdbcTemplate().query(query, params, Parameters.getRowMapper());
    }

    public List<StoragePathPermissions> findByStorageAndSids(final Long storageId, final List<SidImpl> sids) {
        final String query = emptyLimit(WHERE_PATTERN.matcher(loadStoragePathPermissionsQuery)
                .replaceFirst(buildWhere(sids, null, false)));
        return getJdbcTemplate().query(query, Parameters.getRowMapper(), storageId);
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

    public Optional<StoragePathPermissions> findClosestParentFolderPermission(final Long storageId,
                                                                              final List<SidImpl> sids,
                                                                              final List<String> parentFolders) {
        final String query = limitFirst(WHERE_PATTERN.matcher(findClosestStorageFolderPermissionQuery)
                .replaceFirst(buildWhere(sids, parentFolders, true)));
        return getJdbcTemplate().query(query, Parameters.getRowMapper(), storageId).stream()
                .findFirst();
    }

    public List<StoragePathPermissions> findByPrefix(final Long storageId, final List<SidImpl> sids,
                                                     final String prefix) {
        final MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue(Parameters.STORAGE_ID.name(), storageId)
                .addValue(Parameters.FOLDER_PATH.name(), prefix + DaoHelper.POSTGRES_LIKE_CHARACTER);
        final String query = WHERE_PATTERN.matcher(loadClosetsStoragePathPermissionsByPrefixQuery)
                .replaceFirst(buildWhere(sids, null, false));
        return getNamedParameterJdbcTemplate()
                .query(query, parameters, Parameters.getRowMapper());
    }

    public List<SidImpl> findSids(final Long storageId) {
        return getJdbcTemplate()
                .query(findSidsWithStoragePathPermissionsQuery, Parameters.getSidsRowMapper(), storageId);
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
        whereBuilder.append("p.folder_path = '")
                .append(folderPath)
                .append("' AND p.file_name = '")
                .append(fileName)
                .append("' ");
    }

    private void andPathInClause(final List<String> prefixes, final StringBuilder whereBuilder) {
        whereBuilder.append(" AND p.folder_path IN (")
                .append(prefixes.stream().map(s -> "'" + s + "'").collect(Collectors.joining(", ")))
                .append(')');
    }

    private void andSidsInClause(final List<SidImpl> sids, final StringBuilder whereBuilder) {
        whereBuilder.append(" AND (")
                .append(sids.stream()
                        .map(sid -> "p.sid_name = '" + sid.getName() +
                                "' AND p.principal = " + (sid.isPrincipal() ? "TRUE" : "FALSE"))
                        .collect(Collectors.joining(" OR ")))
                .append(')');
    }

    public enum Parameters {
        STORAGE_ID,
        FOLDER_PATH,
        FILE_NAME,
        SID_NAME,
        PRINCIPAL,
        MASK;

        static MapSqlParameterSource getParameters(final StoragePathPermissions entity, final Long storageId,
                                                   final String sidId, final boolean principal) {
            return new MapSqlParameterSource()
                    .addValue(STORAGE_ID.name(), storageId)
                    .addValue(FOLDER_PATH.name(), entity.getFolderPath())
                    .addValue(FILE_NAME.name(), entity.getFileName())
                    .addValue(SID_NAME.name(), sidId)
                    .addValue(PRINCIPAL.name(), principal)
                    .addValue(MASK.name(), entity.getMask());
        }

        static RowMapper<StoragePathPermissions> getRowMapper() {
            return (rs, rowNum) -> StoragePathPermissions.builder()
                    .folderPath(rs.getString(FOLDER_PATH.name()))
                    .fileName(rs.getString(FILE_NAME.name()))
                    .mask(rs.getInt(MASK.name()))
                    .sidName(rs.getString(SID_NAME.name()))
                    .principal(rs.getBoolean(PRINCIPAL.name()))
                    .build();
        }

        static RowMapper<SidImpl> getSidsRowMapper() {
            return (rs, rowNum) -> {
                final SidImpl sid = new SidImpl();
                sid.setName(rs.getString(SID_NAME.name()));
                sid.setPrincipal(rs.getBoolean(PRINCIPAL.name()));
                return sid;
            };
        }
    }

    private String emptyLimit(final String query) {
        return LIMIT_PATTERN.matcher(query).replaceFirst(Strings.EMPTY);
    }

    private String limitFirst(final String query) {
        return LIMIT_PATTERN.matcher(query).replaceFirst(" LIMIT 1");
    }

    private String pathWhere(final String query, final String fileName) {
        return WHERE_PATTERN.matcher(query).replaceFirst(" p.file_name " +
                (StringUtils.isNotBlank(fileName) ? "= '" + fileName + "' " : "IS NULL "));
    }
}
