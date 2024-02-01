/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import React from 'react';
import {computed} from 'mobx';
import {inject, observer} from 'mobx-react';
import {
  Alert,
  Button,
  Icon,
  Input,
  Dropdown,
  Menu,
  Tabs
} from 'antd';
import classNames from 'classnames';
import LoadingView from '../special/LoadingView';
import {SearchGroupTypes} from './searchGroupTypes';
import FacetedFilter, {DocumentTypeFilter, DocumentTypeFilterName} from './faceted-search/filter';
import {
  PresentationModes,
  TogglePresentationMode,
  ExportButton,
  SharingControl
} from './faceted-search/controls';
import SearchResults, {DEFAULT_PAGE_SIZE} from './faceted-search/search-results';
import {
  DocumentColumns,
  getDefaultColumns,
  filterDisplayedColumns,
  DefaultSorting,
  ExcludedSortingKeys,
  parseExtraColumns,
  correctSorting,
  facetedQueryString,
  facetsSearch,
  getAvailableSortingFields,
  getFacetFilterToken,
  fetchFacets,
  FacetModeStorage,
  toggleSortingByField,
  removeSortingByField
} from './faceted-search/utilities';
import {SplitPanel} from '../special/splitPanel';
import {filterNonMatchingItemsFn} from './utilities/elastic-item-utilities';
import styles from './FacetedSearch.css';
import handleDownloadItems from '../special/download-storage-items';
import getNotDownloadableStorages from './utilities/get-downloadable-storages';
import {
  getDocumentDisplayName,
  getStorageFileDisplayNameTemplates
} from './utilities/get-storage-file-display-name-templates';
import getUserSearchColumnsOrder from './utilities/get-user-search-columns-order';
import roleModel from '../../utils/roleModel';

function getDomainKey (domain) {
  return `domain-${domain || ''}`;
}

function parseDomainKey (key) {
  const [, domain] = (key || '').match(/^domain-(.*)$/i);
  if (domain && domain.length) {
    return domain;
  }
  return undefined;
}

@inject('systemDictionaries', 'preferences', 'pipelines', 'uiNavigation')
@roleModel.authenticationInfo
@inject((stores, props) => {
  const {location = {}} = props || {};
  const {query = {}} = location;
  return {
    facetedFilters: facetedQueryString.parse(query)
  };
})
@observer
class FacetedSearch extends React.Component {
  static HIDE_VALUE_IF_EMPTY = false;

  state = {
    activeFilters: {},
    advancedSearchMode: false,
    extraColumnsConfiguration: [],
    sortingOrder: DefaultSorting,
    userDocumentTypes: [],
    continuousOptions: {},
    pending: false,
    facetsLoaded: false,
    facets: [],
    domain: undefined,
    createOtherDomain: false,
    disableCounts: false,
    otherDomainName: 'Other',
    error: undefined,
    facetsCount: {},
    documents: [],
    isFirstPage: false,
    isLastPage: false,
    query: undefined,
    pageSize: DEFAULT_PAGE_SIZE,
    presentationMode: FacetModeStorage.load() || PresentationModes.list,
    showResults: false,
    searchToken: undefined,
    facetsToken: undefined,
    selectedItems: [],
    showSelectionPreview: false,
    notDownloadableStorages: [],
    storageFileDisplayNameTemplates: []
  }

  abortController;

  componentDidMount () {
    const {facetedFilters} = this.props;
    this.initAbortController();
    this.fetchNotDownloadableStorages();
    if (facetedFilters) {
      const {
        query,
        filters,
        advanced = false
      } = facetedFilters;
      this.setState(
        {
          query,
          activeFilters: filters,
          advancedSearchMode: advanced
        },
        () => this.doSearch()
      );
    } else {
      this.doSearch();
    }
  }

  @computed
  get dataStorageSharingEnabled () {
    const {preferences} = this.props;
    return preferences && preferences.loaded &&
        preferences.sharedStoragesSystemDirectory && preferences.dataSharingEnabled;
  }

  @computed
  get nameTag () {
    const {preferences} = this.props;
    if (preferences && preferences.loaded) {
      return preferences.displayNameTag;
    }
    return undefined;
  }

