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
import FacetedFilter from './faceted-search/filter';
import SearchResults from './faceted-search/search-results';
import {
  doSearch,
  facetedQueryString,
  getFacetFilterToken,
  getItemUrl,
  fetchFacets
} from './faceted-search/utilities';
import {SplitPanel} from '../special/splitPanel';
import styles from './FacetedSearch.css';

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
    query: undefined,
    offset: 0,
    pageSize: undefined,
    pageSizeInitialized: false,
    showResults: false
  }

  componentDidMount () {
    const {facetedFilters} = this.props;
    if (facetedFilters) {
      const {query, filters} = facetedFilters;
      this.setState({query, activeFilters: filters}, () => this.doSearch());
    } else {
      this.doSearch();
    }
  }

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
    const {offset, pageSize, pageSizeInitialized} = this.state;
    if (!pageSizeInitialized) {
      return 1;
    }
    return Math.floor(offset / pageSize) + 1;
  }

  getFilterPreferences = (filterName) => {
    const {systemDictionaries, preferences} = this.props;
    const {facetedFiltersDictionaries} = preferences;
    if (systemDictionaries.loaded && facetedFiltersDictionaries) {
      const [filter] = facetedFiltersDictionaries.dictionaries
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
    this.setState({activeFilters: newFilters}, () => this.doSearch());
  }

  doSearch = (offset = 0) => {
    this.setState({pending: true}, () => {
      this.loadFacets()
        .then(() => {
          const {
            activeFilters,
            facets,
            query,
            pageSize,
            pageSizeInitialized,
            searchToken: currentSearchToken
          } = this.state;
          if (!pageSizeInitialized) {
            return;
          }
          if (facets.length === 0) {
            // eslint-disable-next-line
            console.warn('No facets configured. Please, check "faceted.filter.dictionaries" preference and system dictionaries');
            this.setState({
              pending: false
            });
            return;
          }
          const searchToken = getFacetFilterToken(query, activeFilters, offset, pageSize);
          if (currentSearchToken === searchToken) {
            return;
          }
          this.setState({
            searchToken,
            offset
          }, () => {
            let queryString = facetedQueryString.build(query, activeFilters);
            if (queryString) {
              queryString = `?${queryString}`;
            }
            if (this.props.router.location.search !== queryString) {
              this.props.router.push(`/search/advanced${queryString || ''}`);
            }
            Promise.all([
              fetchFacets(facets.map(f => f.name), activeFilters, query),
              doSearch(query, activeFilters, offset, pageSize)
            ])
              .then(([facetsCount, searchResult]) => {
                const {
                  error,
                  documents = [],
                  totalHits = 0
                } = searchResult;
                Promise.all(
                  documents.map(doc => getItemUrl(doc, this.props))
                )
                  .then(urls => {
                    urls.forEach((url, index) => {
                      documents[index].url = url;
                    });
                    const {searchToken: actualSearchToken} = this.state;
                    if (actualSearchToken === searchToken) {
                      this.setState({
                        pending: false,
                        error,
                        facetsCount: {...facetsCount},
                        documents: documents.slice(),
                        totalHits,
                        searchToken: undefined,
                        showResults: Object.keys(activeFilters || {}).length > 0 || !!query
                      });
                    }
                  });
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
          fetchFacets(facets.map(f => f.name), {}, '*')
            .then((facetsCount) => {
              this.setState({
                initialFacetsCount: facetsCount,
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

  onChangePage = (page, pageSize) => {
    const {offset, pageSize: oldPageSize, pageSizeInitialized} = this.state;
    const newOffset = (page - 1) * pageSize;
    if (pageSizeInitialized || newOffset !== offset || pageSize !== oldPageSize) {
      this.setState({
        offset: newOffset,
        pageSize: pageSizeInitialized ? oldPageSize : pageSize,
        pageSizeInitialized: true
      }, () => {
        this.doSearch(newOffset);
      });
    }
  };

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

  render () {
    const {systemDictionaries} = this.props;
    const {
      activeFilters,
      documents,
      facetsLoaded,
      pageSize,
      pending,
      showResults,
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
    return (
      <div
        className={styles.container}
      >
        <div
          className={styles.search}
        >
          <Input
            disabled={pending}
            size="large"
            className={styles.searchInput}
            value={query}
            onChange={this.onQueryChange}
            onPressEnter={() => this.doSearch(0)}
          />
          <Button
            disabled={pending}
            className={styles.find}
            size="large"
            type="primary"
            onClick={() => this.doSearch(0)}
          >
            <Icon type="search" />
            Search
          </Button>
        </div>
        <div className={styles.content}>
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
                    disabled={pending}
                    values={filter.values}
                    selection={(activeFilters || {})[filter.name]}
                    onChange={this.onChangeFilter(filter.name)}
                    preferences={this.getFilterPreferences(filter.name)}
                    showEmptyValues={!FacetedSearch.HIDE_VALUE_IF_EMPTY}
                  />
                ))
              }
            </div>
            <SearchResults
              key="search-results"
              className={classNames(styles.panel, styles.searchResults)}
              disabled={pending}
              documents={(documents || []).slice()}
              page={this.page}
              pageSize={pageSize}
              onChangePage={this.onChangePage}
              onNavigate={this.onNavigate}
              showResults={showResults}
              total={totalHits}
            />
          </SplitPanel>
        </div>
      </div>
    );
  }
}

export default FacetedSearch;
