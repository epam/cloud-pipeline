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
import {computed} from 'mobx';
import {Alert} from 'antd';

const WARNING_TYPES = {
  exceedLimits: 'exceedLimits',
  possibleExceedLimits: 'possibleExceedLimits'
};

@inject('counter', 'preferences')
@observer
export default class AllowedInstancesCountWarning extends React.Component {
  componentDidMount () {
    const {counter} = this.props;
    counter.fetchIfNeededOrWait();
  }

  @computed
  get runningInstancesCount () {
    const {counter} = this.props;
    if (counter && counter.loaded) {
      return counter.value;
    }
    return undefined;
  }

  get instancesLimit () {
    const {preferences} = this.props;
    return preferences.allowedInstancesMaxCount;
  }

  get isSingleNode () {
    const {payload = {}} = this.props;
    return +payload.nodeCount === 1 || (!payload.nodeCount && !payload.maxNodeCount);
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
    if (this.runningInstancesCount + this.instancesToLaunch.min > this.instancesLimit) {
      return WARNING_TYPES.exceedLimits;
    }
    return this.runningInstancesCount + this.instancesToLaunch.max > this.instancesLimit
      ? WARNING_TYPES.possibleExceedLimits
      : undefined;
  }

  render () {
    const {style} = this.props;
    if (!this.warningType) {
      return null;
    }
    return (
      <Alert
        message={this.warningType === WARNING_TYPES.exceedLimits
          ? 'Active runs limits exceeded'
          : 'Possible exceeding of active runs limits'
        }
        type="warning"
        showIcon
        style={style}
      />
    );
  }
}

AllowedInstancesCountWarning.PropTypes = {
  payload: PropTypes.object,
  instancesToLaunch: PropTypes.oneOf([PropTypes.number, PropTypes.string]),
  maxInstancesToLaunch: PropTypes.oneOf([PropTypes.number, PropTypes.string]),
  style: PropTypes.object
};
