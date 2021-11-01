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
import {inject, observer} from 'mobx-react';
import {
  Alert,
  Button,
  Icon,
  Input
} from 'antd';
import classNames from 'classnames';
import LoadingView from '../special/LoadingView';
import {SearchGroupTypes} from './searchGroupTypes';
import FacetedFilter, {DocumentTypeFilter, DocumentTypeFilterName} from './faceted-search/filter';
import {
  PresentationModes,
  TogglePresentationMode
} from './faceted-search/controls';
import SearchResults, {DEFAULT_PAGE_SIZE} from './faceted-search/search-results';
import {
  facetedQueryString,
  facetsSearch,
  getFacetFilterToken,
  fetchFacets,
  FacetModeStorage
} from './faceted-search/utilities';
import {SplitPanel} from '../special/splitPanel';
import styles from './FacetedSearch.css';

@inject('systemDictionaries', 'preferences', 'pipelines', 'uiNavigation')
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
    userDocumentTypes: [],
    continuousOptions: {},
    pending: false,
    facetsLoaded: false,
    facets: [],
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
    facetsToken: undefined
  }

  abortController;

  componentDidMount () {
    const {facetedFilters} = this.props;
    this.initAbortController();
    if (facetedFilters) {
      const {query, filters} = facetedFilters;
      this.setState(
        {query, activeFilters: filters},
        () => this.doSearch()
      );
    } else {
      this.doSearch();
    }
  }

  get documentTypeFilter () {
    const {
      facetsLoaded,
      facetsCount,
      initialFacetsCount = {}
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
        .sort((a, b) => b.count - a.count)
        .filter(v => FacetedSearch.HIDE_VALUE_IF_EMPTY
          ? v.count > 0
          : (
            initialFacetsCount.hasOwnProperty(DocumentTypeFilterName) &&
            initialFacetsCount[DocumentTypeFilterName].hasOwnProperty(v.name) &&
            Number(initialFacetsCount[DocumentTypeFilterName][v.name]) > 0
          )
        )
    };
  };

  get filters () {
    const {
      facetsLoaded,
      facets,
      facetsCount,
      initialFacetsCount = {}
    } = this.state;
    if (!facetsLoaded || !facetsCount) {
      return [];
    }
    return facets
      .filter(d => FacetedSearch.HIDE_VALUE_IF_EMPTY
        ? initialFacetsCount.hasOwnProperty(d.name)
        : facetsCount.hasOwnProperty(d.name)
      )
      .map(d => ({
        name: d.name,
        values: d.values
          .map(v => ({name: v, count: facetsCount[d.name][v] || 0}))
          .sort((a, b) => b.count - a.count)
          .filter(v => FacetedSearch.HIDE_VALUE_IF_EMPTY
            ? v.count > 0
            : (
              initialFacetsCount[d.name].hasOwnProperty(v.name) &&
              Number(initialFacetsCount[d.name][v.name]) > 0
            )
          )
      }));
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
      this.doSearch(undefined, true);
    });
  };

  onClearFilters = () => {
    if (this.activeFiltersIsEmpty) {
      return;
    }
    this.setState({activeFilters: {}}, () => this.doSearch());
  };

  doSearch = (continuousOptions = undefined, abortPendingRequests = false) => {
    this.setState({
      pending: true
    }, () => {
      this.loadFacets()
        .then(() => {
          const {
            activeFilters,
            userDocumentTypes = [],
            documents: currentDocuments = [],
            facets,
            facetsCount: currentFacetsCount,
            query,
            pageSize,
            searchToken: currentSearchToken,
            facetsToken: currentFacetsToken
          } = this.state;
          if (facets.length === 0) {
            // eslint-disable-next-line
            console.warn('No facets configured. Please, check "faceted.filter.dictionaries" preference and system dictionaries');
            this.setState({
              pending: false
            });
            return;
          }
          const userDocumentTypesFilter = userDocumentTypes.length > 0
            ? {[DocumentTypeFilterName]: userDocumentTypes}
            : {};
          const mergedFilters = {
            ...(activeFilters || {}),
            ...userDocumentTypesFilter
          };
          const searchToken = getFacetFilterToken(
            query,
            activeFilters,
            pageSize,
            continuousOptions
          );
          if (currentSearchToken === searchToken) {
            return;
          }
          this.setState({searchToken}, () => {
            let queryString = facetedQueryString.build(query, activeFilters);
            if (queryString) {
              queryString = `?${queryString}`;
            }
            if (this.props.router.location.search !== queryString) {
              this.props.router.push(`/search/advanced${queryString || ''}`);
            }
            if (this.abortController && abortPendingRequests) {
              this.abortController.abort();
              this.initAbortController();
            }
            facetsSearch(
              query,
              mergedFilters,
              pageSize,
              {
                facets: facets.map(f => f.name),
                facetsCount: currentFacetsCount,
                facetsToken: currentFacetsToken,
                stores: this.props,
                metadataFields: facets
                  .map(f => f.name)
                  .filter(facet => facet !== DocumentTypeFilterName)
              },
              continuousOptions,
              this.abortSignal
            )
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
                    documents = [
                      ...currentDocuments.filter(doc => !ids.has(doc.elasticId)),
                      ...receivedDocuments
                    ];
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
                if (actualFacetsToken !== facetsToken) {
                  state.facetsCount = facetsCount;
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
    const {facetsLoaded} = this.state;
    if (facetsLoaded) {
      return Promise.resolve();
    }
    const {systemDictionaries, preferences, uiNavigation} = this.props;
    return new Promise((resolve) => {
      const onDone = () => {
        const configuration = preferences.facetedFiltersDictionaries;
        const searchDocumentTypes = uiNavigation.searchDocumentTypes || [];
        const documentTypes = searchDocumentTypes
          .map(type => SearchGroupTypes.hasOwnProperty(type)
            ? SearchGroupTypes[type].types
            : []
          )
          .reduce((r, c) => ([...r, ...c]), []);
        if (systemDictionaries.loaded && configuration) {
          const {dictionaries = []} = configuration || {};
          const orders = dictionaries
            .map(d => ({[d.dictionary]: d.order || Infinity}))
            .reduce((r, c) => ({...r, ...c}), {});
          const filtered = (systemDictionaries.value || [])
            .filter(d => orders.hasOwnProperty(d.key));
          filtered
            .sort((a, b) => orders[a.key] - orders[b.key]);
          const facets = filtered.map(d => ({
            name: d.key,
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
            abortSignal
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
                initialFacetsCount: facetsCount,
                facetsCount,
                facetsToken,
                facetsLoaded: true,
                facets,
                userDocumentTypes: documentTypes
              }, resolve);
            });
        } else {
          this.setState({facetsLoaded: true}, resolve);
        }
      };
      Promise.all([
        systemDictionaries.fetchIfNeededOrWait(),
        preferences.fetchIfNeededOrWait(),
        uiNavigation.fetchSearchDocumentTypes()
      ])
        .then(() => {})
        .catch(() => {})
        .then(onDone);
    });
  };

  onChangeQuery = () => {
    this.doSearch();
  };

  onLoadNextPage = (document, forward = true) => {
    if (document) {
      this.doSearch({
        docId: document.elasticId,
        docScore: document.score,
        scrollingBackward: !forward
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

  renderSearchResults = () => {
    const {
      activeFilters,
      documents,
      error,
      isLastPage,
      isFirstPage,
      pageSize,
      pending,
      presentationMode,
      showResults
    } = this.state;
    const {
      userDocumentTypes = []
    } = this.state;
    const documentTypes = Array.from(new Set([
      ...((activeFilters || {})[DocumentTypeFilterName] || []),
      ...(userDocumentTypes || [])
    ]));
    return (
      <SearchResults
        key="search-results"
        className={classNames(styles.panel, styles.searchResults)}
        documents={documents}
        extraColumns={
          this.filters
            .filter(f => f.values.length > 0 && f.name !== DocumentTypeFilterName)
            .map(f => f.name)
        }
        disabled={pending}
        loading={pending}
        error={error}
        pageSize={pageSize}
        hasElementsAfter={!isLastPage}
        hasElementsBefore={!isFirstPage}
        onLoadData={this.onLoadNextPage}
        onPageSizeChanged={this.onPageSizeChanged}
        onNavigate={this.onNavigate}
        showResults={showResults}
        onChangeDocumentType={this.onChangeFilter(DocumentTypeFilterName)}
        mode={presentationMode}
        documentTypes={documentTypes}
      />
    );
  };

  render () {
    const {systemDictionaries} = this.props;
    const {
      activeFilters,
      facetsLoaded,
      presentationMode,
      query,
      userDocumentTypes = []
    } = this.state;
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
    return (
      <div
        className={styles.container}
      >
        <div
          className={styles.search}
        >
          <Input
            ref={this.initializeSearchRef}
            size="large"
            suffix={
              query
                ? (
                  <Icon
                    type="close-circle"
                    className={classNames(styles.clearQuery, 'cp-search-clear-button')}
                    onClick={this.onClearQuery}
                  />
                )
                : undefined
            }
            className={styles.searchInput}
            value={query}
            onChange={this.onQueryChange}
            onPressEnter={this.onChangeQuery}
          />
          {
            userDocumentTypes.length > 0 && (
              <TogglePresentationMode
                className={styles.togglePresentationMode}
                onChange={this.onChangePresentationMode}
                mode={presentationMode}
              />
            )
          }
          <Button
            className={styles.find}
            size="large"
            type="primary"
            onClick={this.onChangeQuery}
          >
            <Icon type="search" />
            Search
          </Button>
        </div>
        {
          userDocumentTypes.length === 0 && (
            <div
              className={classNames(styles.actions, 'cp-search-actions')}
            >
              <DocumentTypeFilter
                values={this.documentTypeFilter.values}
                selection={(activeFilters || {})[DocumentTypeFilterName]}
                onChange={this.onChangeFilter(DocumentTypeFilterName)}
                onClearFilters={this.onClearFilters}
              />
              <TogglePresentationMode
                className={styles.togglePresentationMode}
                onChange={this.onChangePresentationMode}
                mode={presentationMode}
              />
            </div>
          )
        }
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
              }]}
              resizerSize={14}
              resizerStyle={{backgroundColor: '#ececec'}}
            >
              <div
                key="faceted-filter"
                className={classNames(styles.panel, styles.facetedFilters)}
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
                  this.filters.map((filter, index) => (
                    <FacetedFilter
                      key={filter.name}
                      name={filter.name}
                      className={styles.filter}
                      values={filter.values}
                      selection={(activeFilters || {})[filter.name]}
                      onChange={this.onChangeFilter(filter.name)}
                      preferences={this.getFilterPreferences(filter.name)}
                      showEmptyValues={!FacetedSearch.HIDE_VALUE_IF_EMPTY}
                    />
                  ))
                }
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
