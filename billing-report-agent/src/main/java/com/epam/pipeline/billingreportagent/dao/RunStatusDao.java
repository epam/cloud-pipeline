package com.epam.pipeline.billingreportagent.dao;

import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;

import java.util.List;

public class RunStatusDao extends NamedParameterJdbcDaoSupport {

    private String loadRunStatusQuery;
    private String loadRunStatusByListQuery;

    public List<RunStatus> loadRunStatus(final Long runId) {
        return getJdbcTemplate().query(loadRunStatusQuery, RunStatusParameters.getRowMapper(), runId);
    }

    public List<RunStatus> loadRunStatus(final List<Long> runIds) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("IDS", runIds);

        return getNamedParameterJdbcTemplate().query(loadRunStatusByListQuery, params,
                RunStatusParameters.getRowMapper());
    }

    enum RunStatusParameters {
        RUN_ID,
        STATUS,
        DATE;

        static MapSqlParameterSource getParameters(RunStatus runStatus) {
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(RUN_ID.name(), runStatus.getRunId());
            params.addValue(STATUS.name(), runStatus.getStatus().getId());
            params.addValue(DATE.name(), runStatus.getTimestamp());
            return params;
        }

        static RowMapper<RunStatus> getRowMapper() {
            return (rs, rowNum) -> {
                RunStatus restartRun = new RunStatus();
                restartRun.setRunId(rs.getLong(RUN_ID.name()));
                restartRun.setStatus(TaskStatus.getById(rs.getLong(STATUS.name())));
                restartRun.setTimestamp(rs.getTimestamp(DATE.name()).toLocalDateTime());

                return restartRun;
            };
        }

    }

    @Required
    public void setLoadRunStatusQuery(final String loadRunStatusQuery) {
        this.loadRunStatusQuery = loadRunStatusQuery;
    }

    @Required
    public void setLoadRunStatusByListQuery(final String loadRunStatusByListQuery) {
        this.loadRunStatusByListQuery = loadRunStatusByListQuery;
    }
}