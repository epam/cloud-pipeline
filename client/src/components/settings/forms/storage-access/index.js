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
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import classNames from 'classnames';

class StorageAccess extends React.Component {
  state = {
    dialogVisible: false
  };

  @computed
  get cloudRegions () {
    const {awsRegions} = this.props;
    if (awsRegions && awsRegions.loaded) {
      return (awsRegions.value || []).slice();
    }
    return [];
  }

  @computed
  get cloudProviders () {
    return [...new Set(this.cloudRegions.map(region => region.provider))];
  }

  @computed
  get storageAccessConfigurations () {
    const {preferences} = this.props;
    return preferences.storageOutsideAccessCredentials;
  }

  getStorageAccessConfigurationByProvider (provider) {
    const configurations = this.storageAccessConfigurations || {};
    return configurations[provider];
  }

  @computed
  get storageAccessAvailable () {
    const providers = this.cloudProviders.slice();
    return providers.some(provider => !!this.getStorageAccessConfigurationByProvider(provider));
  }

  onOpenDialog = () => {
    this.setState({
      dialogVisible: true
    });
  };

  onCloseDialog = () => {
    this.setState({
      dialogVisible: false
    });
  };

  render () {
    const {
      className,
      style
    } = this.props;
    if (!this.storageAccessAvailable) {
      return null;
    }
    return (
      <div
        className={
          classNames(
            className
          )
        }
        style={style}
      >
        <b>Storage access:</b>
        <a
          onClick={this.onOpenDialog}
          style={{marginLeft: 5}}
        >
          configure
        </a>
      </div>
    );
  }
}

StorageAccess.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object
};

export default inject('preferences', 'awsRegions')(observer(StorageAccess));
