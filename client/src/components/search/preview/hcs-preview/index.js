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

import HcsImage from '../../../special/hcs-image';
import LoadingView from '../../../special/LoadingView';
import styles from '../preview.css'; ;

function getHcsInfo () {
  return true;
}
@inject('dataStorageCache', 'dataStorages', 'preferences')
@observer
class HCSPreview extends React.Component {
  state = {
    items: [],
    preview: undefined,
    pending: false
  };

  componentDidMount () {
    this.fetchPreviewItems();
    this.props.onHideInfo(true);
  }

  componentWillUnmount () {}

  componentDidUpdate (prevProps, prevState, snapshot) {}

  fetchPreviewItems = () => {}

  renderPreview = () => {
    const {
      preview,
      pending,
      items
    } = this.state;
    if (!preview || !items || !items.length) {
      return null;
    }
    const {
      pending: previewPending,
      error,
      url
    } = preview;
    let content;
    if (pending || previewPending) {
      content = (<i style={{color: '#999'}}>Loading...</i>);
    } else if (error) {
      content = (<span style={{color: '#999'}}>{error}</span>);
    } else if (!url) {
      content = (<span style={{color: '#999'}}>Preview not available</span>);
    } else {
      content = (
        <HcsImage />
      );
    }
    return (
      <div className={styles.vsiContentPreview}>
        {content}
      </div>
    );
  };

  render () {
    const {
      className
    } = this.props;
    const {
      pending
    } = this.state;
    return (
      <div
        className={className}
      >
        {pending && (<LoadingView />)}
        {!pending && this.renderPreview()}
      </div>
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
export {getHcsInfo};
