/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.search;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.config.Constants;
import com.epam.pipeline.controller.vo.search.ElasticSearchRequest;
import com.epam.pipeline.controller.vo.search.FacetedSearchRequest;
import com.epam.pipeline.controller.vo.search.ScrollingParameters;
import com.epam.pipeline.controller.vo.search.SearchRequestSort;
import com.epam.pipeline.controller.vo.search.SearchRequestSortOrder;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.search.SearchDocumentType;
import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.exception.search.SearchException;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.utils.CommonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.elasticsearch.action.search.MultiSearchRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.common.Strings;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.epam.pipeline.manager.preference.SystemPreferences.SEARCH_ELASTIC_ALLOWED_GROUPS_FIELD;
import static com.epam.pipeline.manager.preference.SystemPreferences.SEARCH_ELASTIC_ALLOWED_USERS_FIELD;
import static com.epam.pipeline.manager.preference.SystemPreferences.SEARCH_ELASTIC_DENIED_GROUPS_FIELD;
import static com.epam.pipeline.manager.preference.SystemPreferences.SEARCH_ELASTIC_DENIED_USERS_FIELD;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchRequestBuilder {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern(Constants.FMT_ISO_LOCAL_DATE);
    private static final String STORAGE_SIZE_BY_TIER_AGG_NAME = "sizeSumByTier";
    private static final String STORAGE_SIZE_AGG_NAME = "sizeSum";

    private static final String NAME_FIELD = "id";
    private static final String ES_FILE_INDEX_PATTERN = "cp-%s-file-%d";
    private static final String ES_DOC_ID_FIELD = "_id";
    private static final String ES_DOC_SCORE_FIELD = "_score";
    private static final String ES_SORT_MISSING_LAST = "_last";
    private static final String ES_SORT_MISSING_FIRST = "_first";
    private static final String SEARCH_HIDDEN = "is_hidden";
    private static final String SEARCH_DELETED = "is_deleted";
    private static final String INDEX_WILDCARD_PREFIX = "*";
    private static final String ES_KEYWORD_TYPE = "keyword";
    private static final String ES_DATE_TYPE = "date";
    private static final String ES_LONG_TYPE = "long";
    private static final String ES_STORAGE_ID_FIELD = "storage_id";
    private static final String ES_STORAGE_NAME_FIELD = "storage_name";
    private static final String STORAGE_CLASS_FIELD = "storage_class";

    private final PreferenceManager preferenceManager;
    private final AuthManager authManager;
    private final UserManager userManager;
    private final MessageHelper messageHelper;

    public SearchRequest buildRequest(final ElasticSearchRequest searchRequest,
                                      final String typeFieldName,
                                      final String aggregation,
                                      final Set<String> metadataSourceFields) {
        final SearchSourceBuilder searchSource = new SearchSourceBuilder()
                .query(getQuery(searchRequest))
                .fetchSource(buildSourceFields(typeFieldName, metadataSourceFields), Strings.EMPTY_ARRAY)
                .size(searchRequest.getPageSize());

        final List<SearchRequestSort> sorts = resolveSorts(searchRequest.getSorts());
        buildSorts(sorts, isScrollingBackwards(searchRequest.getScrollingParameters()))
                .forEach(searchSource::sort);

        if (Objects.isNull(searchRequest.getScrollingParameters())) {
            searchSource.from(searchRequest.getOffset());
        } else {
            Assert.notNull(searchRequest.getScrollingParameters().getDocId(), messageHelper.getMessage(
                    MessageConstants.ERROR_SEARCH_SCROLLING_PARAMETER_DOC_ID_MISSING));
            searchSource.searchAfter(buildSearchAfterArguments(sorts, searchRequest.getScrollingParameters()));
        }

        if (searchRequest.isHighlight()) {
            addHighlighterToSource(searchSource);
        }

        if (searchRequest.isAggregate()) {
            searchSource.aggregation(
                    AggregationBuilders.terms(aggregation)
                            .field(typeFieldName)
                            .size(SearchDocumentType.values().length));
        }

        log.debug("Search request: {} ", searchSource);

        return new SearchRequest(buildIndexNames(searchRequest.getFilterTypes()))
                .indicesOptions(IndicesOptions.lenientExpandOpen())
                .source(searchSource);
    }

    public MultiSearchRequest buildStorageSumRequest(final Long storageId, final DataStorageType storageType,
                                                     final String path, final boolean allowNoIndex,
                                                     final Set<String> storageSizeMasks,
                                                     final Set<String> storageClasses, final boolean allowVersions) {
        final String searchIndex = String.format(
                ES_FILE_INDEX_PATTERN, storageType.toString().toLowerCase(), storageId
        );
        final MultiSearchRequest multiSearchRequest = new MultiSearchRequest();
        multiSearchRequest.add(buildSumAggRequest(
                searchIndex, path, storageClasses, allowNoIndex, null, allowVersions));
        if (CollectionUtils.isNotEmpty(storageSizeMasks)) {
            multiSearchRequest.add(buildSumAggRequest(
                    searchIndex, path, storageClasses, allowNoIndex, storageSizeMasks, allowVersions));
        }
        return multiSearchRequest;
    }

    private SearchRequest buildSumAggRequest(final String searchIndex, final String path,
                                             final Set<String> storageClasses, final boolean allowNoIndex,
                                             final Set<String> storageSizeMasks, final boolean allowVersions) {
        final BoolQueryBuilder fileFilteringQuery = QueryBuilders.boolQuery();
        CollectionUtils.emptyIfNull(storageSizeMasks)
            .forEach(mask -> fileFilteringQuery.mustNot(QueryBuilders.wildcardQuery(NAME_FIELD, mask)));
        if (StringUtils.isNotBlank(path)) {
            fileFilteringQuery.must(QueryBuilders.prefixQuery(NAME_FIELD, path));
        }

        final BoolQueryBuilder storageClassesQuery = QueryBuilders.boolQuery();
        SetUtils.emptyIfNull(storageClasses).forEach(storageClass ->
                storageClassesQuery.should(QueryBuilders.termsQuery(STORAGE_CLASS_FIELD, storageClass)));
        fileFilteringQuery.must(storageClassesQuery);

        final SearchSourceBuilder sizeSumSearch = new SearchSourceBuilder()
                .query(fileFilteringQuery)
                .size(0);

        final TermsAggregationBuilder sizeSumAgg = AggregationBuilders
                .terms(STORAGE_SIZE_BY_TIER_AGG_NAME).field(STORAGE_CLASS_FIELD)
                .subAggregation(AggregationBuilders.sum(STORAGE_SIZE_AGG_NAME).field("size"));
        sizeSumSearch.aggregation(sizeSumAgg);
        if (allowVersions) {
            for (String storageClass : storageClasses) {
                final String ovSizeFieldName = String.format("ov_%s_size", storageClass.toLowerCase(Locale.ROOT));
                sizeSumSearch.aggregation(AggregationBuilders
                        .sum(ovSizeFieldName + "_agg").field(ovSizeFieldName));
            }
        }
        final SearchRequest request = new SearchRequest().indices(searchIndex).source(sizeSumSearch);
        if (allowNoIndex) {
            request.indicesOptions(IndicesOptions.LENIENT_EXPAND_OPEN);
        }
        return request;
    }

    public SearchRequest buildFacetedRequest(final FacetedSearchRequest searchRequest,
                                             final String typeFieldName,
                                             final Set<String> metadataSourceFields) {
        final SearchSourceBuilder searchSource = new SearchSourceBuilder()
                .query(getFacetedQuery(searchRequest.getQuery(), searchRequest.getFilters()))
                .fetchSource(buildSourceFields(typeFieldName, metadataSourceFields), Strings.EMPTY_ARRAY)
                .size(searchRequest.getPageSize());

        final List<SearchRequestSort> sorts = resolveSorts(searchRequest.getSorts());
        buildSorts(sorts, isScrollingBackwards(searchRequest.getScrollingParameters()))
                .forEach(searchSource::sort);

        if (Objects.isNull(searchRequest.getScrollingParameters())) {
            searchSource.from(searchRequest.getOffset());
        } else {
            Assert.notNull(searchRequest.getScrollingParameters().getDocId(), messageHelper.getMessage(
                    MessageConstants.ERROR_SEARCH_SCROLLING_PARAMETER_DOC_ID_MISSING));
            searchSource.searchAfter(buildSearchAfterArguments(sorts, searchRequest.getScrollingParameters()));
        }

        if (searchRequest.isHighlight()) {
            addHighlighterToSource(searchSource);
        }

        ListUtils.emptyIfNull(searchRequest.getFacets())
                .forEach(facet -> addTermAggregationToSource(searchSource, facet));

        log.debug("Search request: {} ", searchSource);

        return new SearchRequest()
                .indices(buildAllIndexTypes())
                .indicesOptions(IndicesOptions.lenientExpandOpen())
                .source(searchSource);
    }

    private Boolean isScrollingBackwards(final ScrollingParameters scrollingParameters) {
        return Optional.ofNullable(scrollingParameters)
                .map(ScrollingParameters::isScrollingBackward)
                .orElse(false);
    }

    private List<SearchRequestSort> resolveSorts(final List<SearchRequestSort> requestedSorts) {
        final List<SearchRequestSort> sorts = new ArrayList<>(ListUtils.emptyIfNull(requestedSorts));
        sorts.add(new SearchRequestSort(ES_DOC_SCORE_FIELD, SearchRequestSortOrder.DESC));
        sorts.add(new SearchRequestSort(ES_DOC_ID_FIELD, SearchRequestSortOrder.DESC));
        return sorts;
    }

    private List<SortBuilder<?>> buildSorts(final List<SearchRequestSort> sorts, final boolean isScrollingBackwards) {
        return sorts.stream()
                .map(sort -> buildSort(sort, isScrollingBackwards))
                .collect(Collectors.toList());
    }

    private SortBuilder<?> buildSort(final SearchRequestSort sort, final boolean isScrollingBackwards) {
        return isDefaultField(sort.getField()) ? buildDefaultFieldSort(sort, isScrollingBackwards)
                : isOwnerField(sort.getField()) ? buildOwnerFieldSort(sort, isScrollingBackwards)
                : isDateField(sort.getField()) ? buildDateFieldSort(sort, isScrollingBackwards)
                : isNumericField(sort.getField()) ? buildNumericFieldSort(sort, isScrollingBackwards)
                : buildRegularFieldSort(sort, isScrollingBackwards);
    }

    private SortBuilder<?> buildDefaultFieldSort(final SearchRequestSort sort, final boolean isScrollingBackwards) {
        return SortBuilders.fieldSort(sort.getField())
                .order(buildSortOrder(sort.getOrder(), isScrollingBackwards));
    }

    private SortBuilder<?> buildOwnerFieldSort(final SearchRequestSort sort, final boolean isScrollingBackwards) {
        return buildFieldSort(SearchSourceFields.OWNER.getFieldName(), sort.getOrder(), ES_KEYWORD_TYPE,
                isScrollingBackwards);
    }

    private SortBuilder<?> buildDateFieldSort(final SearchRequestSort sort, final boolean isScrollingBackwards) {
        return buildFieldSort(sort.getField(), sort.getOrder(), ES_DATE_TYPE, isScrollingBackwards);
    }

    private SortBuilder<?> buildNumericFieldSort(final SearchRequestSort sort, final boolean isScrollingBackwards) {
        return buildFieldSort(sort.getField(), sort.getOrder(), ES_LONG_TYPE, isScrollingBackwards);
    }

    private SortBuilder<?> buildRegularFieldSort(final SearchRequestSort sort, final boolean isScrollingBackwards) {
        return buildFieldSort(buildKeywordName(sort.getField()), sort.getOrder(), ES_KEYWORD_TYPE,
                isScrollingBackwards);
    }

    private SortBuilder<?> buildFieldSort(final String field,
                                          final SearchRequestSortOrder order,
                                          final String unmappedType,
                                          final boolean isScrollingBackwards) {
        final SortOrder actualOrder = buildSortOrder(order, isScrollingBackwards);
        final String missing = actualOrder == SortOrder.ASC ? ES_SORT_MISSING_LAST : ES_SORT_MISSING_FIRST;
        return SortBuilders.fieldSort(field)
                .order(actualOrder)
                .unmappedType(unmappedType)
                .missing(missing);
    }

    private SortOrder buildSortOrder(final SearchRequestSortOrder order, final boolean isScrollingBackwards) {
        switch (isScrollingBackwards ? order.invert() : order) {
            case ASC: return SortOrder.ASC;
            case DESC: return SortOrder.DESC;
            default: throw new IllegalArgumentException(String.format("Non supported sort order %s", this));
        }
    }

    private Object[] buildSearchAfterArguments(final List<SearchRequestSort> sorts,
                                               final ScrollingParameters scrollingParameters) {
        final Map<String, Object> searchAfterParameters = getSearchAfterParameters(scrollingParameters);
        final List<String> sortFields = sorts.stream().map(SearchRequestSort::getField).collect(Collectors.toList());
        final Collection<String> sortFieldsWithoutSearchAfterParameter = CollectionUtils.subtract(sortFields,
                searchAfterParameters.keySet());
        Assert.isTrue(CollectionUtils.isEmpty(sortFieldsWithoutSearchAfterParameter), messageHelper.getMessage(
                MessageConstants.ERROR_SEARCH_SCROLLING_PARAMETER_DOC_SORT_FIELDS_MISSING,
                sortFieldsWithoutSearchAfterParameter));
        return sortFields.stream().map(searchAfterParameters::get).toArray();
    }

    private Map<String, Object> getSearchAfterParameters(final ScrollingParameters scrollingParameters) {
        final Map<String, Object> searchAfterParameters = CommonUtils.mergeMaps(
                getDefaultSearchAfterParameters(scrollingParameters),
                scrollingParameters.getDocSortFields());
        searchAfterParameters.replaceAll(this::buildSearchAfterParameterValue);
        return searchAfterParameters;
    }

    private Map<String, Object> getDefaultSearchAfterParameters(final ScrollingParameters scrollingParameters) {
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put(ES_DOC_ID_FIELD, scrollingParameters.getDocId());
        parameters.put(ES_DOC_SCORE_FIELD, scrollingParameters.getDocScore());
        return parameters;
    }

    private Object buildSearchAfterParameterValue(final String field, final Object value) {
        return isDateField(field) ? buildSearchAfterDateParameterValue(value)
                : isNumericField(field) ? buildSearchAfterNumericParameterValue(value)
                : buildSearchAfterRegularParameterValue(value);
    }

    private long buildSearchAfterDateParameterValue(final Object value) {
        return Optional.ofNullable(value)
                .map(Object::toString)
                .filter(StringUtils::isNotBlank)
                .map(stringValue -> LocalDateTime.parse(stringValue, DATE_TIME_FORMATTER))
                .map(DateUtils::convertLocalDateTimeToEpochMillis)
                .orElse(Long.MAX_VALUE);
    }

    private long buildSearchAfterNumericParameterValue(final Object value) {
        return Optional.ofNullable(value)
                .map(Object::toString)
                .filter(StringUtils::isNotBlank)
                .map(NumberUtils::toLong)
                .orElse(Long.MAX_VALUE);
    }

    private String buildSearchAfterRegularParameterValue(final Object value) {
        return Optional.ofNullable(value)
                .map(Object::toString)
                .filter(StringUtils::isNotBlank)
                .orElse(null);
    }

    private boolean isDefaultField(final String field) {
        return StringUtils.equalsIgnoreCase(field, ES_DOC_ID_FIELD)
                || StringUtils.equalsIgnoreCase(field, ES_DOC_SCORE_FIELD);
    }

    private boolean isOwnerField(final String field) {
        return StringUtils.equalsIgnoreCase(field, SearchSourceFields.OWNER.name().toLowerCase());
    }

    private boolean isDateField(final String field) {
        return SearchSourceFields.DATE_FIELDS.contains(field);
    }

    private boolean isNumericField(final String field) {
        return SearchSourceFields.NUMERIC_FIELDS.contains(field);
    }

    private String[] buildSourceFields(final String typeFieldName, final Set<String> metadataSourceFields) {
        final List<String> storedFields = Arrays.stream(SearchSourceFields.values())
                .map(SearchSourceFields::getFieldName)
                .collect(Collectors.toList());
        storedFields.add(typeFieldName);
        storedFields.addAll(metadataSourceFields);
        return storedFields.toArray(Strings.EMPTY_ARRAY);
    }

    private void addHighlighterToSource(final SearchSourceBuilder searchSource) {
        searchSource.highlighter(SearchSourceBuilder.highlight()
                .field("*")
                .postTags("</highlight>")
                .preTags("<highlight>"));
    }

    private QueryBuilder getQuery(final ElasticSearchRequest queryRequest) {
        final BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery().must(getBasicQuery(queryRequest.getQuery()));
        addStorageFilter(queryBuilder, queryRequest.getObjectIdentifier());
        addGlobsFilterToQuery(queryBuilder, queryRequest.getFilterGlobs());
        if (preferenceManager.getPreference(SystemPreferences.SEARCH_HIDE_DELETED)) {
            queryBuilder.mustNot(QueryBuilders.termsQuery(SEARCH_DELETED, Boolean.TRUE));
        }
        return prepareAclFiltersOrAdmin(queryBuilder);
    }

    private QueryBuilder getFacetedQuery(final String query, final Map<String, List<String>> filters) {
        final BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must(prepareFacetedQuery(query));
        boolQueryBuilder.mustNot(QueryBuilders.termsQuery(SEARCH_HIDDEN, Boolean.TRUE));
        if (preferenceManager.getPreference(SystemPreferences.SEARCH_HIDE_DELETED)) {
            boolQueryBuilder.mustNot(QueryBuilders.termsQuery(SEARCH_DELETED, Boolean.TRUE));
        }
        MapUtils.emptyIfNull(filters)
                .forEach((fieldName, values) -> boolQueryBuilder.must(filterToTermsQuery(fieldName, values)));
        return boolQueryBuilder;
    }

    private QueryBuilder prepareAclFiltersOrAdmin(final BoolQueryBuilder queryBuilder) {
        final PipelineUser pipelineUser = userManager.loadUserByName(authManager.getAuthorizedUser());
        if (pipelineUser == null) {
            throw new IllegalArgumentException("Failed to find currently authorized user");
        }
        //no check for admins
        if (ListUtils.emptyIfNull(pipelineUser.getRoles()).stream()
                .anyMatch(role -> role.getId().equals(DefaultRoles.ROLE_ADMIN.getId()))) {
            return queryBuilder;
        }
        addAclFilters(queryBuilder, pipelineUser);
        return queryBuilder;
    }

    private QueryBuilder getBasicQuery(final String searchQuery) {
        QueryStringQueryBuilder query = QueryBuilders.queryStringQuery(searchQuery);
        ListUtils.emptyIfNull(preferenceManager.getPreference(SystemPreferences.SEARCH_ELASTIC_SEARCH_FIELDS))
                .forEach(query::field);
        return query;
    }

    private void addAclFilters(final BoolQueryBuilder queryBuilder, final PipelineUser pipelineUser) {
        final Set<String> authorities = getAuthorities(pipelineUser);
        final String userName = pipelineUser.getUserName();

        final String allowedUsersField = preferenceManager.getPreference(SEARCH_ELASTIC_ALLOWED_USERS_FIELD);
        final String deniedUsersField = preferenceManager.getPreference(SEARCH_ELASTIC_DENIED_USERS_FIELD);

        final List<QueryBuilder> aclQueries = queryBuilder.should();
        queryBuilder.minimumShouldMatch(1);
        // should be allowed for user
        aclQueries.add(QueryBuilders.boolQuery()
                .mustNot(QueryBuilders.termsQuery(deniedUsersField, userName))
                .filter(QueryBuilders.termsQuery(allowedUsersField, userName)));

        final String allowedGroupsField = preferenceManager.getPreference(SEARCH_ELASTIC_ALLOWED_GROUPS_FIELD);
        final String deniedGroupsField = preferenceManager.getPreference(SEARCH_ELASTIC_DENIED_GROUPS_FIELD);
        // or should be allowed and not denied for group
        aclQueries.add(QueryBuilders.boolQuery()
                .mustNot(QueryBuilders.termsQuery(deniedUsersField, userName))
                .filter(QueryBuilders.termsQuery(allowedGroupsField, authorities))
                .mustNot(QueryBuilders.termsQuery(deniedGroupsField, authorities)));
    }

    private Set<String> getAuthorities(final PipelineUser pipelineUser) {
        Set<String> authorities = new HashSet<>();
        authorities.addAll(pipelineUser.getRoles().stream().map(Role::getName).collect(Collectors.toList()));
        authorities.addAll(pipelineUser.getGroups());
        return authorities;
    }

    private String[] buildIndexNames(final List<SearchDocumentType> filterTypes) {
        if (CollectionUtils.isEmpty(filterTypes)) {
            return buildAllIndexTypes();
        }
        final Map<SearchDocumentType, String> typeIndexPrefixes = getSearchIndexPrefixes();
        return filterTypes.stream()
                .map(type -> Optional.ofNullable(typeIndexPrefixes.get(type))
                        .orElseThrow(() -> new SearchException("Missing index name for type: " + type)))
                .toArray(String[]::new);
    }

    private String[] buildAllIndexTypes() {
        return getSearchIndexPrefixes().values().toArray(Strings.EMPTY_ARRAY);
    }

    private Map<SearchDocumentType, String> getSearchIndexPrefixes() {
        final Map<SearchDocumentType, String> typeIndexPrefixes = preferenceManager
                .getPreference(SystemPreferences.SEARCH_ELASTIC_TYPE_INDEX_PREFIX);
        if (MapUtils.isEmpty(typeIndexPrefixes)) {
            throw new SearchException("Index filtering is not configured");
        }
        return typeIndexPrefixes;
    }

    private QueryBuilder filterToTermsQuery(final String fieldName, final List<String> values) {
        return QueryBuilders.termsQuery(buildKeywordName(fieldName), values);
    }

    private QueryBuilder prepareFacetedQuery(final String requestQuery) {
        final BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
        if (StringUtils.isNotBlank(requestQuery)) {
            queryBuilder.must(getBasicQuery(requestQuery));
        }
        return prepareAclFiltersOrAdmin(queryBuilder);
    }

    private void addTermAggregationToSource(final SearchSourceBuilder searchSource, final String facet) {
        final TermsAggregationBuilder aggregationBuilder = AggregationBuilders.terms(facet)
                .size(preferenceManager.getPreference(SystemPreferences.SEARCH_AGGS_MAX_COUNT))
                .field(buildKeywordName(facet));
        searchSource.aggregation(aggregationBuilder);
    }

    private String buildKeywordName(final String fieldName) {
        return String.format("%s.keyword", fieldName);
    }

    private void addGlobsFilterToQuery(final BoolQueryBuilder queryBuilder, final Set<String> globs) {
        if (CollectionUtils.isEmpty(globs)) {
            return;
        }
        final String pathPrefix = preferenceManager.getPreference(SystemPreferences.SEARCH_ELASTIC_PREFIX_FILTER_FIELD);
        globs.stream()
                .filter(StringUtils::isNotBlank)
                .map(ProviderUtils::withoutLeadingDelimiter)
                .forEach(glob -> queryBuilder.must(glob.contains(INDEX_WILDCARD_PREFIX)
                        ? QueryBuilders.wildcardQuery(pathPrefix, glob)
                        : QueryBuilders.matchQuery(pathPrefix, glob)));
    }

    private void addStorageFilter(final BoolQueryBuilder queryBuilder, final String storageIdentifier) {
        if (StringUtils.isBlank(storageIdentifier)) {
            return;
        }
        final MatchQueryBuilder storageIdentifierQuery = NumberUtils.isDigits(storageIdentifier)
                ? QueryBuilders.matchQuery(ES_STORAGE_ID_FIELD, storageIdentifier)
                : QueryBuilders.matchQuery(buildKeywordName(ES_STORAGE_NAME_FIELD), storageIdentifier);
        queryBuilder.must(storageIdentifierQuery);
    }
}
