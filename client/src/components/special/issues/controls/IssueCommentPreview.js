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
import {Modal} from 'antd';
import styles from './IssueComment.css';
import 'highlight.js/styles/github.css';

@inject('issuesRenderer')
@observer
export default class IssueCommentPreview extends React.Component {

  static propTypes = {
    text: PropTypes.string,
    style: PropTypes.object
  };

  state = {
    previewUrl: null,
    previewName: null
  };

  click = (e) => {
    if (e.target && e.target.tagName && e.target.tagName.toLowerCase() === 'img' && e.target.src) {
      const width = e.target.naturalWidth;
      const height = e.target.naturalHeight;
      const windowWidth = window.innerWidth;
      const windowHeight = window.innerHeight;
      let previewWidth = width;
      let previewHeight = height;
      if (width > windowWidth || height > windowHeight) {
        const ratioWH = width / height;
        const ratioHW = height / width;
        const windowWidthMax = windowWidth * 0.9;
        const windowHeightMax = windowHeight * 0.9;
        if (windowWidthMax * ratioHW <= windowHeightMax) {
          previewWidth = windowWidthMax;
          previewHeight = windowWidthMax * ratioHW;
        } else if (windowHeightMax * ratioWH <= windowWidthMax) {
          previewHeight = windowHeightMax;
          previewWidth = windowHeightMax * ratioWH;
        } else {
          previewWidth = windowWidthMax;
          previewHeight = windowHeightMax;
        }
      }
      this.setState({
        previewUrl: e.target.src,
        previewName: e.target.alt || 'Image',
        previewWidth: previewWidth,
        previewHeight: previewHeight
      });
    }
  };

  onClosePreview = () => {
    this.setState({
      previewUrl: null,
      previewName: null
    });
  };

  render () {
    if (!this.props.issuesRenderer.ready) {
      return null;
    }
    return (
      <div style={{overflowY: 'auto'}}>
        <div
          onClick={this.click}
          id="description-text-container"
          className={styles.mdPreview}
          style={this.props.style || {}}
          dangerouslySetInnerHTML={{__html: this.props.issuesRenderer.render(this.props.text)}} />
        <Modal
          width={this.state.previewWidth}
          height={this.state.previewHeight}
          bodyStyle={{padding: 0}}
          onCancel={this.onClosePreview}
          footer={false}
          title={this.state.previewName}
          visible={!!this.state.previewUrl}>
          <img src={this.state.previewUrl} style={{width: '100%', height: '100%'}} />
        </Modal>
      </div>
    );
  }
}
