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
import AvailableStoragesBrowser from '../dialogs/AvailableStoragesBrowser';
import AWSRegionTag from '../../../special/AWSRegionTag';
import styles from './LimitMountsInput.css';

export const LIMIT_MOUNTS_PARAMETER = 'CP_CAP_LIMIT_MOUNTS';

@inject('dataStorageAvailable')
@observer
export class LimitMountsInput extends React.Component {

  static propTypes = {
    onChange: PropTypes.func,
    value: PropTypes.string,
    disabled: PropTypes.bool
  };

  state = {
    value: null,
    limitMountsDialogVisible: false
  };

  @computed
  get availableStorages () {
    if (this.props.dataStorageAvailable.loaded) {
      return (this.props.dataStorageAvailable.value || []).map(s => s);
    }
    return [];
  }

  @computed
  get selectedStorages () {
    if (this.state.value) {
      const ids = this.state.value.split(',').map(i => i.trim());
      return this.availableStorages.filter(s => ids.indexOf(`${s.id}`) >= 0);
    }
    return [];
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
    this.setState({
      value: mountsIds ? mountsIds.map(i => `${i}`).join(',') : null,
      limitMountsDialogVisible: false
    }, this.handleChange);
  };

  onCancelLimitMountsDialog = () => {
    this.setState({limitMountsDialogVisible: false});
  };

  handleChange = () => {
    this.props.onChange && this.props.onChange(this.state.value);
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
        {
          this.selectedStorages.length > 0 &&
          this.selectedStorages.map(s => {
            return (
              <span key={s.id} className={styles.storage}>
                <AWSRegionTag regionId={s.regionId} regionUID={s.regionName} />
                {s.name}
              </span>
            );
          })
        }
        {
          !this.state.value &&
          <span>All available storages</span>
        }
        <AvailableStoragesBrowser
          visible={this.state.limitMountsDialogVisible}
          availableStorages={this.availableStorages}
          selectedStorages={
            this.selectedStorages.length ? this.selectedStorages : this.availableStorages
          }
          onCancel={this.onCancelLimitMountsDialog}
          onSave={this.onSaveLimitMountsDialog}
        />
      </div>
    );
  }

  componentWillReceiveProps (nextProps) {
    if ('value' in nextProps) {
      const value = nextProps.value;
      this.setState({value});
    }
  }

  componentDidMount () {
    this.props.dataStorageAvailable.fetchIfNeededOrWait();
    this.setState({value: this.props.value});
  }

  componentWillUnmount() {
    this.props.dataStorageAvailable.invalidateCache();
  }

}
