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
import FacetedFilter, {DocumentTypeFilter, DocumentTypeFilterName} from './faceted-search/filter';
import {
  Pagination,
  PresentationModes,
  TogglePresentationMode
} from './faceted-search/controls';
import SearchResults from './faceted-search/search-results';
import {
  facetedQueryString,
  facetsSearch,
  getFacetFilterToken,
  fetchFacets
} from './faceted-search/utilities';
import {SplitPanel} from '../special/splitPanel';
import styles from './FacetedSearch.css';

function getDisplayRange (offset, pageSize, total) {
  return {
    start: Math.max(0, offset - pageSize),
    end: Math.min(
      total,
      Math.max(0, offset - pageSize) + 3 * pageSize
    )
  };
}

function getDataRange (offset, pageSize) {
  const start = Math.ceil(Math.max(0, offset - 2 * pageSize) / pageSize) * pageSize;
  return {
    start,
    end: start + 5 * pageSize
  };
}

@inject('systemDictionaries', 'preferences', 'pipelines')
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
    pending: false,
    facetsLoaded: false,
    facets: [],
    error: undefined,
    totalHits: 0,
    facetsCount: {},
    documents: [],
    documentsOffset: 0,
    query: undefined,
    offset: 0,
    pageSize: undefined,
    presentationMode: PresentationModes.list,
    showResults: false,
    searchToken: undefined,
    facetsToken: undefined
  }

  componentDidMount () {
    const {facetedFilters} = this.props;
    if (facetedFilters) {
      const {query, filters} = facetedFilters;
      this.setState({query, activeFilters: filters}, () => this.doSearch(0, true));
    } else {
      this.doSearch(0, true);
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

  get page () {
    const {offset, pageSize} = this.state;
    return pageSize ? Math.floor((offset + pageSize - 1) / pageSize) + 1 : 1;
  }

  getFilterPreferences = (filterName) => {
    const {systemDictionaries, preferences} = this.props;
    const {facetedFiltersDictionaries} = preferences;
    if (systemDictionaries.loaded && facetedFiltersDictionaries) {
      const [filter] = (facetedFiltersDictionaries.dictionaries || [])
        .filter(dict => dict.dictionary === filterName);
      if (!filter) {
        return null;
      }
      let entriesToDisplay = filter.defaultDictEntriesToDisplay ||
      facetedFiltersDictionaries.defaultDictEntriesToDisplay;
      if (typeof entriesToDisplay === 'string' && entriesToDisplay.toLowerCase() === 'all') {
        entriesToDisplay = Infinity;
      }
      return {
        entriesToDisplay: Number(entriesToDisplay)
      };
    }
    return null;
  }

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
    this.setState({activeFilters: newFilters}, () => this.doSearch(0, true));
  }

  doSearch = (offset = 0, updateOffset) => {
    this.setState({pending: true}, () => {
      this.loadFacets()
        .then(() => {
          let {offset: currentOffset} = this.state;
          const {
            activeFilters,
            facets,
            facetsCount: currentFacetsCount,
            query,
            pageSize,
            searchToken: currentSearchToken,
            facetsToken: currentFacetsToken,
            totalHits: currentTotalHits
          } = this.state;
          if (updateOffset) {
            currentOffset = offset;
          }
          if (facets.length === 0) {
            // eslint-disable-next-line
            console.warn('No facets configured. Please, check "faceted.filter.dictionaries" preference and system dictionaries');
            this.setState({
              pending: false
            });
            return;
          }
          const dataRange = getDataRange(offset, pageSize);
          const searchToken = getFacetFilterToken(
            query,
            activeFilters,
            dataRange.start,
            dataRange.end - dataRange.start
          );
          if (currentSearchToken === searchToken) {
            return;
          }
          this.setState({
            searchToken,
            offset: currentOffset
          }, () => {
            let queryString = facetedQueryString.build(query, activeFilters);
            if (queryString) {
              queryString = `?${queryString}`;
            }
            if (this.props.router.location.search !== queryString) {
              this.props.router.push(`/search/advanced${queryString || ''}`);
            }
            facetsSearch(
              query,
              activeFilters,
              dataRange.start,
              dataRange.end - dataRange.start,
              {
                facets: facets.map(f => f.name),
                facetsCount: currentFacetsCount,
                facetsToken: currentFacetsToken,
                stores: this.props,
                total: currentTotalHits
              }
            )
              .then(result => {
                const {
                  error,
                  facetsCount,
                  facetsToken,
                  documents = [],
                  documentsOffset = 0,
                  totalHits = 0
                } = result;
                const {
                  searchToken: actualSearchToken,
                  facetsToken: actualFacetsToken
                } = this.state;
                const state = {};
                if (actualFacetsToken !== facetsToken) {
                  state.facetsCount = facetsCount;
                  state.facetsToken = facetsToken;
                }
                if (actualSearchToken === searchToken) {
                  state.pending = false;
                  state.error = error;
                  state.documents = documents;
                  state.documentsOffset = documentsOffset;
                  state.totalHits = totalHits;
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

  loadFacets = () => {
    const {facetsLoaded} = this.state;
    if (facetsLoaded) {
      return Promise.resolve();
    }
    const {systemDictionaries, preferences} = this.props;
    return new Promise((resolve) => {
      const onDone = () => {
        const configuration = preferences.facetedFiltersDictionaries;
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
          fetchFacets(facets.map(f => f.name), {}, '*')
            .then((result) => {
              const {
                facetsCount,
                facetsToken
              } = result || {};
              this.setState({
                initialFacetsCount: facetsCount,
                facetsCount,
                facetsToken,
                facetsLoaded: true,
                facets
              }, resolve);
            });
        } else {
          this.setState({facetsLoaded: true}, resolve);
        }
      };
      Promise.all([
        systemDictionaries.fetchIfNeededOrWait(),
        preferences.fetchIfNeededOrWait()
      ])
        .then(() => {})
        .catch(() => {})
        .then(onDone);
    });
  };

  offsetIsNotInRange = () => {
    const {
      documentsOffset,
      documents,
      offset,
      pageSize,
      totalHits
    } = this.state;
    const dataRange = {
      start: (documentsOffset || 0),
      end: (documentsOffset || 0) + (documents || []).length
    };
    const requestedRange = getDisplayRange(offset, pageSize, totalHits);
    return dataRange.start > requestedRange.start || dataRange.end < requestedRange.end;
  };

  onChangeQuery = () => {
    this.doSearch(0, true);
  };

  onChangePage = (page, pageSize) => {
    const {offset, pageSize: oldPageSize} = this.state;
    const newOffset = (page - 1) * pageSize;
    if (newOffset !== offset || pageSize !== oldPageSize) {
      this.setState({
        offset: newOffset,
        pageSize
      }, () => {
        if (this.offsetIsNotInRange()) {
          this.doSearch(newOffset);
        }
      });
    }
  };

  onChangeOffset = (offset, pageSize) => {
    const {offset: oldOffset, pageSize: oldPageSize} = this.state;
    if (offset !== oldOffset || pageSize !== oldPageSize) {
      this.setState({
        offset,
        pageSize
      }, () => {
        if (this.offsetIsNotInRange()) {
          this.doSearch(offset);
        }
      });
    }
  };

  onChangePresentationMode = (mode) => {
    const {presentationMode} = this.state;
    if (mode === presentationMode) {
      return null;
    }
    this.setState({presentationMode: mode});
  }

  onQueryChange = (e) => {
    this.setState({
      query: e.target.value
    });
  };

  onNavigate = async (item) => {
    if (!this.props.router || !item.url) {
      return;
    }
    this.props.router.push(item.url);
  }

  renderSearchResults = () => {
    const {
      documents,
      documentsOffset,
      error,
      offset,
      pageSize,
      pending,
      presentationMode,
      showResults,
      totalHits
    } = this.state;
    return (
      <SearchResults
        key="search-results"
        className={classNames(styles.panel, styles.searchResults)}
        documents={documents}
        documentsOffset={documentsOffset}
        disabled={pending}
        error={error}
        offset={offset}
        pageSize={pageSize}
        onChangeOffset={this.onChangeOffset}
        onNavigate={this.onNavigate}
        showResults={showResults}
        total={totalHits}
        onChangeDocumentType={this.onChangeFilter(DocumentTypeFilterName)}
        mode={presentationMode}
      />
    );
  }

  render () {
    const {systemDictionaries} = this.props;
    const {
      activeFilters,
      facetsLoaded,
      pageSize,
      presentationMode,
      totalHits,
      query
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
            size="large"
            className={styles.searchInput}
            value={query}
            onChange={this.onQueryChange}
            onPressEnter={this.onChangeQuery}
          />
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
        <div className={styles.actions}>
          <DocumentTypeFilter
            values={this.documentTypeFilter.values}
            selection={(activeFilters || {})[DocumentTypeFilterName]}
            onChange={this.onChangeFilter(DocumentTypeFilterName)}
          />
          <TogglePresentationMode
            className={styles.togglePresentationMode}
            onChange={this.onChangePresentationMode}
            mode={presentationMode}
          />
          <Pagination
            className={styles.pagination}
            page={this.page}
            pageSize={pageSize}
            onChangePage={this.onChangePage}
            total={totalHits}
          />
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
              }]}
              resizerSize={14}
              resizerStyle={{backgroundColor: '#ececec'}}
            >
              <div
                key="faceted-filter"
                className={classNames(styles.panel, styles.facetedFilters)}
              >
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
