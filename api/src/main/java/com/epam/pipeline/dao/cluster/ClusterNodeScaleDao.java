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

package com.epam.pipeline.dao.cluster;

import com.epam.pipeline.dao.DaoHelper;
import com.epam.pipeline.entity.cluster.ClusterNodeScale;
import com.epam.pipeline.entity.pipeline.RunInstance;
import org.apache.commons.collections4.ListUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class ClusterNodeScaleDao extends NamedParameterJdbcDaoSupport {

    @Autowired
    private DaoHelper daoHelper;

    private String clusterNodeScaleSequence;
    private String createClusterNodeScaleQuery;
    private String loadClusterNodeScaleQuery;
    private String loadAllClusterNodeScaleQuery;
    private String updateClusterNodeScaleQuery;
    private String deleteClusterNodeScaleQuery;


    @Transactional(propagation = Propagation.MANDATORY)
    public Long createNextFreeNodeId() {
        return daoHelper.createId(clusterNodeScaleSequence);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void createClusterNodeScale(final ClusterNodeScale clusterNodeScale) {
        final Long nextBatchNodeSpecId = createNextFreeNodeId();
        clusterNodeScale.setId(nextBatchNodeSpecId);

        getNamedParameterJdbcTemplate()
                .update(createClusterNodeScaleQuery, ClusterNodeScaleParameters.getParameters(clusterNodeScale));
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public ClusterNodeScale loadClusterNodeScale(final Long id) {
        List<ClusterNodeScale> result = getJdbcTemplate()
                .query(loadClusterNodeScaleQuery, ClusterNodeScaleParameters.getRowMapper(), id);
        return result.isEmpty() ? null : result.iterator().next();
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<ClusterNodeScale> loadClusterNodeScale() {
        List<ClusterNodeScale> result = getJdbcTemplate()
                .query(loadAllClusterNodeScaleQuery, ClusterNodeScaleParameters.getRowMapper());
        return ListUtils.emptyIfNull(result);
    }


    @Transactional(propagation = Propagation.MANDATORY)
    public ClusterNodeScale updateClusterNodeScale(final ClusterNodeScale clusterNodeScale) {
        final MapSqlParameterSource params = ClusterNodeScaleParameters.getParameters(clusterNodeScale);
        getNamedParameterJdbcTemplate().update(updateClusterNodeScaleQuery, params);
        return clusterNodeScale;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteClusterNodeScale(final Long id) {
        getJdbcTemplate().update(deleteClusterNodeScaleQuery, id);
    }

    enum ClusterNodeScaleParameters {
        ID,
        INSTANCE_TYPE,
        NODE_DISK,
        IS_SPOT,
        CLOUD_REGION_ID,
        NUMBER_OF_INSTANCES;

        static MapSqlParameterSource getParameters(ClusterNodeScale clusterNodeScale) {
            MapSqlParameterSource params = new MapSqlParameterSource();

            params.addValue(ID.name(), clusterNodeScale.getId());
            params.addValue(INSTANCE_TYPE.name(), clusterNodeScale.getInstance().getNodeType());
            params.addValue(NUMBER_OF_INSTANCES.name(), clusterNodeScale.getNumberOfInstances());
            params.addValue(NODE_DISK.name(), clusterNodeScale.getInstance().getNodeDisk());
            params.addValue(CLOUD_REGION_ID.name(), clusterNodeScale.getInstance().getCloudRegionId());
            params.addValue(IS_SPOT.name(), clusterNodeScale.getInstance().getSpot());
            return params;
        }

        static RowMapper<ClusterNodeScale> getRowMapper() {
            return (rs, rowNum) -> {
                ClusterNodeScale clusterNodeScale = new ClusterNodeScale();
                clusterNodeScale.setId(rs.getLong(ID.name()));
                clusterNodeScale.setInstance(getRunInstance(rs));
                clusterNodeScale.setNumberOfInstances(rs.getInt(NUMBER_OF_INSTANCES.name()));
                return clusterNodeScale;
            };
        }

        private static RunInstance getRunInstance(final ResultSet rs) throws SQLException {
            final RunInstance instance = new RunInstance();
            instance.setNodeType(rs.getString(INSTANCE_TYPE.name()));
            instance.setNodeDisk(rs.getInt(NODE_DISK.name()));
            instance.setCloudRegionId(rs.getLong(CLOUD_REGION_ID.name()));
            instance.setSpot(rs.getBoolean(IS_SPOT.name()));
            return instance;
        }
    }

    @Required
    public void setClusterNodeScaleSequence(String clusterNodeScaleSequence) {
        this.clusterNodeScaleSequence = clusterNodeScaleSequence;
    }

    @Required
    public void setCreateClusterNodeScaleQuery(final String createClusterNodeScaleQuery) {
        this.createClusterNodeScaleQuery = createClusterNodeScaleQuery;
    }

    @Required
    public void setLoadClusterNodeScaleQuery(final String loadClusterNodeScaleQuery) {
        this.loadClusterNodeScaleQuery = loadClusterNodeScaleQuery;
    }

    @Required
    public void setLoadAllClusterNodeScaleQuery(final String loadAllClusterNodeScaleQuery) {
        this.loadAllClusterNodeScaleQuery = loadAllClusterNodeScaleQuery;
    }

    @Required
    public void setUpdateClusterNodeScaleQuery(final String updateClusterNodeScaleQuery) {
        this.updateClusterNodeScaleQuery = updateClusterNodeScaleQuery;
    }

    @Required
    public void setDeleteClusterNodeScaleQuery(final String deleteClusterNodeScaleQuery) {
        this.deleteClusterNodeScaleQuery = deleteClusterNodeScaleQuery;
    }
}
