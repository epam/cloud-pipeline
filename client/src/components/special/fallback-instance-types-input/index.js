/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import classNames from 'classnames';
import {Icon, Input} from 'antd';
import Dropdown from 'rc-dropdown';
import {instanceInfoString} from '../instance-type-info';
import styles from './fallback-instance-types-input.css';

function normalizeValue (value) {
  if (!value) {
    return [];
  }
  if (Array.isArray(value)) {
    return value.filter(o => o !== undefined && o !== null && `${o}`.length > 0);
  }
  return [value];
}

@inject('preferences')
@observer
export class FallbackInstanceTypesInput extends React.Component {
  static propTypes = {
    className: PropTypes.string,
    style: PropTypes.object,
    // eslint-disable-next-line react/forbid-prop-types
    value: PropTypes.array,
    onChange: PropTypes.func,
    disabled: PropTypes.bool,
    // eslint-disable-next-line react/forbid-prop-types
    instanceTypes: PropTypes.array,
    hyperThreadingDisabled: PropTypes.bool,
    displayRegion: PropTypes.bool,
    showReservationTag: PropTypes.bool,
    maximumCount: PropTypes.number,
    size: PropTypes.oneOf(['small', 'default', 'large']),
    placeholder: PropTypes.string
  };

  static defaultProps = {
    placeholder: 'Fallback instance types'
  };

  state = {
    expanded: false,
    active: false,
    search: undefined
  };

  input;

  @computed
  get maximumCount () {
    const {maximumCount, preferences} = this.props;
    if (maximumCount !== undefined && maximumCount !== null) {
      return maximumCount;
    }
    return preferences ? preferences.maximumFallbackInstanceTypes : undefined;
  }

  get value () {
    return normalizeValue(this.props.value);
  }

  get hasLimit () {
    const {maximumCount} = this;
    return maximumCount !== undefined && maximumCount !== null;
  }

  get hidden () {
    // when no fallback instance types are allowed, the control is not displayed
    return this.hasLimit && this.maximumCount <= 0;
  }

  get limitReached () {
    return this.hasLimit && this.value.length >= this.maximumCount;
  }

  get expanded () {
    return this.state.expanded || this.value.length > 0;
  }

  /**
   * "X instance types selected" summary, or undefined when nothing is selected.
   * @returns {string|undefined}
   */
  get summaryText () {
    const count = this.value.length;
    if (count === 0) {
      return undefined;
    }
    return `${count} instance ${count === 1 ? 'type' : 'types'} selected`;
  }

  /**
   * Value displayed in the main input.
   * - active: the user's search string (empty when nothing is typed yet)
   * - not active: the "X selected" summary
   * @returns {string}
   */
  get inputValue () {
    if (this.state.active) {
      return this.state.search || '';
    }
    return this.summaryText || '';
  }

  /**
   * Placeholder shown in the main input.
   * - active: the "X selected" summary (or the generic placeholder when empty)
   * - not active: the generic placeholder only when nothing is selected
   * @returns {string|undefined}
   */
  get inputPlaceholder () {
    const {placeholder} = this.props;
    if (this.state.active) {
      return this.summaryText || placeholder;
    }
    return this.summaryText ? undefined : placeholder;
  }

  /**
   * Instance types grouped by family, filtered by the current search string.
   * @returns {{family: string, instances: Object[]}[]}
   */
  get groupedInstanceTypes () {
    const {instanceTypes = []} = this.props;
    const searchString = this.state.active ? (this.state.search || '').toLowerCase() : '';
    const filtered = searchString.length > 0
      ? instanceTypes.filter(
        (instance) => (instance.name || '').toLowerCase().indexOf(searchString) >= 0
      )
      : instanceTypes;
    const families = [...new Set(filtered.map((i) => i.instanceFamily))];
    return families.map((family) => ({
      family: family || 'Other',
      instances: filtered.filter((i) => i.instanceFamily === family)
    }));
  }

  optionDisabled = (instance) => {
    if (!this.limitReached) {
      return false;
    }
    // when the maximum is reached, only already-selected types remain selectable
    return this.value.indexOf(instance.name) < 0;
  };

  isSelected = (instance) => this.value.indexOf(instance.name) >= 0;

  initializeInput = (input) => {
    this.input = input;
  };

  onExpand = () => {
    if (this.props.disabled) {
      return;
    }
    this.setState({expanded: true, active: true}, () => {
      if (this.input) {
        this.input.focus();
      }
    });
  };

