/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

const UPDATE_SIZE_TIMER_MS = 250;

class ResizableContainer extends React.Component {
  state = {
    container: undefined,
    width: 0,
    height: 0
  };

  componentDidMount () {
    this.updateTimer = setInterval(() => this.updateContainerSize(), UPDATE_SIZE_TIMER_MS);
  }

  componentWillUnmount () {
    clearInterval(this.updateTimer);
  }

  initializeContainer = (container) => {
    this.setState({container}, this.updateContainerSize);
  };

  updateContainerSize = () => {
    const {container, width, height} = this.state;
    if (container) {
      const containerWidth = container.clientWidth;
      const containerHeight = container.clientHeight;
      if (containerWidth !== width || containerHeight !== height) {
        this.setState(
          {
            width: containerWidth,
            height: containerHeight
          }
        );
      }
    }
  };

  render () {
    const {width, height} = this.state;
    const {
      children: childrenFn,
      className,
      style
    } = this.props;
    return (
      <div
        className={className}
        ref={this.initializeContainer}
        style={Object.assign({overflow: 'hidden'}, style)}
      >
        {
          childrenFn
            ? childrenFn({width, height})
            : undefined
        }
      </div>
    );
  }
}

ResizableContainer.propTypes = {
  className: PropTypes.string,
  children: PropTypes.func,
  style: PropTypes.object
};

export default ResizableContainer;
