/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import {observer} from 'mobx-react';
import {
  Button,
  Dropdown,
  Icon,
  Menu
} from 'antd';
import classNames from 'classnames';
import {SearchGroupTypes} from '../../searchGroupTypes';
import localization from '../../../../utils/localization';
import displayCount from '../../../../utils/displayCount';
import styles from './controls.css';

@localization.localizedComponent
@observer
class DocumentTypeFilter extends localization.LocalizedReactComponent {
  state={
    overlayVisible: false
  }

  get filters () {
    const {values = [], selection} = this.props;
    return Object.entries(SearchGroupTypes).map(([key, group]) => ({
      ...group,
      key,
      enabled: group.test(selection),
      count: values
        .filter(v => group.test(v.name))
        .map(v => v.count)
        .reduce((r, c) => r + c, 0)
    }));
  }

  get activeFilters () {
    return this.filters.filter(f => f.enabled);
  }

  handleFilterClick = ({key}) => {
    const {disabled, selection, onChange, showCounts} = this.props;
    const filter = this.filters.find(f => f.key === key);
    if (disabled || (showCounts && !filter.enabled && filter.count === 0)) {
      return;
    }
    let newSelection = [];
    if (!showCounts || filter.count > 0) {
      newSelection = (selection || []).filter(s => !filter.test(s));
      if (!filter.enabled) {
        newSelection.push(...filter.types);
      }
    }
    if (onChange) {
      onChange(newSelection);
    }
  };

  onFilterVisibleChange= (visible) => {
    this.setState({overlayVisible: visible});
  };

  render () {
    const {disabled, size, showCounts} = this.props;
    const {overlayVisible} = this.state;
    const filterMenu = (
      <Menu
        onClick={this.handleFilterClick}
        className={styles.azaza}
      >
        {this.filters.map(filter => (
          <Menu.Item
            key={filter.key}
            style={{
              cursor: !filter.enabled && filter.count === 0
                ? 'default'
                : 'pointer'
            }}
          >
            <div
              className={
                classNames(
                  styles.documentFilter,
                  'cp-search-faceted-button',
                  {
                    'selected': filter.enabled,
                    'disabled': (!filter.enabled && showCounts && filter.count === 0) || disabled
                  }
                )
              }
            >
              <Icon
                className={classNames('cp-icon-larger', styles.icon)}
                type={filter.icon}
              />
              {filter.title(this.localizedString)()}
              {filter.count > 0 && showCounts
                ? (
                  <span
                    className={styles.count}
                  >
                    ({displayCount(filter.count, true)})
                  </span>
                )
                : undefined
              }
              {filter.enabled ? (
                <Icon
                  type="check-circle"
                  className="cp-icon-larger"
                  style={{
                    position: 'absolute',
                    right: 0
                  }}
                />
              ) : null}
            </div>
          </Menu.Item>
        ))}
      </Menu>
    );
    return (
      <Dropdown
        overlay={filterMenu}
        onVisibleChange={this.onFilterVisibleChange}
        visible={overlayVisible}
      >
        <Button
          size={size}
          className={classNames(
            styles.documentFilterBtn,
            'cp-search-faceted-button'
          )}
        >
          <Icon
            type="filter"
            className={classNames(
              'cp-icon-larger',
              {'selected': this.activeFilters.length > 0}
            )}
          />
        </Button>
      </Dropdown>
    );
  }
}

DocumentTypeFilter.propTypes = {
  disabled: PropTypes.bool,
  onChange: PropTypes.func,
  selection: PropTypes.array,
  values: PropTypes.array,
  size: PropTypes.string,
  showCounts: PropTypes.bool
};

const DocumentTypeFilterName = 'doc_type';

export {DocumentTypeFilterName};
export default DocumentTypeFilter;
