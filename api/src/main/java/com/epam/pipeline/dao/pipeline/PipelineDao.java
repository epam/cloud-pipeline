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

package com.epam.pipeline.dao.pipeline;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.regex.Pattern;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.epam.pipeline.dao.DaoHelper;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.RepositoryType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class PipelineDao extends NamedParameterJdbcDaoSupport {
    private Pattern limitPattern = Pattern.compile("@LIMIT@");
    private Pattern offsetPattern = Pattern.compile("@OFFSET@");

    @Autowired
    private DaoHelper daoHelper;

    private String pipelineSequence;
    private String createPipelineQuery;
    private String updatePipelineQuery;
    private String loadAllPipelinesQuery;
    private String deletePipelineQuery;
    private String loadPipelineByIdQuery;
    private String loadPipelineByNameQuery;
    private String loadPipelineByRepoUrlQuery;
    private String loadRootPipelinesQuery;
    private String updatePipelineLocksQuery;
    private String loadAllPipelinesWithParentsQuery;
    private String loadPipelinesCountQuery;
    private String loadPipelineWithParentsQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    public Long createPipelineId() {
        return daoHelper.createId(pipelineSequence);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void createPipeline(Pipeline pipeline) {
        pipeline.setId(createPipelineId());
        getNamedParameterJdbcTemplate().update(createPipelineQuery, PipelineParameters.getParameters(pipeline));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updatePipeline(Pipeline pipeline) {
        getNamedParameterJdbcTemplate().update(updatePipelineQuery, PipelineParameters.getParameters(pipeline));
    }

    public List<Pipeline> loadAllPipelines() {
        return getNamedParameterJdbcTemplate().query(loadAllPipelinesQuery,
                PipelineParameters.getRowMapper());
    }

    public Pipeline loadPipeline(Long id) {
        List<Pipeline> items = getJdbcTemplate().query(loadPipelineByIdQuery, PipelineParameters
                .getRowMapper(), id);

        return !items.isEmpty() ? items.get(0) : null;
    }

    public Pipeline loadPipelineByName(String name) {
        List<Pipeline> items = getJdbcTemplate().query(loadPipelineByNameQuery, PipelineParameters
                .getRowMapper(), name.toLowerCase());

        return !items.isEmpty() ? items.get(0) : null;
    }

    public Optional<Pipeline> loadPipelineByRepoUrl(String url) {
        return getJdbcTemplate().query(loadPipelineByRepoUrlQuery, PipelineParameters
                .getRowMapper(), url).stream().findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deletePipeline(Long id) {
        getJdbcTemplate().update(deletePipelineQuery, id);
    }

    public List<Pipeline> loadRootPipelines() {
        return getNamedParameterJdbcTemplate().query(loadRootPipelinesQuery,
                PipelineParameters.getRowMapper());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateLocks(List<Long> pipelineIds, boolean isLocked) {
        daoHelper.updateLocks(updatePipelineLocksQuery, pipelineIds, isLocked);
    }

    public Set<Pipeline> loadAllPipelinesWithParents(Integer pageNum, Integer pageSize) {
        String query = limitPattern.matcher(loadAllPipelinesWithParentsQuery)
                .replaceFirst(pageSize == null ? "ALL" : pageSize.toString());
        int size = pageSize == null ? 0 : pageSize;
        int offset = pageNum == null ? 0 : (pageNum - 1) * size;
        query = offsetPattern.matcher(query).replaceFirst(String.valueOf(offset));
        return new HashSet<>(getJdbcTemplate().query(query, PipelineParameters.getPipelineWithFolderTreeExtractor()));
    }

    public Integer loadPipelinesCount() {
        return getNamedParameterJdbcTemplate().queryForObject(loadPipelinesCountQuery, new MapSqlParameterSource(),
                Integer.class);
    }

    public Pipeline loadPipelineWithParents(final Long id) {
        return getJdbcTemplate().query(loadPipelineWithParentsQuery,
                PipelineParameters.getPipelineWithFolderTreeExtractor(), id)
                .stream()
                .findFirst()
                .orElse(null);
    }

    enum PipelineParameters {
        PIPELINE_ID,
        PIPELINE_NAME,
        DESCRIPTION,
        REPOSITORY,
        FOLDER_ID,
        CREATED_DATE,
        OWNER,
        REPOSITORY_TOKEN,
        REPOSITORY_TYPE,
        PIPELINE_LOCKED,
        PARENT_FOLDER_ID;

        static MapSqlParameterSource getParameters(Pipeline pipeline) {
            MapSqlParameterSource params = new MapSqlParameterSource();

            params.addValue(PIPELINE_ID.name(), pipeline.getId());
            params.addValue(PIPELINE_NAME.name(), pipeline.getName());
            params.addValue(DESCRIPTION.name(), pipeline.getDescription());
            params.addValue(REPOSITORY.name(), pipeline.getRepository());
            params.addValue(FOLDER_ID.name(), pipeline.getParentFolderId());
            params.addValue(CREATED_DATE.name(), pipeline.getCreatedDate());
            params.addValue(OWNER.name(), pipeline.getOwner());
            params.addValue(REPOSITORY_TOKEN.name(), pipeline.getRepositoryToken());
            params.addValue(REPOSITORY_TYPE.name(), pipeline.getRepositoryType());
            params.addValue(PIPELINE_LOCKED.name(), pipeline.isLocked());
            return params;
        }

        static RowMapper<Pipeline> getRowMapper() {
            return (rs, rowNum) -> {
                Pipeline pipeline = basicInitPipeline(rs);
                Long folderId = rs.getLong(FOLDER_ID.name());
                if (!rs.wasNull()) {
                    pipeline.setParentFolderId(folderId);
                }
                return pipeline;
            };
        }

        static ResultSetExtractor<Collection<Pipeline>> getPipelineWithFolderTreeExtractor() {
            return DaoHelper.getFolderTreeExtractor(PIPELINE_ID.name(), FOLDER_ID.name(), PARENT_FOLDER_ID.name(),
                    PipelineParameters::getPipeline, PipelineParameters::fillFolders);
        }

        private static void fillFolders(final Pipeline pipeline, final Map<Long, Folder> folders) {
            pipeline.setParent(DaoHelper.fillFolder(folders, pipeline.getParent()));
        }

        private static Pipeline getPipeline(final ResultSet rs, final Folder folder) {
            try {
                Pipeline pipeline = basicInitPipeline(rs);
                if (folder != null) {
                    pipeline.setParent(folder);
                    pipeline.setParentFolderId(folder.getId());
                }
                return pipeline;
            } catch (SQLException e) {
                throw new IllegalArgumentException();
            }
        }

        private static Pipeline basicInitPipeline(ResultSet rs) throws SQLException {
            Pipeline pipeline = new Pipeline();
            pipeline.setId(rs.getLong(PIPELINE_ID.name()));
            pipeline.setName(rs.getString(PIPELINE_NAME.name()));
            pipeline.setDescription(rs.getString(DESCRIPTION.name()));
            pipeline.setRepository(rs.getString(REPOSITORY.name()));
            pipeline.setOwner(rs.getString(OWNER.name()));
            pipeline.setRepositoryToken(rs.getString(REPOSITORY_TOKEN.name()));
            pipeline.setRepositoryType(RepositoryType.getById(rs.getLong(REPOSITORY_TYPE.name())));
            pipeline.setLocked(rs.getBoolean(PIPELINE_LOCKED.name()));
            pipeline.setCreatedDate(new Date(rs.getTimestamp(CREATED_DATE.name()).getTime()));
            return pipeline;
        }

    }

    @Required
    public void setPipelineSequence(String pipelineSequence) {
        this.pipelineSequence = pipelineSequence;
    }

    @Required
    public void setCreatePipelineQuery(String createPipelineQuery) {
        this.createPipelineQuery = createPipelineQuery;
    }

    @Required
    public void setUpdatePipelineQuery(String updatePipelineQuery) {
        this.updatePipelineQuery = updatePipelineQuery;
    }

    @Required
    public void setLoadAllPipelinesQuery(String loadAllPipelinesQuery) {
        this.loadAllPipelinesQuery = loadAllPipelinesQuery;
    }

    @Required
    public void setDeletePipelineQuery(String deletePipelineQuery) {
        this.deletePipelineQuery = deletePipelineQuery;
    }

    @Required
    public void setLoadPipelineByIdQuery(String loadPipelineByIdQuery) {
        this.loadPipelineByIdQuery = loadPipelineByIdQuery;
    }

    @Required
    public void setLoadPipelineByNameQuery(String loadPipelineByNameQuery) {
        this.loadPipelineByNameQuery = loadPipelineByNameQuery;
    }

    @Required
    public void setLoadPipelineByRepoUrlQuery(String loadPipelineByRepoUrlQuery) {
        this.loadPipelineByRepoUrlQuery = loadPipelineByRepoUrlQuery;
    }

    @Required
    public void setLoadRootPipelinesQuery(String loadRootPipelinesQuery) {
        this.loadRootPipelinesQuery = loadRootPipelinesQuery;
    }

    @Required
    public void setUpdatePipelineLocksQuery(String updatePipelineLocksQuery) {
        this.updatePipelineLocksQuery = updatePipelineLocksQuery;
    }

    @Required
    public void setLoadAllPipelinesWithParentsQuery(String loadAllPipelinesWithParentsQuery) {
        this.loadAllPipelinesWithParentsQuery = loadAllPipelinesWithParentsQuery;
    }

    @Required
    public void setLoadPipelinesCountQuery(String loadPipelinesCountQuery) {
        this.loadPipelinesCountQuery = loadPipelinesCountQuery;
    }

    @Required
    public void setLoadPipelineWithParentsQuery(String loadPipelineWithParentsQuery) {
        this.loadPipelineWithParentsQuery = loadPipelineWithParentsQuery;
    }
}
