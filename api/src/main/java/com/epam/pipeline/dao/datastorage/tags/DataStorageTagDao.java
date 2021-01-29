package com.epam.pipeline.dao.datastorage.tags;

import com.epam.pipeline.entity.datastorage.tags.DataStorageObject;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTag;
import com.epam.pipeline.entity.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequiredArgsConstructor
public class DataStorageTagDao extends NamedParameterJdbcDaoSupport {

    private static final String LATEST = "";

    private final String upsertTagQuery;
    private final String loadTagQuery;
    private final String loadTagsQuery;
    private final String bulkLoadTagsQuery;
    private final String deleteTagsQuery;
    private final String deleteSpecificTagsQuery;
    
    public List<DataStorageTag> bulkUpsert(final DataStorageTag... tags) {
        return bulkUpsert(Arrays.stream(tags));
    }

    public List<DataStorageTag> bulkUpsert(final List<DataStorageTag> tags) {
        return bulkUpsert(tags.stream());
    }

    public List<DataStorageTag> bulkUpsert(final Stream<DataStorageTag> tags) {
        final LocalDateTime now = DateUtils.nowUTC();
        final List<DataStorageTag> upsertingTags = tags
                .map(tag -> tag.withCreatedDate(now))
                .collect(Collectors.toList());
        getNamedParameterJdbcTemplate().batchUpdate(upsertTagQuery, Parameters.getParameters(upsertingTags));
        return upsertingTags;
    }

    public List<DataStorageTag> bulkLoad(final DataStorageObject... objects) {
        return bulkLoad(Arrays.stream(objects));
    }

    public List<DataStorageTag> bulkLoad(final List<DataStorageObject> objects) {
        return bulkLoad(objects.stream());
    }

    public List<DataStorageTag> bulkLoad(final Stream<DataStorageObject> objects) {
        return objects.collect(
                Collectors.groupingBy(DataStorageObject::getStorageId,
                        Collectors.mapping(DataStorageObject::getPath,
                                Collectors.toList())))
                .entrySet()
                .stream()
                .map(entry -> bulkLoad(entry.getKey(), entry.getValue()))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    public List<DataStorageTag> bulkLoad(final Long storageId, final List<String> paths) {
        final MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(Parameters.DATASTORAGE_ID.name(), storageId);
        params.addValue(Parameters.DATASTORAGE_PATH.name(), paths);
        params.addValue(Parameters.DATASTORAGE_VERSION.name(), Collections.singletonList(LATEST));
        return getNamedParameterJdbcTemplate().query(bulkLoadTagsQuery, params, Parameters.getRowMapper());
    }

    public void bulkDelete(final DataStorageObject... objects) {
        bulkDelete(Arrays.stream(objects));
    }

    public void bulkDelete(final List<DataStorageObject> objects) {
        bulkDelete(objects.stream());
    }

    public void bulkDelete(final Stream<DataStorageObject> objects) {
        getNamedParameterJdbcTemplate().batchUpdate(deleteTagsQuery, Parameters.getParameters(objects));
    }

    public DataStorageTag upsert(final DataStorageTag tag) {
        final DataStorageTag upsertingTag = tag.withCreatedDate(DateUtils.nowUTC());
        getNamedParameterJdbcTemplate().update(upsertTagQuery,
                Parameters.getParameters(upsertingTag));
        return upsertingTag;
    }

    public Optional<DataStorageTag> load(final DataStorageObject object, final String key) {
        return getNamedParameterJdbcTemplate()
                .query(loadTagQuery,
                        Parameters.getParameters(object, key),
                        Parameters.getRowMapper())
                .stream()
                .findFirst();
    }

    public List<DataStorageTag> load(final DataStorageObject object) {
        return getNamedParameterJdbcTemplate()
                .query(loadTagsQuery,
                        Parameters.getParameters(object),
                        Parameters.getRowMapper());
    }

    public void delete(final DataStorageObject object) {
        getNamedParameterJdbcTemplate().update(deleteTagsQuery, Parameters.getParameters(object)
                .addValue(Parameters.TAG_KEY.name(), Collections.emptyList()));
    }

    public void delete(final DataStorageObject object, final String... keys) {
        delete(object, Arrays.asList(keys));
    }

    public void delete(final DataStorageObject object, final List<String> keys) {
        getNamedParameterJdbcTemplate().update(deleteSpecificTagsQuery, Parameters.getParameters(object)
                .addValue(Parameters.TAG_KEY.name(), keys));
    }

    enum Parameters {
        DATASTORAGE_ID,
        DATASTORAGE_PATH,
        DATASTORAGE_VERSION,
        TAG_KEY,
        TAG_VALUE,
        CREATED_DATE;

        static MapSqlParameterSource getParameters(final DataStorageTag tag) {
            return getParameters(tag.getObject(), tag.getKey())
                    .addValue(TAG_VALUE.name(), tag.getValue())
                    .addValue(CREATED_DATE.name(), tag.getCreatedDate());
        }

        static MapSqlParameterSource[] getParameters(final List<DataStorageTag> tags) {
            return tags.stream().map(Parameters::getParameters).toArray(MapSqlParameterSource[]::new);
        }

        static MapSqlParameterSource getParameters(final DataStorageObject object, final String key) {
            return getParameters(object)
                    .addValue(TAG_KEY.name(), key);
        }

        static MapSqlParameterSource getParameters(final DataStorageObject object) {
            return new MapSqlParameterSource()
                    .addValue(DATASTORAGE_ID.name(), object.getStorageId())
                    .addValue(DATASTORAGE_PATH.name(), object.getPath())
                    .addValue(DATASTORAGE_VERSION.name(), Optional.ofNullable(object.getVersion()).orElse(LATEST));
        }

        public static MapSqlParameterSource[] getParameters(final Stream<DataStorageObject> objects) {
            return objects.map(Parameters::getParameters).toArray(MapSqlParameterSource[]::new);
        }

        static RowMapper<DataStorageTag> getRowMapper() {
            return (rs, rowNum) -> {
                final Long storageId = rs.getLong(DATASTORAGE_ID.name());
                final String path = rs.getString(DATASTORAGE_PATH.name());
                final String version = Optional.of(rs.getString(DATASTORAGE_VERSION.name()))
                        .filter(StringUtils::isNotEmpty).orElse(null);
                final String key = rs.getString(TAG_KEY.name());
                final String value = rs.getString(TAG_VALUE.name());
                final LocalDateTime createdDate = DateUtils.convertEpochMilliToLocalDateTime(
                        rs.getTimestamp(CREATED_DATE.name()).getTime());
                final DataStorageObject object = new DataStorageObject(storageId, path, version);
                return new DataStorageTag(object, key, value, createdDate);
            };
        }
    }
}
