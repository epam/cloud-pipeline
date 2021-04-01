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

class HTMLRenderer extends React.Component {
  static propTypes = {
    htmlString: PropTypes.string.isRequired,
    style: PropTypes.object
  };

  state = {
    height: 150,
    width: 300
  }

  iframe;

  onLoad = () => {
    if (this.iframe && this.iframe.contentWindow) {
      setTimeout(() => this.handleResize(), 100);
    }
  }

  handleResize = () => {
    const {width, height} = this.state;
    const padding = 25;
    let contentWidth;
    let contentHeight;
    let error;
    try {
      contentWidth = Math.max(
        this.iframe.contentWindow.document.body.getBoundingClientRect().width,
        this.iframe.contentWindow.document.body.scrollWidth,
        width
      );
      contentHeight = Math.max(
        this.iframe.contentWindow.document.body.getBoundingClientRect().height,
        this.iframe.contentWindow.document.body.scrollHeight,
        height
      );
    } catch (err) {
      error = err;
    }
    if (!error) {
      this.setState({
        height: contentHeight + padding,
        width: contentWidth + padding
      });
    }
  }

  render () {
    const {htmlString, style} = this.props;
    const {height, width} = this.state;
    if (!htmlString) {
      return null;
    }
    const iframeStyles = Object.assign({
      height: `${height}px`,
      width: `${width}px`,
      border: 0,
      overflow: 'hidden'
    }, style);
    return (
      <iframe
        sandbox="allow-same-origin"
        srcDoc={htmlString}
        style={iframeStyles}
        ref={(iframe) => { this.iframe = iframe; }}
        onLoad={this.onLoad}
      />
    );
  }
}

export default HTMLRenderer;
