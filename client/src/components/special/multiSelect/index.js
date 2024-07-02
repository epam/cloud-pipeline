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
import classNames from 'classnames';
import {
  Icon,
  Input,
  Dropdown,
  Checkbox,
  Tag,
  Spin
} from 'antd';
import styles from './styles.css';

class MultiSelect extends React.Component {
  state = {
    selectedOptions: [],
    dropdownVisible: false,
    searchString: ''
  }

  get allSelected () {
    const {values} = this.props;
    return values && values.length === 0;
  }

  get selectedValues () {
    const {options, values} = this.props;
    if (this.allSelected) {
      return options;
    }
    return values;
  }

  get tags () {
    if (this.allSelected) {
      return ['all versions'];
    }
    return this.selectedValues;
  }

  get filteredOptions () {
    const {options} = this.props;
    const {searchString} = this.state;
    if (!searchString) {
      return [];
    }
    return (options || []).filter(o => o.includes(searchString));
  }

  onVisibleChange = (visible) => {
    this.setState({
      dropdownVisible: visible,
      searchString: ''
    });
  };

  removeValue = (key) => {
    const {onChange, values} = this.props;
    if (this.allSelected) {
      return onChange && onChange(this.selectedValues.filter(o => o !== key));
    }
    return onChange && onChange(values.filter(o => o !== key));
  };

  addValue = (key) => {
    const {onChange, values} = this.props;
    return onChange && onChange([...values, key]);
  };

  onSelectItem = (e, key) => {
    e && e.stopPropagation();
    const {values, options = []} = this.props;
    const {onChange} = this.props;
    const [lastInactiveKey] = options.length - values.length === 1
      ? options.filter(o => !values.includes(o))
      : [];
    if (lastInactiveKey && lastInactiveKey === key) {
      return onChange && onChange([]);
    }
    if (values.includes(key) || this.allSelected) {
      return this.removeValue(key);
    }
    return this.addValue(key);
  };

  onSearch = (e) => {
    this.setState({searchString: e.target.value});
  };

  renderDropdown = () => {
    const {disabled, pending} = this.props;
    return (
      <div className={styles.dropdownOverlay}>
        <div>
          <Input.Search
            disabled={disabled || pending}
            placeholder="Start typing to filter versions..."
            className={styles.menuItem}
            onChange={this.onSearch}
            value={this.state.searchString}
          />
        </div>
        {
          this.filteredOptions.map((option) => (
            <div
              key={option}
              className={styles.menuItem}
              onClick={(e) => this.onSelectItem(e, option)}
              style={{cursor: 'pointer'}}
            >
              <Checkbox
                checked={this.selectedValues.includes(option)}
                style={{pointerEvents: 'none'}}
              >
                {option}
              </Checkbox>
            </div>
          ))
        }
      </div>
    );
  };

  renderTags = () => {
    return (
      this.tags.map(value => (
        <Tag
          key={value}
          closable={!this.allSelected}
          className={styles.tag}
          onClose={(e) => {
            e && e.stopPropagation();
            this.removeValue(value);
          }}
          style={{margin: '3px'}}
        >
          {value}
        </Tag>
      )));
  };

  render () {
    const {disabled, pending} = this.props;
    const {dropdownVisible} = this.state;
    return (
      <Dropdown
        overlay={this.renderDropdown()}
        trigger={['click']}
        visible={dropdownVisible}
        disabled={disabled || pending}
        onVisibleChange={this.onVisibleChange}
        placement="bottomLeft"
      >
        <div
          className={classNames(
            styles.input,
            {[styles.disabled]: disabled || pending}
          )}
        >
          {pending ? (
            <Spin
              style={{
                width: '100%',
                marginTop: '5px'
              }}
              size="small"
            />) : this.renderTags()
          }
          <Icon
            className={classNames(
              styles.inputArrow,
              {[styles.expanded]: dropdownVisible}
            )}
            type="down"
            style={{fontSize: '10px'}}
          />
        </div>
      </Dropdown>
    );
  }
}

MultiSelect.PropTypes = {
  options: PropTypes.arrayOf(PropTypes.string),
  values: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  onChange: PropTypes.func,
  disabled: PropTypes.bool,
  pending: PropTypes.bool
};

export default MultiSelect;
