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
import classNames from 'classnames';
import {computed} from 'mobx';
import AvailableStoragesBrowser, {filterNFSStorages}
from '../dialogs/AvailableStoragesBrowser';
import AWSRegionTag from '../../../special/AWSRegionTag';
import styles from './LimitMountsInput.css';
import {
  correctLimitMountsParameterValue, storageMatchesIdentifiers
} from '../../../../utils/limit-mounts/get-limit-mounts-storages';

@inject('dataStorageAvailable', 'preferences')
@observer
export class LimitMountsInput extends React.Component {
  static propTypes = {
    onChange: PropTypes.func,
    value: PropTypes.string,
    disabled: PropTypes.bool,
    showOnlySummary: PropTypes.bool,
    allowSensitive: PropTypes.bool,
    className: PropTypes.string
  };

  static defaultProps = {
    allowSensitive: true
  };

  state = {
    value: null,
    limitMountsDialogVisible: false
  };

  componentDidMount () {
    this.props.dataStorageAvailable.fetch();
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
          this.setState({
            value: correctLimitMountsParameterValue(
              value,
              dataStorageAvailable.value || [],
              {allowSensitive}
            )
          }, this.handleChange);
        })
        .catch(console.error);
    } else {
      this.setState({
        value
      }, this.handleChange);
    }
  };

  @computed
  get nfsSensitivePolicy () {
    const {preferences} = this.props;
    return preferences.nfsSensitivePolicy;
  }

  @computed
  get availableStorages () {
    if (this.props.dataStorageAvailable.loaded) {
      return (this.props.dataStorageAvailable.value || [])
        .filter(s => !s.mountDisabled && (this.props.allowSensitive || !s.sensitive) && !s.shared)
        .map(s => s);
    }
    return [];
  }

  @computed
  get availableNonSensitiveStorages () {
    return this.availableStorages.filter(s => !s.sensitive);
  }

  allNonSensitiveStorages (selection) {
    return (selection || []).length === this.availableNonSensitiveStorages.length &&
      this.availableNonSensitiveStorages
        .filter(s => storageMatchesIdentifiers(s, selection || []))
        .length === this.availableNonSensitiveStorages.length;
  }

  allStorages (selection) {
    const hasSensitive = !!this.availableStorages.find(s => s.sensitive);
    const all = this.availableStorages
      .filter(filterNFSStorages(this.nfsSensitivePolicy, hasSensitive));
    return (selection || []).length === all.length &&
      all.filter(s => storageMatchesIdentifiers(s, selection)).length === all.length;
  }

  get selectedStorages () {
    if (this.state.value) {
      if (/^none$/i.test(this.state.value)) {
        return [];
      }
      const ids = this.state.value.split(',');
      return this.availableStorages.filter(s => storageMatchesIdentifiers(s, ids));
    }
    return this.availableNonSensitiveStorages;
  }

  get hasSelectedSensitiveStorages () {
    return !!this.selectedStorages.find(s => s.sensitive);
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
    const {showOnlySummary} = this.props;
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
    if (this.allStorages(this.selectedStorages.map(s => s.id))) {
      return (
        <span>
          All available storages
        </span>
      );
    }
    const filteredSelectedStorages = this.selectedStorages
      .filter(filterNFSStorages(
        this.nfsSensitivePolicy,
        this.hasSelectedSensitiveStorages
      ));
    if (showOnlySummary) {
      return (
        <p>
          {filteredSelectedStorages.map(s => s.id).join(',')}
        </p>
      );
    }
    return filteredSelectedStorages.map(s => (
      <span
        key={s.id}
        className={classNames(styles.storage, 'cp-limit-mounts-input-tag')}
      >
        <AWSRegionTag regionId={s.regionId} regionUID={s.regionName} />
        {s.name}
      </span>
    ));
  };

  render () {
    return (
      <div
        ref={this.initializeInput}
        onFocus={this.openLimitMountsDialog}
        tabIndex={0}
        className={
          classNames(
            this.props.className,
            styles.limitMountsInput,
            'cp-limit-mounts-input',
            {
              disabled: this.props.disabled,
              [styles.disabled]: this.props.disabled,
              [styles.summary]: this.props.showOnlySummary
            }
          )
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
