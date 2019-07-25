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
import {
  Alert,
  Button,
  Row
} from 'antd';
import displaySize from '../../../utils/displaySize';
import DataStorageItemSize from '../../../models/dataStorage/DataStorageItemSize';

const KB = 1024;
const MB = 1024 * KB;
const GB = 1024 * MB;
const TB = 1024 * GB;

const MAXIMUM = 16 * TB;

function getPaths (parameters) {
  const types = [
    'input',
    'common'
  ];
  return Object.values(parameters || {})
    .filter(v => types.indexOf(v.type) >= 0)
    .map(v => v.value)
    .reduce((paths, value) => ([...paths, ...value.split(',')]), [])
    .map(v => v.trim());
}

@inject((stores, {parameters}) => {
  const paths = getPaths(parameters);
  let sizeRequest;
  if (paths.length > 0) {
    sizeRequest = new DataStorageItemSize();
    sizeRequest.send(paths);
  }
  return {
    sizeRequest
  };
})
@observer
class EstimatedDiskSizeWarning extends React.Component {
  static propTypes = {
    parameters: PropTypes.object,
    nodeCount: PropTypes.number,
    hddSize: PropTypes.number,
    onDiskSizeChanged: PropTypes.func
  };

  state = {
    suggestionAccepted: false
  };

  get requestedSize () {
    const {hddSize, nodeCount} = this.props;
    return (hddSize || 0) * GB * ((nodeCount || 0) + 1);
  }

  get totalSize () {
    const {sizeRequest} = this.props;
    if (sizeRequest && sizeRequest.loaded) {
      return (sizeRequest.value || [])
        .filter(v => v.completed)
        .map(v => v.size)
        .reduce((sum, current) => sum + current, 0);
    }
    return 0;
  }

  get suggestedSize () {
    const {nodeCount} = this.props;
    return Math.min(MAXIMUM, this.totalSize * 3) / ((nodeCount || 0) + 1);
  }

  acceptSuggestion = () => {
    this.setState({suggestionAccepted: true}, () => {
      const {onDiskSizeChanged} = this.props;
      if (onDiskSizeChanged) {
        onDiskSizeChanged(Math.ceil(this.suggestedSize / GB));
      }
    });
  };

  render () {
    const {sizeRequest} = this.props;
    if (!sizeRequest || (sizeRequest.pending && !sizeRequest.loaded)) {
      return null;
    }
    if (sizeRequest.error) {
      return (
        <Alert
          type="error"
          message={sizeRequest.message}
        />
      );
    }
    if (this.requestedSize >= this.totalSize) {
      return null;
    }
    const {suggestionAccepted} = this.state;
    if (suggestionAccepted) {
      return (
        <Alert
          type="info"
          showIcon
          style={{margin: 2}}
          message={(
            <div>
              <p>
                The disk size is set to {displaySize(this.suggestedSize, false)}.
              </p>
            </div>
          )}
        />
      );
    }
    if (this.suggestedSize >= MAXIMUM) {
      return (
        <Alert
          type="warning"
          showIcon
          style={{margin: 2}}
          message={(
            <div>
              <p>
                <span>The requested disk size for this run is </span>
                <b>{displaySize(this.requestedSize, false)}</b>
                <span>, but the data that is going to be processed exceeds </span>
                <b>{displaySize(MAXIMUM)}</b>
                <span> (which is a hard limit). Please use the cluster run configuration to scale the disks horizontally or reduce the input data volume.</span>
              </p>
              <p>
                <span>Do you want to use the maximum disk size </span>
                <b>{displaySize(MAXIMUM, false)}</b>
                <span> anyway?</span>
              </p>
              <Row type="flex" justify="space-around">
                <Button
                  type="primary"
                  size="small"
                  onClick={this.acceptSuggestion}
                >
                  Set maximum disk size ({displaySize(MAXIMUM, false)})
                </Button>
              </Row>
            </div>
          )}
        />
      );
    }
    return (
      <Alert
        type="warning"
        showIcon
        style={{margin: 2}}
        message={(
          <div>
            <p>
              <span>The requested disk size for this run is </span>
              <b>{displaySize(this.requestedSize, false)}</b>
              <span>, but the data that is going to be processed exceeds it (estimated volume: </span>
              <b>{displaySize(this.totalSize, false)}</b>
              <span>). This may cause run to fail with "Out Of Disk" error.</span>
            </p>
            <p>
              <span>Do you want to use the suggested disk size </span>
              <b>{displaySize(this.suggestedSize, false)}</b>
              <span> (which also considers interim and resulting datasets)?</span>
            </p>
            <Row type="flex" justify="space-around">
              <Button
                type="primary"
                size="small"
                onClick={this.acceptSuggestion}
              >
                Set suggested disk size ({displaySize(this.suggestedSize, false)})
              </Button>
            </Row>
          </div>
        )}
      />
    );
  }
}

export default EstimatedDiskSizeWarning;
