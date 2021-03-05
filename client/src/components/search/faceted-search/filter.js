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
import MENU_VIEW from './enums/menu-view';
import styles from './filter.css';

const DEFAULT_ITEMS = 5;

class FacetedFilter extends React.Component {
  state = {
    menuView: MENU_VIEW.collapsed
  }
  get entriesToDisplayPreference () {
    const {preferences} = this.props;
    if (preferences) {
      const {entriesToDisplay, defaultEntriesToDisplay} = preferences;
      return entriesToDisplay || defaultEntriesToDisplay || DEFAULT_ITEMS;
    }
    return DEFAULT_ITEMS;
  }
  get entriesToDisplay () {
    const {menuView} = this.state;
    if (menuView === MENU_VIEW.entirelyCollapsed) {
      return 0;
    }
    if (menuView === MENU_VIEW.expanded) {
      return Infinity;
    }
    return this.entriesToDisplayPreference;
  }
  get showFilterControls () {
    const {values} = this.props;
    if (!values || values.length === 0) {
      return false;
    }
    return (values.length > this.entriesToDisplay) ||
      (values.length !== this.entriesToDisplayPreference);
  }
  get filterGroup () {
    const {activeFilters, name} = this.props;
    return activeFilters.filter(f => f.group === name);
  }
  collapseMenu = (event, entirely) => {
    event && event.stopPropagation();
    entirely
      ? this.setState({menuView: MENU_VIEW.entirelyCollapsed})
      : this.setState({menuView: MENU_VIEW.collapsed});
  }
  expandMenu = (event, toCollapsed) => {
    event && event.stopPropagation();
    toCollapsed
      ? this.setState(({menuView: MENU_VIEW.collapsed}))
      : this.setState(({menuView: MENU_VIEW.expanded}));
  }
  onHeaderClick = (event) => {
    const {menuView} = this.state;
    menuView === MENU_VIEW.entirelyCollapsed
      ? this.expandMenu(event)
      : this.collapseMenu(event, true);
  }
  render () {
    const {
      className,
      name,
      values,
      changeFilter
    } = this.props;
    const {menuView} = this.state;
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
        <div
          className={styles.header}
          onClick={this.onHeaderClick}
        >
          <div
            className={
              classNames(styles.headerCaret,
                {[styles.expanded]: [MENU_VIEW.expanded, MENU_VIEW.collapsed].includes(menuView)
                })}
          >
            <Icon type="caret-right" />
          </div>
          <span className={styles.title}>{name}</span>
        </div>
        <div className={styles.optionsContainer}>
          {
            values.map((v, i) => (
              <div
                key={v.name}
                className={
                  classNames(styles.option,
                    {[styles.optionHidden]: (i + 1) > this.entriesToDisplay
                    })}
              >
                <Checkbox
                  onChange={(e) => changeFilter(name, v.name, e.target.checked)}
                  checked={this.filterGroup.some(f => f.name === v.name)}
                  disabled={v.count === 0}
                >
                  {v.name} ({v.count})
                </Checkbox>
              </div>
            ))
          }
        </div>
        {this.showFilterControls && (
          <FilterControl
            menuView={menuView}
            onExpand={this.expandMenu}
            onCollapse={this.collapseMenu}
          />
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
