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
import PropTypes from 'prop-types';
import {Checkbox, Icon, Input} from 'antd';
import classNames from 'classnames';
import {FilterControl} from './controls';
import highlightText from '../../special/highlightText';
import styles from './filter.css';

const DEFAULT_ITEMS = 5;
const MIN_ITEMS_TO_SEARCH = 10;

class FacetedFilter extends React.Component {
  state = {
    filterGroupExpanded: true,
    filtersExpanded: false,
    searchString: ''
  }

  get values () {
    const {showEmptyValues, values} = this.props;
    if (!Array.isArray(values)) {
      return [];
    }
    if (showEmptyValues) {
      return values;
    }
    return values.filter(v => v.count && Number(v.count) > 0);
  }

  get filteredValues () {
    const {searchString} = this.state;
    if (!searchString) {
      return this.values;
    }
    return this.values
      .filter(v => v.name.toLowerCase().includes(searchString.toLowerCase()));
  }

  get filtersToShow () {
    const {filterGroupExpanded, filtersExpanded} = this.state;
    const {preferences} = this.props;
    const {entriesToDisplay} = preferences || {};
    if (!filterGroupExpanded) {
      return 0;
    }
    if (filterGroupExpanded && filtersExpanded) {
      return this.values.length;
    }
    return entriesToDisplay || DEFAULT_ITEMS;
  }

  get showFilterControl () {
    const {filtersExpanded, filterGroupExpanded} = this.state;
    const {preferences} = this.props;
    const {entriesToDisplay} = preferences || {};
    if (!filterGroupExpanded) {
      return false;
    }
    if (filtersExpanded) {
      return this.filteredValues.length > (entriesToDisplay || DEFAULT_ITEMS);
    }
    return this.filteredValues.length > this.filtersToShow;
  }

  filterIsChecked = (value) => {
    const {selection} = this.props;
    if (!selection || !selection.length) {
      return false;
    }
    return selection.some(s => s === value.name);
  }

  onChangeFilters = (value, selected) => {
    const {selection = [], onChange} = this.props;
    let changed = false;
    const newSelection = [...selection];
    const index = newSelection.indexOf(value);
    if (selected && index === -1) {
      newSelection.push(value);
      changed = true;
    } else if (!selected && index >= 0) {
      newSelection.splice(index, 1);
      changed = true;
    }
    if (changed && onChange) {
      onChange(newSelection);
    }
  };

  onSearchDictionaries = (event) => {
    if (event) {
      this.setState({
        searchString: event.target.value
      });
    }
  }

  clearSearch = () => {
    this.setState({searchString: ''});
  }

  toggleFilters = (event) => {
    event && event.stopPropagation();
    this.setState(prevState => ({filtersExpanded: !prevState.filtersExpanded}));
  }

  toggleFilterGroup = (event) => {
    event && event.stopPropagation();
    this.setState(prevState => ({filterGroupExpanded: !prevState.filterGroupExpanded}));
  }

  renderSearchInput = () => {
    const {searchString, filterGroupExpanded} = this.state;
    if (this.values.length < MIN_ITEMS_TO_SEARCH) {
      return null;
    }
    return (
      <Input
        placeholder="Search dictionaries..."
        value={searchString}
        size="small"
        onChange={this.onSearchDictionaries}
        className={
          classNames(styles.searchInput,
            {[styles.optionHidden]: !filterGroupExpanded}
          )
        }
        suffix={searchString ? (
          <Icon
            type="close-circle-o"
            onClick={this.clearSearch}
            className={styles.clearBtn}
          />) : null
        }
      />
    );
  }

  render () {
    const {
      className,
      disabled,
      name,
      showCounts = true
    } = this.props;
    const {searchString} = this.state;
    const {filtersExpanded, filterGroupExpanded} = this.state;
    if (this.values.length === 0) {
      return null;
    }
    return (
      <div
        className={
          classNames(
            styles.filter,
            'cp-panel',
            'cp-search-filter',
            className
          )
        }
      >
        <div
          className={
            classNames(
              styles.header,
              'cp-search-filter-header',
              {
                'cp-search-filter-header-expanded': filterGroupExpanded
              }
            )
          }
          onClick={this.toggleFilterGroup}
        >
          <div
            className={
              classNames(
                styles.headerCaret,
                'cp-search-filter-header-caret'
              )
            }
          >
            <Icon type="caret-right" />
          </div>
          <span className={styles.title}>{name}</span>
        </div>
        {this.renderSearchInput()}
        <div className={styles.optionsContainer}>
          {
            this.filteredValues.map((v, i) => (
              <div
                key={i}
                className={
                  classNames(styles.option,
                    {[styles.optionHidden]: (i + 1) > this.filtersToShow}
                  )}
              >
                <Checkbox
                  onChange={(e) => this.onChangeFilters(v.name, e.target.checked)}
                  checked={this.filterIsChecked(v)}
                  disabled={(!this.filterIsChecked(v) && (showCounts && v.count === 0)) || disabled}
                >
                  {highlightText(v.name, searchString)}
                  {showCounts ? ` (${v.count})` : false}
                </Checkbox>
              </div>
            ))
          }
        </div>
        <FilterControl
          onClick={this.toggleFilters}
          expanded={filtersExpanded}
          visible={this.showFilterControl}
        />
      </div>
    );
  }
}

FacetedFilter.propTypes = {
  className: PropTypes.string,
  disabled: PropTypes.bool,
  name: PropTypes.string,
  onChange: PropTypes.func,
  preferences: PropTypes.object,
  selection: PropTypes.array,
  showCounts: PropTypes.bool,
  showEmptyValues: PropTypes.bool,
  values: PropTypes.array
};

export default FacetedFilter;
export {DocumentTypeFilter, DocumentTypeFilterName} from './controls';