  @computed
  get downloadFileTag () {
    const {preferences} = this.props;
    if (preferences && preferences.loaded) {
      return preferences.facetedFilterDownloadFileTag;
    }
    return undefined;
  }

  get documentTypeFilter () {
    const {
      facetsLoaded,
      facetsCount,
      initialFacetsCount = {},
      disableCounts
    } = this.state;
    if (!facetsLoaded || !facetsCount) {
      return {name: DocumentTypeFilterName, values: []};
    }
    const filter = facetsCount[DocumentTypeFilterName];
    if (!filter) {
      return {name: DocumentTypeFilterName, values: []};
    }
    return {
      name: DocumentTypeFilterName,
      values: Object.keys(filter)
        .map(key => ({
          name: key,
          count: filter[key] || 0
        }))
        .sort((a, b) => disableCounts ? 0 : b.count - a.count)
        .filter(v => disableCounts || (FacetedSearch.HIDE_VALUE_IF_EMPTY
          ? v.count > 0
          : (
            initialFacetsCount.hasOwnProperty(DocumentTypeFilterName) &&
            initialFacetsCount[DocumentTypeFilterName].hasOwnProperty(v.name) &&
            Number(initialFacetsCount[DocumentTypeFilterName][v.name]) > 0
          )
        ))
    };
  };

  get filters () {
    const {
      facetsLoaded,
      facets,
      facetsCount,
      initialFacetsCount = {},
      disableCounts
    } = this.state;
    if (!facetsLoaded || !facetsCount) {
      return [];
    }
    return facets
      .filter(d => disableCounts || (FacetedSearch.HIDE_VALUE_IF_EMPTY
        ? initialFacetsCount.hasOwnProperty(d.name)
        : facetsCount.hasOwnProperty(d.name)
      ))
      .map(d => ({
        name: d.name,
        domain: d.domain,
        values: d.values
          .map(v => ({
            name: v,
            count: (facetsCount[d.name] ? facetsCount[d.name][v] : undefined) || 0
          }))
          .sort((a, b) => disableCounts ? a.name.localeCompare(b.name) : b.count - a.count)
          .filter(v => disableCounts || (FacetedSearch.HIDE_VALUE_IF_EMPTY
            ? v.count > 0
            : (
              initialFacetsCount[d.name] &&
              initialFacetsCount[d.name].hasOwnProperty(v.name) &&
              Number(initialFacetsCount[d.name][v.name]) > 0
            )
          ))
      }));
  }

  get filterDomains () {
    const {
      createOtherDomain
    } = this.state;
    const filters = this.filters.filter((f) => f.name !== DocumentTypeFilterName);
    const domains = [...new Set(filters.map((filter) => filter.domain).concat(undefined))]
      .sort((a, b) => {
        if (!a) {
          return 1;
        }
        if (!b) {
          return -1;
        }
        return a.localeCompare(b);
      });
    return domains
      .map((domain) => ({
        domain,
        enabled: filters
          .filter((f) => f.domain === domain)
          .some((f) => f.values.length > 0)
      }))
      .filter((domain) => domain.enabled)
      .map(domain => domain.domain)
      .filter(domain => domain || createOtherDomain);
  }

  get filteredSortingFields () {
    const {sortingOrder} = this.state;
    const excludedKeys = [
      ...ExcludedSortingKeys,
      ...(sortingOrder || []).map(sort => sort.field)
    ];
    return getAvailableSortingFields(this.columns)
      .filter(key => !excludedKeys.includes(key));
  }

  get facetsColumns () {
    return this.filters
      .filter(f => f.values.length > 0 && f.name !== DocumentTypeFilterName)
      .map(f => ({
        key: f.name,
        name: f.name
      }));
  }

  @computed
  get searchColumnsOrder () {
    const {
      preferences,
      authenticatedUserInfo
    } = this.props;
    if (preferences.loaded && authenticatedUserInfo.loaded) {
      return getUserSearchColumnsOrder(preferences, authenticatedUserInfo);
    }
    return [];
  }

  get extraColumns () {
    const {extraColumnsConfiguration: extra} = this.state;
    return extra || [];
  }

