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

package com.epam.pipeline.dao.datastorage;

import com.epam.pipeline.dao.DaoHelper;
import com.epam.pipeline.entity.datastorage.FileShareMount;
import com.epam.pipeline.entity.datastorage.MountType;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class FileShareMountDao extends NamedParameterJdbcDaoSupport {

    private final DaoHelper daoHelper;

    @Setter(onMethod_={@Required}) private String loadShareMountByIdQuery;
    @Setter(onMethod_={@Required}) private String loadAllShareMountByRegionIdQuery;
    @Setter(onMethod_={@Required}) private String createShareMountQuery;
    @Setter(onMethod_={@Required}) private String updateShareMountQuery;
    @Setter(onMethod_={@Required}) private String deleteShareMountQuery;
    @Setter(onMethod_={@Required}) private String fileShareMountSequence;


    public Optional<FileShareMount> loadById(final long id) {
        return getJdbcTemplate()
                .query(loadShareMountByIdQuery, FileShareMountDao.MountShareParameters.getRowMapper(), id)
                .stream()
                .findFirst();
    }

    public List<FileShareMount> loadAllByRegionId(final long regionId) {
        return getJdbcTemplate().query(loadAllShareMountByRegionIdQuery,
                FileShareMountDao.MountShareParameters.getRowMapper(), regionId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Long createShareMountId() {
        return daoHelper.createId(fileShareMountSequence);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public FileShareMount create(final FileShareMount fileShareMount) {
        fileShareMount.setId(createShareMountId());
        getNamedParameterJdbcTemplate()
                .update(createShareMountQuery, FileShareMountDao.MountShareParameters.getParameters(fileShareMount));
        return fileShareMount;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public FileShareMount update(final FileShareMount fileShareMount) {
        getNamedParameterJdbcTemplate()
                .update(updateShareMountQuery, FileShareMountDao.MountShareParameters.getParameters(fileShareMount));
        return fileShareMount;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteById(final long id) {
        getJdbcTemplate().update(deleteShareMountQuery, id);
    }

    private enum  MountShareParameters {
        ID,
        REGION_ID,
        MOUNT_ROOT,
        MOUNT_TYPE,
        MOUNT_OPTIONS;

        private static MapSqlParameterSource getParameters(final FileShareMount fileShareMount) {
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(ID.name(), fileShareMount.getId());
            params.addValue(REGION_ID.name(), fileShareMount.getRegionId());
            params.addValue(MOUNT_ROOT.name(), fileShareMount.getMountRoot());
            params.addValue(MOUNT_TYPE.name(), fileShareMount.getMountType().name());
            params.addValue(MOUNT_OPTIONS.name(), fileShareMount.getMountOptions());
            return params;
        }

        public static RowMapper<FileShareMount> getRowMapper() {
            return (rs, rowNum) -> {
                FileShareMount result = new FileShareMount();
                result.setId(rs.getLong(ID.name()));
                result.setRegionId(rs.getLong(REGION_ID.name()));
                result.setMountRoot(rs.getString(MOUNT_ROOT.name()));
                result.setMountType(MountType.valueOf(rs.getString(MOUNT_TYPE.name())));
                result.setMountOptions(rs.getString(MOUNT_OPTIONS.name()));
                return result;
            };
        }
    }
}
