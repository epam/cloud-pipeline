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

class ResponsiveContainer extends React.PureComponent {
  static propTypes = {
    className: PropTypes.string,
    onResize: PropTypes.func,
    style: PropTypes.object
  };

  state = {
    width: 0,
    height: 0
  };

  animationFrame;

  container;

  componentDidMount () {
    this.checkDimensions();
  }

  componentWillUnmount () {
    if (this.animationFrame) {
      cancelAnimationFrame(this.animationFrame);
    }
  }

  checkDimensions = () => {
    this.animationFrame = requestAnimationFrame(this.checkDimensions);
    if (this.container) {
      const width = this.container.clientWidth;
      const height = this.container.clientHeight;
      if (width !== this.state.width || height !== this.state.height) {
        this.setState({width, height}, () => {
          const {onResize} = this.props;
          if (onResize) {
            onResize(width, height);
          }
        });
      }
    }
  };

  initializeContainer = (container) => {
    this.container = container;
  };

  render () {
    const {
      className,
      children,
      style
    } = this.props;
    return (
      <div
        className={className}
        ref={this.initializeContainer}
        style={style}
      >
        {children}
      </div>
    );
  }
}

export default ResponsiveContainer;