  get additionalColumns () {
    const {extraColumns = []} = this;
    const additionalColumns = this.facetsColumns.slice();
    extraColumns.forEach(column => {
      if (!additionalColumns.find(c => c.key === column.key)) {
        additionalColumns.push(column);
      }
    });
    return additionalColumns;
  }

  get metadataFields () {
    const {
      storageFileDisplayNameTemplates = [],
      facets = []
    } = this.state;
    const additionalTags = [...new Set(
      storageFileDisplayNameTemplates.reduce((result, current) => ([
        ...result,
        ...current.tags
      ]), []))];
    return [...new Set(this.allColumns
      .slice()
      .map((column) => column.key)
      .concat(facets.map((facet) => facet.name))
      .concat([this.nameTag, this.downloadFileTag, ...additionalTags].filter(Boolean))
      .filter((column) => !DocumentColumns.some((documentColumn) => documentColumn.key === column))
    )];
  }

  get documentTypes () {
    const {
      activeFilters,
      userDocumentTypes = []
    } = this.state;
    return Array.from(new Set([
      ...((activeFilters || {})[DocumentTypeFilterName] || []),
      ...(userDocumentTypes || [])
    ]));
  }

  get columns () {
    const documentTypes = this.documentTypes;
    return filterDisplayedColumns(this.allColumns, documentTypes);
  }

  get allColumns () {
    const documentTypes = this.documentTypes;
    const all = getDefaultColumns({
      facetsColumns: this.facetsColumns,
      extraColumns: this.extraColumns,
      searchColumnsOrder: this.searchColumnsOrder
    });
    if (!documentTypes || !documentTypes.length) {
      return all;
    } else {
      return all
        .filter(column => !column.types || documentTypes.find(type => column.types.has(type)));
    }
  }

  get activeFiltersIsEmpty () {
    const {activeFilters} = this.state;
    if (activeFilters) {
      return !Object.keys(activeFilters).length;
    }
    return true;
  }

  get abortSignal () {
    if (this.abortController) {
      return this.abortController.signal;
    }
    return undefined;
  }

  getFilterPreferences = (filterName) => {
    const {systemDictionaries, preferences} = this.props;
    const {activeFilters} = this.state;
    const {facetedFiltersDictionaries} = preferences;
    if (systemDictionaries.loaded && facetedFiltersDictionaries) {
      const [filter] = (facetedFiltersDictionaries.dictionaries || [])
        .filter(dict => dict.dictionary === filterName);
      if (!filter) {
        return null;
      }
      let entriesToDisplay;
      let minByActiveFilters;
      let preferenceAmount = filter.defaultDictEntriesToDisplay !== undefined
        ? filter.defaultDictEntriesToDisplay
        : facetedFiltersDictionaries.defaultDictEntriesToDisplay;
      if (typeof preferenceAmount === 'string') {
        if (preferenceAmount.toLowerCase() === 'all') {
          preferenceAmount = Infinity;
        } else if (!isNaN(Number(preferenceAmount))) {
          preferenceAmount = Number(preferenceAmount);
        }
      }
      if (activeFilters[filter.dictionary]) {
        const currentFilter = this.filters.find(filter => filter.name === filterName);
        if (currentFilter && currentFilter.values) {
          minByActiveFilters = currentFilter.values
            .map(value => activeFilters[filter.dictionary].includes(value.name))
            .lastIndexOf(true) + 1;
        }
      }
      if (preferenceAmount >= 0 || minByActiveFilters) {
        entriesToDisplay = Math.max(...[
          minByActiveFilters,
          preferenceAmount
        ].filter(Number));
      }
      return {
        entriesToDisplay
      };
    }
    return null;
  };

  initAbortController = () => {
    if (window.AbortController) {
      this.abortController = new AbortController();
    }
  };

  fetchNotDownloadableStorages = () => {
    const {
      authenticatedUserInfo,
      preferences
    } = this.props;
    getNotDownloadableStorages(authenticatedUserInfo, preferences)
      .then((storages = []) => this.setState({
        notDownloadableStorages: storages
      }));
  };

  setDefaultDomain = () => {
    const domains = this.filterDomains;
    this.setState({domain: domains[0]});
  };

