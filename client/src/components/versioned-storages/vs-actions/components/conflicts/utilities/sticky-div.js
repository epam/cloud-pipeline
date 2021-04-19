/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import getStyle, {getBrowser} from '../../../../../../utils/browserDependentStyle';

const browser = getBrowser();
const isIE = /^ie$/i.test(browser.name);

class StickyDiv extends React.Component {
  static Placement = {
    left: 'left',
    right: 'right'
  };

  div;

  componentDidMount () {
    const {onInitialized} = this.props;
    if (onInitialized) {
      onInitialized(this);
    }
    this.attachEventListeners();
    this.processPosition();
  }

  componentWillUnmount () {
    const {onInitialized} = this.props;
    if (onInitialized) {
      onInitialized(null);
    }
    this.detachEventListeners();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.to !== this.props.to) {
      this.detachEventListeners(prevProps.to);
      this.attachEventListeners();
    }
  }

  attachEventListeners = () => {
    if (!isIE) {
      return;
    }
    const {to: stickyParent} = this.props;
    if (stickyParent) {
      console.log('attaching scroll event listener');
      stickyParent.addEventListener('scroll', this.onScroll);
      this.processPosition();
    }
  };

  detachEventListeners = (stickyParent) => {
    if (!isIE) {
      return;
    }
    if (!stickyParent) {
      stickyParent = this.props.to;
    }
    if (stickyParent) {
      console.log('detaching scroll event listener');
      stickyParent.removeEventListener('scroll', this.onScroll);
    }
  };

  divRef = (div) => {
    this.div = div;
    this.processPosition();
  };

  onScroll = () => {
    this.processPosition();
  };

  processPosition = (left = undefined) => {
    if (!isIE) {
      return;
    }
    const {placement, to: stickyParent} = this.props;
    if (stickyParent && this.div) {
      const parentWidth = stickyParent.clientWidth;
      const scrollWidth = stickyParent.scrollWidth;
      const width = this.div.clientWidth;
      if (left === undefined) {
        left = stickyParent.scrollLeft;
      }
      if (placement === StickyDiv.Placement.right) {
        left = (parentWidth - width) + left;
        stickyParent.style['padding-right'] = `${width}px`;
        left = Math.max(0, Math.min(scrollWidth - width, left));
      } else {
        stickyParent.style['padding-left'] = `${width}px`;
        left = Math.max(0, Math.min(scrollWidth - parentWidth, left));
      }
      this.div.style.left = `${left}px`;
    }
  };

  render () {
    const {
      children,
      className,
      placement,
      style
    } = this.props;
    return (
      <div
        className={className}
        style={
          Object.assign(
            {},
            style,
            getStyle({
              ie: {
                position: 'absolute',
                top: 0
              },
              default: {
                position: 'sticky',
                left: placement === StickyDiv.Placement.left ? 0 : undefined,
                right: placement === StickyDiv.Placement.right ? 0 : undefined
              }
            })
          )
        }
        ref={this.divRef}
      >
        {children}
      </div>
    );
  }
}

StickyDiv.propTypes = {
  children: PropTypes.node,
  className: PropTypes.string,
  onInitialized: PropTypes.func,
  placement: PropTypes.oneOf([StickyDiv.Placement.left, StickyDiv.Placement.right]),
  style: PropTypes.object,
  to: PropTypes.object
};

export default StickyDiv;
