/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import AvailableStoragesBrowser
  from '../dialogs/AvailableStoragesBrowser';
import AWSRegionTag from '../../../special/AWSRegionTag';
import styles from './LimitMountsInput.css';

@inject('dataStorageAvailable')
@observer
export class LimitMountsInput extends React.Component {
  static propTypes = {
    onChange: PropTypes.func,
    value: PropTypes.string,
    disabled: PropTypes.bool,
    allowSensitive: PropTypes.bool
  };

  static defaultProps = {
    allowSensitive: true
  };

  state = {
    value: null,
    limitMountsDialogVisible: false
  };

  componentDidMount () {
    this.updateFromProps();
  }

  componentWillUnmount () {
    this.props.dataStorageAvailable.invalidateCache();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      (prevProps.allowSensitive !== this.props.allowSensitive) ||
      (prevProps.value !== this.props.value)
    ) {
      this.updateFromProps();
    }
  }

  updateFromProps = () => {
    const {
      allowSensitive,
      dataStorageAvailable,
      value
    } = this.props;
    if (!allowSensitive && value && !/^none$/i.test(value)) {
      dataStorageAvailable
        .fetchIfNeededOrWait()
        .then(() => {
          const nonSensitiveStorageIds = (dataStorageAvailable.value || [])
            .filter(s => !s.sensitive)
            .map(s => +s.id);
          const mountsIds = value
            .split(',')
            .map(id => +id)
            .filter(id => nonSensitiveStorageIds.indexOf(id) >= 0);
          const newValue = mountsIds ? mountsIds.map(i => `${i}`).join(',') : null;
          if (newValue !== value) {
            this.setState({
              value: newValue
            }, this.handleChange);
          }
        })
        .catch(console.error);
    } else {
      this.setState({
        value
      }, this.handleChange);
    }
  };

  @computed
  get availableStorages () {
    if (this.props.dataStorageAvailable.loaded) {
      return (this.props.dataStorageAvailable.value || [])
        .filter(s => this.props.allowSensitive || !s.sensitive)
        .map(s => s);
    }
    return [];
  }

  @computed
  get availableNonSensitiveStorages () {
    return this.availableStorages.filter(s => !s.sensitive);
  }

  allNonSensitiveStorages (selection) {
    const ids = new Set(selection.map(id => +id));
    return ids.size === this.availableNonSensitiveStorages.length &&
      this.availableNonSensitiveStorages
        .filter(s => ids.has(+s.id))
        .length === this.availableNonSensitiveStorages.length;
  }

  get selectedStorages () {
    if (this.state.value) {
      if (/^none$/i.test(this.state.value)) {
        return [];
      }
      const ids = new Set(this.state.value.split(',').map(i => +i));
      return this.availableStorages.filter(s => ids.has(s.id));
    }
    return this.availableNonSensitiveStorages;
  }

  input;

  initializeInput = (input) => {
    this.input = input;
  };

  openLimitMountsDialog = () => {
    if (this.input) {
      this.input.blur();
    }
    if (!this.props.disabled) {
      this.setState({
        limitMountsDialogVisible: true
      });
    }
  };

  onSaveLimitMountsDialog = (mountsIds) => {
    let value = mountsIds.join(',');
    if (mountsIds.length === 0) {
      value = 'None';
    } else if (this.allNonSensitiveStorages(mountsIds)) {
      value = null;
    }
    this.setState({
      value,
      limitMountsDialogVisible: false
    }, this.handleChange);
  };

  onCancelLimitMountsDialog = () => {
    this.setState({limitMountsDialogVisible: false});
  };

  handleChange = () => {
    this.props.onChange && this.props.onChange(this.state.value);
  };

  renderContent = () => {
    if (this.selectedStorages.length === 0) {
      return (
        <span>No storages will be mounted</span>
      );
    }
    if (this.allNonSensitiveStorages(this.selectedStorages.map(s => s.id))) {
      return (
        <span>
          All available non-sensitive storages
        </span>
      );
    }
    if (this.selectedStorages.length === this.availableStorages.length) {
      return (
        <span>
          All available storages
        </span>
      );
    }
    return this.selectedStorages.map(s => {
      return (
        <span key={s.id} className={styles.storage}>
          <AWSRegionTag regionId={s.regionId} regionUID={s.regionName} />
          {s.name}
        </span>
      );
    });
  };

  render () {
    return (
      <div
        ref={this.initializeInput}
        onFocus={this.openLimitMountsDialog}
        tabIndex={0}
        className={
          !this.props.disabled
            ? styles.limitMountsInput
            : `${styles.limitMountsInput} ${styles.disabled}`
        }
      >
        {this.renderContent()}
        <AvailableStoragesBrowser
          visible={this.state.limitMountsDialogVisible}
          availableStorages={this.availableStorages}
          selectedStorages={this.selectedStorages.map(s => s.id)}
          onCancel={this.onCancelLimitMountsDialog}
          onSave={this.onSaveLimitMountsDialog}
        />
      </div>
    );
  }
}