  onChangeFilter = (group) => (selection) => {
    if (!group) {
      return;
    }
    const {activeFilters} = this.state;
    const newFilters = {...activeFilters};
    if (selection && selection.length) {
      newFilters[group] = selection.slice();
    } else {
      delete newFilters[group];
    }
    this.setState({activeFilters: newFilters}, () => {
      this.correctSorting()
        .then(() => this.doSearch(undefined, true));
    });
  };

  onClearFilters = () => {
    if (this.activeFiltersIsEmpty) {
      return;
    }
    this.setState({activeFilters: {}}, () => {
      this.correctSorting().then(() => this.doSearch());
    });
  };

  changeSortingOrder = (key, cancelable = false) => {
    if (!key) {
      return;
    }
    const {sortingOrder = []} = this.state;
    this.setState({
      sortingOrder: toggleSortingByField(key, sortingOrder, cancelable)
    }, () => this.doSearch());
  };

  removeSortingByField = (field, e) => {
    if (e) {
      e.stopPropagation();
    }
    const {sortingOrder = []} = this.state;
    this.setState({
      sortingOrder: removeSortingByField(field, sortingOrder)
    }, () => this.doSearch());
  }

  correctSorting = () => {
    return new Promise((resolve) => {
      const {sortingOrder = []} = this.state;
      this.setState({
        sortingOrder: correctSorting(sortingOrder, this.columns)
      }, () => resolve());
    });
  };

  updateCurrentRouting = () => {
    const {
      query,
      activeFilters,
      advancedSearchMode
    } = this.state;
    let queryString = facetedQueryString.build(
      query,
      activeFilters,
      advancedSearchMode
    );
    if (queryString) {
      queryString = `?${queryString}`;
    }
    if (this.props.router.location.search !== queryString) {
      this.props.router.push(`/search/advanced${queryString || ''}`);
    }
  };

  doSearch = (continuousOptions = undefined, abortPendingRequests = false) => {
    this.setState({
      pending: true
    }, () => {
      this.loadFacets()
        .then(() => {
          const {
            advancedSearchMode,
            activeFilters,
            sortingOrder,
            userDocumentTypes = [],
            documents: currentDocuments = [],
            facets,
            facetsCount: currentFacetsCount,
            query: currentQuery,
            pageSize,
            searchToken: currentSearchToken,
            facetsToken: currentFacetsToken,
            storageFileDisplayNameTemplates = [],
            disableCounts
          } = this.state;
          if (facets.length === 0) {
            // eslint-disable-next-line
            console.warn('No facets configured. Please, check "faceted.filter.dictionaries" preference and system dictionaries');
            this.setState({
              pending: false
            });
            return;
          }
          const query = !advancedSearchMode && currentQuery
            ? `*${currentQuery}*`
            : currentQuery;
          const userDocumentTypesFilter = userDocumentTypes.length > 0
            ? {[DocumentTypeFilterName]: userDocumentTypes}
            : {};
          const mergedFilters = {
            ...(activeFilters || {}),
            ...userDocumentTypesFilter
          };
          const searchToken = getFacetFilterToken({
            query,
            sortingOrder,
            filters: activeFilters,
            pageSize,
            scrollingParameters: continuousOptions
          });
          if (currentSearchToken === searchToken) {
            return;
          }
          this.setState({searchToken}, () => {
            this.updateCurrentRouting();
            if (this.abortController && abortPendingRequests) {
              this.abortController.abort();
              this.initAbortController();
            }
            facetsSearch({
              query,
              sortingOrder,
              filters: mergedFilters,
              pageSize,
              options: {
                facets: facets.map(f => f.name),
                facetsCount: currentFacetsCount,
                facetsToken: currentFacetsToken,
                stores: this.props,
                metadataFields: this.metadataFields
              },
              scrollingParameters: continuousOptions,
              abortSignal: this.abortSignal,
              skipFacets: disableCounts
            })
              .then(result => {
                if (result && result.aborted) {
                  return;
                }
                const {
                  error,
                  facetsCount,
                  facetsToken,
                  documents: receivedDocuments = []
                } = result;
                const {
                  searchToken: actualSearchToken,
                  facetsToken: actualFacetsToken
                } = this.state;
                const state = {};
                let documents;
                if (!continuousOptions) {
                  state.isFirstPage = true;
                  state.isLastPage = receivedDocuments.length < pageSize;
                  documents = receivedDocuments.slice();
                } else {
                  const lastPage = receivedDocuments.length < pageSize;
                  if (lastPage) {
                    const ids = new Set(receivedDocuments.map(doc => doc.elasticId));
                    if (continuousOptions.scrollingBackward) {
                      documents = [
                        ...receivedDocuments,
                        ...currentDocuments.filter(doc => !ids.has(doc.elasticId))
                      ];
                    } else {
                      documents = [
                        ...currentDocuments.filter(doc => !ids.has(doc.elasticId)),
                        ...receivedDocuments
                      ];
                    }
                  } else {
                    documents = receivedDocuments.slice();
                  }
                  if (continuousOptions.scrollingBackward) {
                    state.isFirstPage = lastPage;
                    if (!lastPage) {
                      state.isLastPage = false;
                    }
                  } else {
                    state.isLastPage = lastPage;
                    if (!lastPage) {
                      state.isFirstPage = false;
                    }
                  }
                }
                documents = documents.map(document => ({
                  ...document,
                  nameOverride: getDocumentDisplayName(
                    document,
                    storageFileDisplayNameTemplates,
                    this.nameTag
                  ),
                  downloadOverride: this.downloadFileTag
                    ? document[this.downloadFileTag]
                    : undefined
                }));
                if (actualFacetsToken !== facetsToken) {
                  state.facetsCount = facetsCount || {};
                  state.facetsToken = facetsToken;
                }
                if (actualSearchToken === searchToken) {
                  state.pending = false;
                  state.error = error;
                  state.documents = documents;
                  state.searchToken = undefined;
                  state.showResults = true;
                }
                if (Object.keys(state).length > 0) {
                  this.setState(state);
                }
              });
          });
        });
    });
  };