  onFocus = () => {
    if (this.props.disabled) {
      return;
    }
    this.setState({active: true});
  };

  onBlur = () => {
    this.setState({active: false, search: undefined});
  };

  onSearch = (event) => {
    if (!this.state.active) {
      return;
    }
    this.setState({search: event.target.value});
  };

  toggleInstance = (instance) => {
    const {onChange, disabled} = this.props;
    if (disabled || !onChange) {
      return;
    }
    const current = this.value;
    const index = current.indexOf(instance.name);
    let next;
    if (index >= 0) {
      next = current.filter((name) => name !== instance.name);
    } else {
      if (this.optionDisabled(instance)) {
        return;
      }
      next = [...current, instance.name];
    }
    onChange(next.length > 0 ? next : undefined);
  };

  onClear = (event) => {
    if (event) {
      event.stopPropagation();
    }
    const {onChange, disabled} = this.props;
    if (disabled || !onChange) {
      return;
    }
    onChange(undefined);
  };

  renderLink () {
    const {className, style, disabled} = this.props;
    if (disabled) {
      return null;
    }
    return (
      <a
        className={classNames(styles.link, 'cp-text', 'underline', className)}
        style={style}
        onClick={this.onExpand}
      >
        <Icon type="plus" />
        Specify fallback instance types
      </a>
    );
  }

  renderOption = (instance) => {
    const {
      hyperThreadingDisabled,
      displayRegion,
      showReservationTag
    } = this.props;
    const selected = this.isSelected(instance);
    const disabled = this.optionDisabled(instance);
    return (
      <div
        key={instance.sku || instance.name}
        className={classNames(
          styles.option,
          'cp-table-element',
          {
            [styles.selected]: selected,
            'cp-table-element-selected': selected,
            [styles.disabled]: disabled
          }
        )}
        // prevent the main input from losing focus so the dropdown stays open
        onMouseDown={(event) => event.preventDefault()}
        onClick={() => this.toggleInstance(instance)}
      >
        <Icon
          type="check"
          className={classNames(
            styles.optionCheck,
            {[styles.optionCheckHidden]: !selected}
          )}
        />
        <span className={styles.optionLabel}>
          {
            instanceInfoString(
              instance,
              {
                hyperThreadingDisabled,
                displayRegion,
                showReservationTag,
                plainText: false
              }
            )
          }
        </span>
      </div>
    );
  };

  renderOverlay = () => {
    const {maximumCount} = this;
    const groups = this.groupedInstanceTypes;
    return (
      <div className={classNames('cp-panel', 'cp-panel-no-hover', 'borderless')}>
        {
          this.hasLimit
            ? (
              <div className={classNames(styles.hint, 'cp-text-not-important')}>
                Up to {maximumCount} instance{maximumCount === 1 ? ' type' : ' types'} can
                be selected.
              </div>
            )
            : null
        }
        <div className={styles.options}>
          {
            groups.length === 0
              ? (
                <div className={classNames(styles.empty, 'cp-text-not-important')}>
                  No instance types found
                </div>
              )
              : groups.map((group) => (
                <div key={group.family} className={styles.group}>
                  <div className={classNames(styles.groupTitle, 'cp-text-not-important')}>
                    {group.family}
                  </div>
                  {group.instances.map(this.renderOption)}
                </div>
              ))
          }
        </div>
      </div>
    );
  };

  renderInput () {
    const {
      className,
      style,
      disabled,
      size
    } = this.props;
    const {active} = this.state;
    const count = this.value.length;
    const clearIcon = count > 0 && !disabled
      ? (
        <Icon
          type="close-circle"
          className={styles.clear}
          onMouseDown={(event) => event.preventDefault()}
          onClick={this.onClear}
        />
      )
      : null;
    return (
      <div className={classNames(styles.container, className)} style={style}>
        <Dropdown
          disabled={disabled}
          visible={active}
          trigger={[]}
          overlay={this.renderOverlay()}
          minOverlayWidthMatchTrigger
        >
          <Input
            ref={this.initializeInput}
            className={styles.input}
            size={size}
            disabled={disabled}
            value={this.inputValue}
            placeholder={this.inputPlaceholder}
            suffix={clearIcon}
            onChange={this.onSearch}
            onFocus={this.onFocus}
            onBlur={this.onBlur}
          />
        </Dropdown>
      </div>
    );
  }

  render () {
    if (this.hidden) {
      return null;
    }
    if (this.expanded) {
      return this.renderInput();
    }
    return this.renderLink();
  }
}

export default FallbackInstanceTypesInput;
