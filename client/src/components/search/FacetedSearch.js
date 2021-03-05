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
import {computed} from 'mobx';
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
import {SplitPanel} from '../special/splitPanel';
import styles from './FacetedSearch.css';

@inject('systemDictionaries', 'preferences')
@observer
class FacetedSearch extends React.Component {
  state = {
    activeFilters: [],
    filtersMock: null
  }

  @computed
  get configuredFacetedFilters () {
    const {systemDictionaries, preferences} = this.props;
    const configuration = preferences.facetedFiltersDictionaries;
    if (systemDictionaries.loaded && configuration) {
      const {dictionaries = []} = configuration || {};
      const orders = dictionaries
        .map(d => ({[d.dictionary]: d.order || Infinity}))
        .reduce((r, c) => ({...r, ...c}), {});
      const systemDictionariesFiltered = (systemDictionaries.value || [])
        .filter(d => orders.hasOwnProperty(d.key));
      systemDictionariesFiltered
        .sort((a, b) => orders[a.key] - orders[b.key]);
      return systemDictionariesFiltered.map(d => ({
        name: d.key,
        values: (d.values || []).map(v => v.value)
      }));
    }
    return [];
  }

  setFiltersMock (mock) {
    const {filtersMock} = this.state;
    if (!filtersMock && mock.length) {
      this.setState({filtersMock: mock});
    }
  }

  componentDidUpdate () {
    const {filtersMock} = this.state;
    if (!filtersMock && this.filters.length) {
      this.setFiltersMock(this.filters);
    }
  }

  get filters () {
    // todo: filter `configuredFacetedFilters` dictionaries and their values
    // todo: (based on API response)
    const {filtersMock} = this.state;
    const getRandomNumber = (from, to) => {
      return Math.floor(from + Math.random() * (to + 1 - from));
    };
    if (filtersMock) {
      return filtersMock;
    }
    return this.configuredFacetedFilters
      .filter(d => true)
      .map(d => ({
        name: d.name,
        values: d.values
          .filter(v => true)
          .map(v => ({name: v, count: getRandomNumber(0, 7)}))
          .sort((a, b) => a.count - b.count)
      }));
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
      const preferences = {
        entriesToDisplay: filter.defaultDictEntriesToDisplay,
        defaultEntriesToDisplay: facetedFiltersDictionaries.defaultDictEntriesToDisplay
      };
      return Object.fromEntries(Object.entries(preferences)
        .map(([key, value]) => {
          if (value && typeof value === 'string' && value.toLowerCase() === 'all') {
            return [key, Infinity];
          }
          return [key, Number(value)];
        })
      );
    }
    return null;
  }

  onChangeFilter = (group, name, active) => {
    if (!group || !name) {
      return;
    }
    const {activeFilters} = this.state;
    const filter = {group, name, active};
    let newState = [...activeFilters];
    if (active) {
      newState.push(filter);
    } else {
      newState = newState.filter(f => !(f.group === group && f.name === name));
    }
    this.setState({activeFilters: newState});
  }

  render () {
    const {systemDictionaries} = this.props;
    const {activeFilters} = this.state;
    if (systemDictionaries.pending && !systemDictionaries.loaded) {
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
            size="large"
            className={styles.searchInput}
          />
          <Button
            className={styles.find}
            size="large"
            type="primary"
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
                percentMaximum: 75,
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
                    activeFilters={activeFilters}
                    changeFilter={this.onChangeFilter}
                    preferences={this.getFilterPreferences(filter.name)}
                    test={index === 0}
                  />
                ))
              }
            </div>
            <SearchResults
              key="search-results"
              className={classNames(styles.panel, styles.searchResults)}
            />
          </SplitPanel>
        </div>
      </div>
    );
  }
}

export default FacetedSearch;
