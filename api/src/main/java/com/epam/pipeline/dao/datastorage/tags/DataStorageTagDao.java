package com.epam.pipeline.dao.datastorage.tags;

import com.epam.pipeline.entity.datastorage.tag.DataStorageObject;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTag;
import com.epam.pipeline.entity.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequiredArgsConstructor
public class DataStorageTagDao extends NamedParameterJdbcDaoSupport {

    private static final String LATEST = "";

    private final String upsertTagQuery;
    private final String copyTagsByPathPatternQuery;
    private final String loadTagQuery;
    private final String loadTagsQuery;
    private final String batchLoadTagsQuery;
    private final String deleteTagsQuery;
    private final String deleteSpecificTagsQuery;
    private final String batchDeleteAllTagsQuery;
    private final String deleteAllTagsByPathPatternQuery;
    
    public List<DataStorageTag> batchUpsert(final Long root, final DataStorageTag... tags) {
        return batchUpsert(root, Arrays.stream(tags));
    }

    public List<DataStorageTag> batchUpsert(final Long root, final List<DataStorageTag> tags) {
        return batchUpsert(root, tags.stream());
    }

    public List<DataStorageTag> batchUpsert(final Long root, final Stream<DataStorageTag> tags) {
        final LocalDateTime now = DateUtils.nowUTC();
        final List<DataStorageTag> upsertingTags = tags
                .map(tag -> tag.withCreatedDate(now))
                .collect(Collectors.toList());
        getNamedParameterJdbcTemplate().batchUpdate(upsertTagQuery, Parameters.getParameters(root, upsertingTags));
        return upsertingTags;
    }

    public List<DataStorageTag> batchLoad(final Long root, final List<String> paths) {
        final MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(Parameters.DATASTORAGE_ROOT_ID.name(), root);
        params.addValue(Parameters.DATASTORAGE_PATH.name(), paths);
        params.addValue(Parameters.DATASTORAGE_VERSION.name(), LATEST);
        return getNamedParameterJdbcTemplate().query(batchLoadTagsQuery, params, Parameters.getRowMapper());
    }

    public void batchDelete(final Long root, final DataStorageObject... objects) {
        batchDelete(root, Arrays.stream(objects));
    }

    public void batchDelete(final Long root, final List<DataStorageObject> objects) {
        batchDelete(root, objects.stream());
    }

    public void batchDelete(final Long root, final Stream<DataStorageObject> objects) {
        getNamedParameterJdbcTemplate().batchUpdate(deleteTagsQuery, Parameters.getParameters(root, objects));
    }

    public void batchDeleteAll(final Long root, final List<String> paths) {
        getNamedParameterJdbcTemplate().update(batchDeleteAllTagsQuery, Parameters.getParameters(root)
            .addValue(Parameters.DATASTORAGE_PATH.name(), paths));
    }

    public DataStorageTag upsert(final Long root, final DataStorageTag tag) {
        final DataStorageTag upsertingTag = tag.withCreatedDate(DateUtils.nowUTC());
        getNamedParameterJdbcTemplate().update(upsertTagQuery, Parameters.getParameters(root, upsertingTag));
        return upsertingTag;
    }

    public void copyFolder(final Long root, final String oldPath, final String newPath) {
        final String oldPathPattern = StringUtils.isNotBlank(oldPath)
                ? String.format("%s/%%", StringUtils.removeEnd(oldPath, "/"))
                : "%%";
        getNamedParameterJdbcTemplate().update(copyTagsByPathPatternQuery, Parameters.getParameters(root)
                .addValue(Parameters.DATASTORAGE_PATH.name(), oldPathPattern)
                .addValue(Parameters.DATASTORAGE_VERSION.name(), LATEST)
                .addValue("OLD_DATASTORAGE_PATH", StringUtils.removeEnd(oldPath, "/"))
                .addValue("NEW_DATASTORAGE_PATH", StringUtils.removeEnd(newPath, "/")));
    }

    public Optional<DataStorageTag> load(final Long root, final DataStorageObject object, final String key) {
        return getNamedParameterJdbcTemplate()
                .query(loadTagQuery,
                        Parameters.getParameters(root, object, key),
                        Parameters.getRowMapper())
                .stream()
                .findFirst();
    }