  loadFacets = (abortSignal) => {
    const {facetsLoaded, sortingOrder = []} = this.state;
    if (facetsLoaded) {
      return Promise.resolve();
    }
    const {systemDictionaries, preferences, uiNavigation} = this.props;
    return new Promise((resolve) => {
      const onDone = (storageFileDisplayNameTemplates = []) => {
        const extraColumnsConfiguration = parseExtraColumns(preferences);
        const configuration = preferences.facetedFiltersDictionaries;
        const searchDocumentTypes = uiNavigation.searchDocumentTypes || [];
        const documentTypes = searchDocumentTypes
          .map(type => SearchGroupTypes.hasOwnProperty(type)
            ? SearchGroupTypes[type].types
            : []
          )
          .reduce((r, c) => ([...r, ...c]), []);
        if (systemDictionaries.loaded && configuration) {
          const {
            createOtherDomain = false,
            disableCounts = false,
            otherDomainName = 'Other',
            dictionaries = []
          } = configuration || {};
          const orders = dictionaries
            .map(d => ({[d.dictionary]: d.order || Infinity}))
            .reduce((r, c) => ({...r, ...c}), {});
          const filtered = (systemDictionaries.value || [])
            .filter(d => orders.hasOwnProperty(d.key));
          filtered
            .sort((a, b) => orders[a.key] - orders[b.key]);
          const getDictionaryDomain = (dictionary) => {
            const obj = dictionaries.find((o) => o.dictionary === dictionary);
            return obj ? obj.domain : undefined;
          };
          const facets = filtered.map(d => ({
            name: d.key,
            domain: getDictionaryDomain(d.key),
            values: (d.values || []).map(v => v.value)
          }));
          facets.push({
            name: DocumentTypeFilterName,
            values: []
          });
          fetchFacets(
            facets.map(f => f.name),
            documentTypes.length > 0
              ? {
                [DocumentTypeFilterName]: documentTypes
              }
              : {},
            '*',
            sortingOrder,
            abortSignal,
            disableCounts
          )
            .then((result) => {
              const {
                facetsCount,
                facetsToken
              } = result || {};
              if (result && result.aborted) {
                return;
              }
              this.setState({
                createOtherDomain,
                disableCounts,
                otherDomainName,
                extraColumnsConfiguration,
                initialFacetsCount: facetsCount,
                facetsCount,
                facetsToken,
                facetsLoaded: true,
                facets,
                userDocumentTypes: documentTypes,
                storageFileDisplayNameTemplates
              }, () => {
                this.setDefaultDomain();
                this.correctSorting()
                  .then(resolve);
              });
            });
        } else {
          this.setState({facetsLoaded: true}, resolve);
        }
      };
      Promise.all([
        systemDictionaries.fetchIfNeededOrWait(),
        preferences.fetchIfNeededOrWait(),
        uiNavigation.fetchSearchDocumentTypes(),
        getStorageFileDisplayNameTemplates(preferences)
      ])
        .then(([, , , templates]) => Promise.resolve(templates))
        .catch(() => Promise.resolve([]))
        .then(onDone);
    });
  };

