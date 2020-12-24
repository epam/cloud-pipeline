/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {
  Button,
  Icon,
  Select
} from 'antd';
import classNames from 'classnames';
import FilterControl, {
  criteriaValid,
  criteriaEqual,
  mapCriteria
} from './filter-control';
import styles from './filters-control.css';

function getFiltersError (filters) {
  if (!filters) {
    return undefined;
  }
  const {operator, filters: criteria} = filters;
  if (criteria && criteria.length > 0) {
    if (!operator) {
      return 'You must specify a condition';
    }
    if (criteria.map(criteriaValid).filter(o => !o).length > 0) {
      return 'Some conditions are invalid';
    }
  }
  return undefined;
}

function filtersAreEqual (a, b) {
  if (!a && !b) {
    return true;
  }
  if (!a || !b) {
    return false;
  }
  const {operator: aOperator, filters: aCriteria = []} = a;
  const {operator: bOperator, filters: bCriteria = []} = b;
  if (aOperator !== bOperator) {
    return false;
  }
  if (aCriteria.length !== bCriteria.length) {
    return false;
  }
  for (let c = 0; c < aCriteria.length; c++) {
    const criteria = aCriteria[c];
    const theSame = bCriteria.find(bc => criteriaEqual(criteria, bc));
    if (!theSame) {
      return false;
    }
  }
  return true;
}

const mapFilters = o => o
  ? ({
    operator: o.operator,
    filters: (o.filters || []).slice().map(mapCriteria)
  })
  : {};

class FiltersControl extends React.Component {
  state = {
    filters: {}
  };

  componentDidMount () {
    this.updateState();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (!filtersAreEqual(prevProps.filters, this.props.filters)) {
      this.updateState();
    }
  }

  updateState = () => {
    this.setState({
      filters: mapFilters(this.props.filters)
    });
  };

  onChangeOperator = o => {
    const {filters = {}} = this.state;
    filters.operator = o;
    this.setState({
      filters
    }, this.onChange);
  };

  onChange = () => {
    const {onChange} = this.props;
    if (onChange) {
      onChange(this.state.filters);
    }
  };

  onAddFilter = () => {
    const {filters = {}} = this.state;
    if (!filters.filters) {
      filters.filters = [{}];
    } else {
      filters.filters.push({});
    }
    this.setState({
      filters
    }, this.onChange);
  };

  onRemoveFilter = (index) => () => {
    const {filters = {}} = this.state;
    if (filters.filters && filters.filters.length > index) {
      filters.filters.splice(index, 1);
      this.setState({
        filters
      }, this.onChange);
    }
  };

  onChangeFilter = (index) => filter => {
    const {filters = {}} = this.state;
    if (filters.filters && filters.filters.length > index) {
      filters.filters.splice(index, 1, filter);
      this.setState({
        filters
      }, this.onChange);
    }
  };

  render () {
    const {disabled} = this.props;
    const {filters} = this.state;
    const {
      operator,
      filters: criteria = []
    } = filters || {};
    return (
      <div
        className={styles.container}
      >
        <div
          className={styles.row}
        >
          <span
            className={styles.label}
          >
            Condition:
          </span>
          <Select
            disabled={disabled}
            style={{width: 200}}
            value={operator}
            onChange={this.onChangeOperator}
          >
            <Select.Option key="AND" value="AND">
              Matches all filters ("and")
            </Select.Option>
            <Select.Option key="OR" value="OR">
              Matches any filter ("or")
            </Select.Option>
          </Select>
        </div>
        <div
          className={classNames(styles.row, styles.multiRow)}
        >
          <span
            className={styles.label}
          >
            Filters:
          </span>
          <div className={styles.column}>
            {
              criteria.map((c, i) => (
                <div
                  key={`${i}`}
                  className={styles.filterRow}
                >
                  <FilterControl
                    disabled={disabled}
                    className={styles.criteria}
                    style={{flex: 1}}
                    filter={c}
                    onChange={this.onChangeFilter(i)}
                  />
                  <Button
                    disabled={disabled}
                    style={{marginLeft: 5}}
                    size="small"
                    type="danger"
                    onClick={this.onRemoveFilter(i)}
                  >
                    <Icon type="delete" />
                  </Button>
                </div>
              ))
            }
            <div>
              <Button
                disabled={disabled}
                type="dashed"
                onClick={this.onAddFilter}
              >
                <Icon type="plus" />
                Add filter
              </Button>
            </div>
          </div>
        </div>
      </div>
    );
  }
}

FiltersControl.propTypes = {
  disabled: PropTypes.bool,
  filters: PropTypes.object,
  onChange: PropTypes.func
};

export {getFiltersError, filtersAreEqual, mapFilters};
export default FiltersControl;
