/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import {observer, inject} from 'mobx-react';
import {computed, observable} from 'mobx';
import {Alert, message} from 'antd';
import roleModel from '../../../../../utils/roleModel';
import LaunchLimits, {LIMIT_TYPES} from '../../../../../models/user/LaunchLimits';

const WARNING_TYPES = {
  exceedLimits: 'exceedLimits',
  possibleExceedLimits: 'possibleExceedLimits'
};

@roleModel.authenticationInfo
@inject('counter')
@observer
export default class AllowedInstancesCountWarning extends React.Component {
  @observable userLimits;

  componentDidMount () {
    this.fetchData();
  };

  @computed
  get runningInstancesCount () {
    const {counter} = this.props;
    if (counter && counter.loaded) {
      return counter.value;
    }
    return undefined;
  }

  @computed
  get isAdmin () {
    const {authenticatedUserInfo} = this.props;
    if (authenticatedUserInfo.loaded) {
      return authenticatedUserInfo.value.admin;
    }
    return false;
  }

  get isSingleNode () {
    const {payload = {}} = this.props;
    return (!payload.maxNodeCount && +payload.nodeCount === 1) ||
      (!payload.nodeCount && !payload.maxNodeCount);
  }

  get instancesToLaunch () {
    const {payload} = this.props;
    if (this.isSingleNode) {
      return {
        min: 1,
        max: 1
      };
    }
    const correctValue = (value) => {
      if (value === undefined || isNaN(value)) {
        return 0;
      }
      return +value;
    };
    let min = correctValue(payload.nodeCount);
    let max = correctValue(payload.maxNodeCount);
    if (min > max) {
      max = min;
    }
    return {
      min,
      max
    };
  }

  get warningType () {
    if (!this.userLimits || this.runningInstancesCount === undefined) {
      return undefined;
    }
    const {min, max} = this.instancesToLaunch;
    if (this.runningInstancesCount + min > this.userLimits) {
      return WARNING_TYPES.exceedLimits;
    }
    return this.runningInstancesCount + max > this.userLimits
      ? WARNING_TYPES.possibleExceedLimits
      : undefined;
  }

  fetchData = async () => {
    const {counter} = this.props;
    const limitsRequest = new LaunchLimits();
    counter.fetch();
    await limitsRequest.fetchIfNeededOrWait();
    if (limitsRequest.error) {
      message.error('Error loading maximum running instances limits', 5);
    } else {
      this.userLimits = limitsRequest.loaded && limitsRequest.value
        ? limitsRequest.value[LIMIT_TYPES.userLimit]
        : undefined;
    }
  };

  render () {
    const {style} = this.props;
    if (this.isAdmin || !this.warningType || !this.userLimits) {
      return null;
    }
    return (
      <div>
        <Alert
          message={this.warningType === WARNING_TYPES.exceedLimits
            ? 'Active runs limits exceeded'
            : 'Possible exceeding of active runs limits'
          }
          type="warning"
          showIcon
          style={style}
        />
        {`${this.userLimits}`}
      </div>
    );
  }
}

AllowedInstancesCountWarning.PropTypes = {
  payload: PropTypes.object,
  style: PropTypes.object
};