  onChangeQuery = () => {
    this.doSearch();
  };

  onLoadNextPage = (document, forward = true) => {
    const {sortingOrder} = this.state;
    if (document) {
      const docSortFields = sortingOrder.reduce((acc, {field}) => ({
        ...acc,
        [field]: document[field] || ''
      }), {});
      this.doSearch({
        docId: document.elasticId,
        docScore: document.score,
        scrollingBackward: !forward,
        ...(Object.keys(docSortFields).length && {docSortFields})
      });
    }
  };

  onChangePresentationMode = (mode) => {
    const {presentationMode} = this.state;
    if (mode === presentationMode) {
      return null;
    }
    this.setState({presentationMode: mode}, () => {
      FacetModeStorage.save(mode);
    });
  };

  onChangeSearchMode = () => {
    const {advancedSearchMode} = this.state;
    this.setState({
      advancedSearchMode: !advancedSearchMode
    }, () => {
      this.updateCurrentRouting();
      this.doSearch(undefined, true);
    });
  };

  onQueryChange = (e) => {
    this.setState({
      query: e.target.value
    });
  };

  initializeSearchRef = (input) => {
    this.searchInput = input;
  }

  onClearQuery = () => {
    this.setState({
      query: undefined
    }, () => {
      this.onChangeQuery();
      if (this.searchInput) {
        this.searchInput.focus();
      }
    });
  };

  onNavigate = async (item) => {
    if (!this.props.router || !item.url) {
      return;
    }
    this.props.router.push(item.url);
  };

  onPageSizeChanged = (newPageSize) => {
    const {
      pageSize: currentPage
    } = this.state;
    this.setState({
      pageSize: newPageSize
    }, () => {
      if (currentPage !== this.state.pageSize) {
        this.doSearch();
      }
    });
  };

  onSelectItem = (item) => {
    const selectedItems = [...new Set([...this.state.selectedItems, item])];

    this.setState({selectedItems});
  };

  onDeselectItem = (item) => {
    const filterFn = filterNonMatchingItemsFn(item);
    const selectedItems = this.state.selectedItems
      .filter(filterFn);

    this.setState({selectedItems});
  };

  clearSelection = () => {
    this.setState({
      selectedItems: []
    });
  };

  renderExportButton = () => {
    const {
      advancedSearchMode,
      activeFilters,
      query,
      userDocumentTypes = [],
      sortingOrder = [],
      facets = []
    } = this.state;
    return (
      <ExportButton
        className={styles.exportButton}
        size="default"
        columns={this.allColumns}
        advanced={advancedSearchMode}
        query={query}
        filters={{
          ...(activeFilters || {}),
          ...(userDocumentTypes.length > 0
            ? {[DocumentTypeFilterName]: userDocumentTypes}
            : {}
          )
        }}
        sorting={sortingOrder}
        facets={facets}
      />
    );
  };

