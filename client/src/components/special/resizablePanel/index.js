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
import {observer} from 'mobx-react';
import styles from './styles.css';

export const ResizeAnchors = {
  top: 'top',
  topLeft: 'top-left',
  left: 'left',
  bottomLeft: 'bottom-left',
  bottom: 'bottom',
  bottomRight: 'bottom-right',
  right: 'right',
  topRight: 'top-right'
};

const resizeFactorForAnchor = {
  [ResizeAnchors.top]: {dx: 0, dy: -1},
  [ResizeAnchors.bottom]: {dx: 0, dy: 1},
  [ResizeAnchors.left]: {dx: -1, dy: 0},
  [ResizeAnchors.right]: {dx: 1, dy: 0},
  [ResizeAnchors.topLeft]: {dx: -1, dy: -1},
  [ResizeAnchors.topRight]: {dx: 1, dy: -1},
  [ResizeAnchors.bottomLeft]: {dx: -1, dy: 1},
  [ResizeAnchors.bottomRight]: {dx: 1, dy: 1}
};

const anchorsOrder = [
  ResizeAnchors.top,
  ResizeAnchors.bottom,
  ResizeAnchors.left,
  ResizeAnchors.right,
  ResizeAnchors.topLeft,
  ResizeAnchors.topRight,
  ResizeAnchors.bottomLeft,
  ResizeAnchors.bottomRight
];

function sortAnchors (a1, a2) {
  const i1 = anchorsOrder.indexOf(a1);
  const i2 = anchorsOrder.indexOf(a2);
  if (i1 > i2) {
    return 1;
  } else if (i1 < i2) {
    return -1;
  }
  return 0;
}

@observer
export class ResizablePanel extends React.Component {

  static propTypes = {
    className: PropTypes.string,
    resizeAnchors: PropTypes.arrayOf(PropTypes.oneOf(Object.values(ResizeAnchors)))
  };

  static defaultProps = {
    resizeAnchors: Object.values(ResizeAnchors)
  };

  state = {
    currentAnchor: null
  };

  componentDidMount () {
    window.addEventListener('mousemove', this.mouseMove);
    window.addEventListener('mouseup', this.mouseUp);
  }

  componentWillUnmount () {
    window.removeEventListener('mousemove', this.mouseMove);
    window.removeEventListener('mouseup', this.mouseUp);
  }

  mouseMove = (e) => {
    if (this.state.currentAnchor && this.resizablePanel) {
      if (e.stopPropagation) {
        e.stopPropagation();
      }
      if (e.preventDefault) {
        e.preventDefault();
      }
      e.cancelBubble=true;
      e.returnValue=false;
      const {currentAnchor} = this.state;
      const dx = (e.screenX - currentAnchor.x) * resizeFactorForAnchor[currentAnchor.anchor].dx;
      const dy = (e.screenY - currentAnchor.y) * resizeFactorForAnchor[currentAnchor.anchor].dy;
      const width = currentAnchor.width + dx;
      const height = currentAnchor.height + dy;
      if (dx !== 0) {
        this.resizablePanel.style.width = `${width}px`;
      }
      if (dy !== 0) {
        this.resizablePanel.style.height = `${height}px`;
      }
      this.setState({currentAnchor});
    }
  };

  mouseUp = () => {
    if (this.state.currentAnchor) {
      this.setState({currentAnchor: null});
    }
  };

  resizablePanel;

  initializePanel = (div) => {
    this.resizablePanel = div;
  };

  onResizeStart = (anchor) => (e) => {
    if (e.button !== 0) {
      return;
    }
    if (e.stopPropagation) {
      e.stopPropagation();
    }
    if (e.preventDefault) {
      e.preventDefault();
    }
    if (!this.resizablePanel) {
      return;
    }
    this.setState({
      currentAnchor: {
        anchor,
        x: e.screenX,
        y: e.screenY,
        width: this.resizablePanel.clientWidth,
        height: this.resizablePanel.clientHeight
      }
    });
  };

  renderAnchor = (anchor, key) => {
    const classes = [styles.anchor, ...anchor.split('-').map(cl => styles[cl])];
    return (
      <div
        onMouseDown={this.onResizeStart(anchor)}
        key={key}
        className={classes.join(' ')}>
        {'\u00A0'}
      </div>
    );
  };

  render () {
    return (
      <div
        ref={this.initializePanel}
        className={this.props.className ? `${styles.panel} ${this.props.className}` : styles.panel}>
        {this.props.children}
        {(this.props.resizeAnchors || []).sort(sortAnchors).map(this.renderAnchor)}
      </div>
    );
  }
}
