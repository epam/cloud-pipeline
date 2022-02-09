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
import {Button, Icon} from 'antd';

import HcsImage from '../../../special/hcs-image';
import styles from '../preview.css';

class HCSPreview extends React.Component {
  state = {
    showAttributes: false
  }

  componentDidMount () {
    const {
      onHideInfo,
      onPreviewLoaded
    } = this.props;
    if (onHideInfo) {
      onHideInfo(true);
    }
    if (onPreviewLoaded) {
      onPreviewLoaded({requireMaximumSpace: true});
    }
  }

  renderAttributesInfo = () => {
    if (this.props.children) {
      return (
        <Button
          type="primary"
          shape="circle"
          icon="info"
          size="small"
          className={styles.attributesInfoBtn}
          onClick={this.showAttributes}
        />
      );
    } else {
      return null;
    }
  }

  showAttributes = () => {
    this.setState({
      showAttributes: true
    });
  }

  hideAttributes = () => {
    this.setState({
      showAttributes: false
    });
  }

  showAttributesPanel = () => {
    return (
      <div className={styles.attributesPanel}>
        <Icon
          type="close"
          size="small"
          onClick={this.hideAttributes}
        />
        {this.props.children}
      </div>
    );
  }

  render () {
    const {
      className,
      storageId,
      file
    } = this.props;
    return (
      <HcsImage
        className={className}
        path={file}
        storageId={storageId}
        style={{
          height: 'calc(100vh - 150px)'
        }}
      >
        {
          this.state.showAttributes
            ? this.showAttributesPanel()
            : this.renderAttributesInfo()
        }
      </HcsImage>
    );
  };
}

HCSPreview.propTypes = {
  className: PropTypes.string,
  file: PropTypes.string,
  storageId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  fullScreenAvailable: PropTypes.bool,
  shareAvailable: PropTypes.bool,
  x: PropTypes.number,
  y: PropTypes.number,
  zoom: PropTypes.number,
  roll: PropTypes.number,
  onCameraChanged: PropTypes.func,
  onPreviewLoaded: PropTypes.func,
  fullscreen: PropTypes.bool,
  onFullScreenChange: PropTypes.func,
  onHideInfo: PropTypes.func
};

HCSPreview.defaultProps = {
  fullScreenAvailable: true,
  shareAvailable: true
};

export default HCSPreview;