  renderSortingControls = () => {
    const {
      sortingOrder,
      pending
    } = this.state;
    const getSortingFieldName = (key) => {
      const document = DocumentColumns.find(doc => doc.key === key);
      if (!document) {
        return key;
      }
      return document.name;
    };
    const menu = (
      <Menu onClick={({key}) => {
        this.changeSortingOrder(key);
      }}>
        {this.filteredSortingFields.map(sortingKey => (
          <Menu.Item key={sortingKey}>
            {getSortingFieldName(sortingKey)}
          </Menu.Item>
        ))}
      </Menu>
    );
    return (
      <div className={styles.sortingControlsContainer}>
        <span style={{marginRight: '5px'}}>Sort by: </span>
        {sortingOrder.map(sort => (
          <Button
            onClick={() => this.changeSortingOrder(sort.field)}
            disabled={pending}
            className={styles.sortingBtn}
            key={sort.field}
          >
            <Icon
              style={{fontSize: '10px'}}
              type={
                sort.asc
                  ? 'caret-up'
                  : 'caret-down'
              }
            />
            {getSortingFieldName(sort.field)}
            <Icon
              type="close"
              className={classNames(
                styles.removeSortingBtn,
                'cp-icon-button',
                {'cp-disabled': pending}
              )}
              onClick={(event) => this.removeSortingByField(sort.field, event)}
            />
          </Button>
        ))}
        <Dropdown
          overlay={menu}
          placement="bottomLeft"
          disabled={pending}
        >
          <Button className={styles.sortingBtn}>
            <Icon
              type="plus"
              style={{fontSize: '14px'}}
            />
          </Button>
        </Dropdown>
      </div>
    );
  };

  renderControlsRow = () => {
    const {presentationMode} = this.state;
    return (
      <div
        className={classNames(styles.actions, 'cp-search-actions')}
      >
        <TogglePresentationMode
          className={styles.togglePresentationMode}
          onChange={this.onChangePresentationMode}
          mode={presentationMode}
          size="default"
        />
        {this.renderSortingControls()}
        {this.renderExportButton()}
      </div>
    );
  };

  renderSearchResults = () => {
    const {
      sortingOrder,
      documents,
      error,
      isLastPage,
      isFirstPage,
      pageSize,
      pending,
      presentationMode,
      showResults,
      facetsToken
    } = this.state;
    return (
      <div
        key="search-results"
        style={{
          overflow: 'hidden',
          height: '100%',
          display: 'flex',
          flexDirection: 'column'
        }}
      >
        {this.renderControlsRow()}
        <SearchResults
          className={classNames(styles.panel, styles.searchResults)}
          documents={documents}
          extraColumns={this.additionalColumns}
          onChangeSortingOrder={this.changeSortingOrder}
          sortingOrder={sortingOrder}
          disabled={pending}
          loading={pending}
          error={error}
          pageSize={pageSize}
          hasElementsAfter={!isLastPage}
          hasElementsBefore={!isFirstPage}
          resetPositionToken={facetsToken}
          onLoadData={this.onLoadNextPage}
          onPageSizeChanged={this.onPageSizeChanged}
          onNavigate={this.onNavigate}
          onSelectItem={this.onSelectItem}
          onDeselectItem={this.onDeselectItem}
          selectedItems={this.state.selectedItems}
          dataStorageSharingEnabled={this.dataStorageSharingEnabled}
          notDownloadableStorages={this.state.notDownloadableStorages}
          showResults={showResults}
          onChangeDocumentType={this.onChangeFilter(DocumentTypeFilterName)}
          mode={presentationMode}
          columns={this.columns}
        />
      </div>
    );
  };

  handleDownloadItems = async (items = []) => {
    const {preferences} = this.props;
    await handleDownloadItems(preferences, items);
  };

  handleDomainSelection = (key) => this.setState({domain: parseDomainKey(key)});

