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
import {Checkbox, Icon} from 'antd';
import classNames from 'classnames';
import {FilterControl} from './controls';
import styles from './filter.css';

const DEFAULT_ITEMS = 5;

class FacetedFilter extends React.Component {
  state = {
    filterGroupExpanded: true,
    filtersExpanded: false
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
      return this.values.length > (entriesToDisplay || DEFAULT_ITEMS);
    }
    return this.values.length > this.filtersToShow;
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

  toggleFilters = (event) => {
    event && event.stopPropagation();
    this.setState(prevState => ({filtersExpanded: !prevState.filtersExpanded}));
  }

  toggleFilterGroup = (event) => {
    event && event.stopPropagation();
    this.setState(prevState => ({filterGroupExpanded: !prevState.filterGroupExpanded}));
  }

  render () {
    const {
      className,
      disabled,
      name,
      selection = []
    } = this.props;
    const {filtersExpanded, filterGroupExpanded} = this.state;
    if (this.values.length === 0) {
      return null;
    }
    return (
      <div
        className={
          classNames(
            styles.filter,
            className
          )
        }
      >
        <div
          className={
            classNames(styles.header,
              {[styles.expanded]: filterGroupExpanded})
          }
          onClick={this.toggleFilterGroup}
        >
          <div
            className={
              classNames(styles.headerCaret,
                {[styles.expanded]: filterGroupExpanded})
            }
          >
            <Icon type="caret-right" />
          </div>
          <span className={styles.title}>{name}</span>
        </div>
        <div className={styles.optionsContainer}>
          {
            this.values.map((v, i) => (
              <div
                key={v.name}
                className={
                  classNames(styles.option,
                    {[styles.optionVisible]: (i + 1) <= this.filtersToShow},
                    {[styles.optionHidden]: (i + 1) > this.filtersToShow}
                  )}
              >
                <Checkbox
                  onChange={(e) => this.onChangeFilters(v.name, e.target.checked)}
                  checked={selection.some(s => s === v.name)}
                  disabled={v.count === 0 || disabled}
                >
                  {v.name} ({v.count})
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
  showAmount: PropTypes.number,
  showEmptyValues: PropTypes.bool,
  values: PropTypes.array
};

export default FacetedFilter;