    public List<DataStorageTag> load(final Long root, final DataStorageObject object) {
        return getNamedParameterJdbcTemplate()
                .query(loadTagsQuery,
                        Parameters.getParameters(root, object),
                        Parameters.getRowMapper());
    }

    public void delete(final Long root, final DataStorageObject object) {
        getNamedParameterJdbcTemplate().update(deleteTagsQuery, Parameters.getParameters(root, object)
                .addValue(Parameters.TAG_KEY.name(), Collections.emptyList()));
    }

    public void delete(final Long root, final DataStorageObject object, final String... keys) {
        delete(root, object, Arrays.asList(keys));
    }

    public void delete(final Long root, final DataStorageObject object, final List<String> keys) {
        getNamedParameterJdbcTemplate().update(deleteSpecificTagsQuery, Parameters.getParameters(root, object)
                .addValue(Parameters.TAG_KEY.name(), keys));
    }
    
    public void deleteAllInFolder(final Long root, final String path) {
        final String pathPattern = StringUtils.isNotBlank(path) 
                ? String.format("%s/%%", StringUtils.removeEnd(path, "/"))
                : "%%";
        getNamedParameterJdbcTemplate().update(deleteAllTagsByPathPatternQuery, Parameters.getParameters(root)
                .addValue(Parameters.DATASTORAGE_PATH.name(), pathPattern));
    }

    enum Parameters {
        DATASTORAGE_ROOT_ID,
        DATASTORAGE_PATH,
        DATASTORAGE_VERSION,
        TAG_KEY,
        TAG_VALUE,
        CREATED_DATE;

        public static MapSqlParameterSource[] getParameters(final Long root, final List<DataStorageTag> tags) {
            return tags.stream()
                    .map(tag -> getParameters(root, tag))
                    .map(params -> params.addValue(DATASTORAGE_ROOT_ID.name(), root))
                    .toArray(MapSqlParameterSource[]::new);
        }

        public static MapSqlParameterSource getParameters(final Long root, final DataStorageTag tag) {
            return getParameters(root, tag.getObject(), tag.getKey())
                    .addValue(TAG_VALUE.name(), tag.getValue())
                    .addValue(CREATED_DATE.name(), tag.getCreatedDate());
        }

        public static MapSqlParameterSource getParameters(final Long root,
                                                          final DataStorageObject object,
                                                          final String key) {
            return getParameters(root, object)
                    .addValue(TAG_KEY.name(), key);
        }

        public static MapSqlParameterSource[] getParameters(final Long root,
                                                            final Stream<DataStorageObject> objects) {
            return objects
                    .map(object -> getParameters(root, object))
                    .toArray(MapSqlParameterSource[]::new);
        }

        public static MapSqlParameterSource getParameters(final Long root, final DataStorageObject object) {
            return getParameters(root)
                    .addValue(DATASTORAGE_PATH.name(), object.getPath())
                    .addValue(DATASTORAGE_VERSION.name(), Optional.ofNullable(object.getVersion()).orElse(LATEST));
        }

        private static MapSqlParameterSource getParameters(final Long root) {
            return new MapSqlParameterSource()
                    .addValue(DATASTORAGE_ROOT_ID.name(), root);
        }

        public static RowMapper<DataStorageTag> getRowMapper() {
            return (rs, rowNum) -> {
                final String path = rs.getString(DATASTORAGE_PATH.name());
                final String version = Optional.of(rs.getString(DATASTORAGE_VERSION.name()))
                        .filter(StringUtils::isNotEmpty).orElse(null);
                final String key = rs.getString(TAG_KEY.name());
                final String value = rs.getString(TAG_VALUE.name());
                final LocalDateTime createdDate = DateUtils.convertEpochMilliToLocalDateTime(
                        rs.getTimestamp(CREATED_DATE.name()).getTime());
                final DataStorageObject object = new DataStorageObject(path, version);
                return new DataStorageTag(object, key, value, createdDate);
            };
        }
    }
}