  render () {
    const {systemDictionaries} = this.props;
    const {
      activeFilters,
      facetsLoaded,
      query,
      selectedItems = [],
      userDocumentTypes = [],
      advancedSearchMode,
      domain,
      createOtherDomain,
      disableCounts,
      otherDomainName
    } = this.state;
    const filterFacetByDomain = (facet) => {
      if (createOtherDomain) {
        // Create separate tab for filters without domains
        return domain === facet.domain;
      }
      // Display filters without domains for every domain tab
      return domain === facet.domain || !facet.domain;
    };
    if (!facetsLoaded || (systemDictionaries.pending && !systemDictionaries.loaded)) {
      return (
        <LoadingView />
      );
    }
    if (systemDictionaries.error) {
      return (
        <Alert message={systemDictionaries.error} type="error" />
      );
    }
    const noFilters = this.filters.filter(f => f.name !== DocumentTypeFilterName).length === 0;
    const inputSuffix = (
      <div className={styles.suffixContainer}>
        {query ? (
          <Icon
            type="close-circle"
            className={classNames(styles.clearQuery, 'cp-search-clear-button')}
            onClick={this.onClearQuery}
          />
        ) : null}
        <span
          className={classNames(
            styles.searchModeButton,
            'cp-button',
            {'primary': advancedSearchMode}
          )}
          onClick={this.onChangeSearchMode}
        >
          Advanced
        </span>
      </div>
    );
    return (
      <div
        className={
          classNames(styles.container, 'cp-panel', 'cp-panel-no-hover', 'cp-panel-borderless')
        }
      >
        <div
          className={styles.search}
        >
          <Input
            ref={this.initializeSearchRef}
            size="large"
            suffix={inputSuffix}
            className={styles.searchInput}
            value={query}
            onChange={this.onQueryChange}
            onPressEnter={this.onChangeQuery}
          />
          <SharingControl
            visible={
              selectedItems &&
              selectedItems.length > 0
            }
            items={selectedItems}
            onClearSelection={this.clearSelection}
            extraColumns={this.additionalColumns}
            onDownload={this.handleDownloadItems}
            dataStorageSharingEnabled={this.dataStorageSharingEnabled}
            notDownloadableStorages={this.state.notDownloadableStorages}
          />
          {
            userDocumentTypes.length === 0 && (
              <DocumentTypeFilter
                values={this.documentTypeFilter.values}
                selection={(activeFilters || {})[DocumentTypeFilterName]}
                onChange={this.onChangeFilter(DocumentTypeFilterName)}
                onClearFilters={this.onClearFilters}
                showCounts={!disableCounts}
              />
            )
          }
          <Button
            className={styles.find}
            size="default"
            type="primary"
            onClick={this.onChangeQuery}
          >
            <Icon type="search" />
            Search
          </Button>
        </div>
        <div className={styles.content}>
          {!noFilters ? (
            <SplitPanel
              contentInfo={[{
                key: 'faceted-filter',
                size: {
                  pxMinimum: 300,
                  percentMaximum: 50,
                  percentDefault: 25
                }
              }, {
                key: 'search-results',
                containerStyle: {
                  overflow: 'hidden'
                }
              }]}
              resizerSize={8}
              className={'cp-transparent-background'}
            >
              <div
                key="faceted-filter"
                className={classNames(styles.panel, styles.facetedFiltersContainer)}
              >
                {
                  this.filterDomains.length > 1 && (
                    <Tabs
                      className={
                        classNames(
                          'cp-tabs-no-content',
                          'cp-faceted-filters'
                        )
                      }
                      hideAdd
                      type="card"
                      activeKey={getDomainKey(domain)}
                      onChange={this.handleDomainSelection}
                    >
                      {
                        this.filterDomains.map((domain) => (
                          <Tabs.TabPane
                            key={getDomainKey(domain)}
                            closable={false}
                            tab={domain || otherDomainName}
                          />
                        ))
                      }
                    </Tabs>
                  )
                }
                <div
                  className={
                    classNames(
                      styles.facetedFilters,
                      {
                        [styles.singleGroup]: this.filterDomains.length <= 1,
                        'cp-tabs-content': this.filterDomains.length > 1
                      }
                    )
                  }
                >
                  <span
                    className={classNames(
                      styles.clearFiltersBtn,
                      'cp-search-clear-filters-button',
                      {
                        [styles.disabled]: this.activeFiltersIsEmpty
                      }
                    )}
                    onClick={this.onClearFilters}
                  >
                    Clear filters
                  </span>
                  {
                    this.filters
                      .filter(filterFacetByDomain)
                      .map((filter) => (
                        <FacetedFilter
                          key={filter.name}
                          name={filter.name}
                          className={styles.filter}
                          values={filter.values}
                          selection={(activeFilters || {})[filter.name]}
                          onChange={this.onChangeFilter(filter.name)}
                          preferences={this.getFilterPreferences(filter.name)}
                          showEmptyValues={!FacetedSearch.HIDE_VALUE_IF_EMPTY || disableCounts}
                          showCounts={!disableCounts}
                        />
                      ))
                  }
                </div>
              </div>
              {this.renderSearchResults()}
            </SplitPanel>
          ) : (
            this.renderSearchResults()
          )
          }
        </div>
      </div>
    );
  }
}

export default FacetedSearch;
