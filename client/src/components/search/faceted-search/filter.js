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
import {Checkbox, Button} from 'antd';
import classNames from 'classnames';
import styles from './filter.css';

const DEFAULT_ITEMS = 5;

class FacetedFilter extends React.Component {
  state = {
    menuCollapsed: true
  }
  get showExpandButton () {
    const {showAmount, values} = this.props;
    if (!values || values.length === 0) {
      return false;
    }
    return values.length > (showAmount || DEFAULT_ITEMS);
  }
  get filterGroup () {
    const {activeFilters, name} = this.props;
    return activeFilters.filter(f => f.group === name);
  }
  onFilterChange = (e) => {
    const {changeFilter} = this.props;
    changeFilter && changeFilter();
  }
  toggleMenu = () => {
    this.setState(prevstate => ({menuCollapsed: !prevstate.menuCollapsed}));
  }
  render () {
    const {
      className,
      name,
      values,
      changeFilter,
      showAmount
    } = this.props;
    const {menuCollapsed} = this.state;
    if (!values || values.length === 0) {
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
        <div className={styles.title}>
          {name}
        </div>
        {
          values.map((v, i) => (
            <div
              key={v.name}
              className={
                classNames(styles.option,
                  {[styles.optionHidden]: menuCollapsed && (i + 1 > (showAmount || DEFAULT_ITEMS))
                  })}
            >
              <Checkbox
                onChange={(e) => changeFilter(name, v.name, e.target.checked)}
                checked={this.filterGroup.some(f => f.name === v.name)}
              >
                {v.name} ({v.count})
              </Checkbox>
            </div>
          ))
        }
        {this.showExpandButton && (
          <Button
            onClick={this.toggleMenu}
            className={
              classNames(styles.expandBtn,
                {[styles.expanded]: !menuCollapsed})
            }
          >
            {menuCollapsed ? 'Show all' : 'Collapse menu'}
          </Button>
        )}
      </div>
    );
  }
}

FacetedFilter.propTypes = {
  className: PropTypes.string,
  name: PropTypes.string,
  values: PropTypes.array,
  selection: PropTypes.array,
  showAmount: PropTypes.number,
  activeFilters: PropTypes.array,
  changeFilter: PropTypes.func
};

export default FacetedFilter;
