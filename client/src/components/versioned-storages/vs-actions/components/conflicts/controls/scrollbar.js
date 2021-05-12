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
import classNames from 'classnames';
import styles from './scrollbar.css';

class Scrollbar extends React.PureComponent {
  static size = 8;
  state = {
    moving: undefined
  };

  attachToScroll;
  bar;
  container;

  componentDidMount () {
    const {onInitialized} = this.props;
    onInitialized && onInitialized(this);
    window.addEventListener('mousemove', this.onMouseMove);
    window.addEventListener('mouseup', this.onMouseUp);
  }

  componentWillUnmount () {
    window.removeEventListener('mousemove', this.onMouseMove);
    window.removeEventListener('mouseup', this.onMouseUp);
  }

  onMouseDown = (e) => {
    if (this.attachToScroll && this.container) {
      const isVertical = this.props.direction === 'vertical';
      const {
        scrollTop,
        scrollLeft,
        clientWidth,
        clientHeight,
        scrollWidth,
        scrollHeight
      } = this.attachToScroll;
      const {
        clientWidth: containerWidth,
        clientHeight: containerHeight
      } = this.container;
      const totalWidth = (isVertical ? scrollHeight : scrollWidth) || 0;
      const screenWidth = (isVertical ? clientHeight : clientWidth) || 0;
      const visible = screenWidth < totalWidth;
      if (visible) {
        const containerSize = (isVertical ? containerHeight : containerWidth) || 0;
        const ratio = containerSize / totalWidth;
        this.setState({
          moving: {
            start: isVertical ? e.screenY : e.screenX,
            scroll: isVertical
              ? scrollTop
              : scrollLeft,
            ratio
          }
        });
      }
    }
  };

  onMouseMove = (e) => {
    const isVertical = this.props.direction === 'vertical';
    if (this.state.moving && this.attachToScroll) {
      const {start, scroll, ratio} = this.state.moving;
      const delta = (isVertical ? e.screenY : e.screenX) - start;
      const newScroll = scroll + delta / ratio;
      if (isVertical) {
        this.attachToScroll.scrollTop = newScroll;
      } else {
        this.attachToScroll.scrollLeft = newScroll;
      }
      e.stopPropagation();
      e.preventDefault();
    }
  };

  onMouseUp = (e) => {
    if (this.state.moving) {
      this.onMouseMove(e);
      this.setState({
        moving: undefined
      });
    }
  };

  attach = (scroll) => {
    this.detach();
    if (scroll) {
      this.attachToScroll = scroll;
      this.attachToScroll.addEventListener('scroll', this.onScroll);
      this.renderBar();
    }
  };

  detach = () => {
    if (this.attachToScroll) {
      this.attachToScroll.removeEventListener('scroll', this.onScroll);
      this.attachToScroll = null;
    }
  };

  onScroll = (e) => {
    const {target} = e;
    this.renderBar(target);
  };

  initializeContainer = (container) => {
    this.container = container;
    this.renderBar();
  };

  initializeBar = (bar) => {
    this.bar = bar;
    this.renderBar();
  };

  renderBar = (target = undefined) => {
    if (this.bar && this.container && (this.attachToScroll || target)) {
      const {
        scrollTop,
        scrollLeft,
        clientWidth,
        clientHeight,
        scrollWidth,
        scrollHeight
      } = target || this.attachToScroll;
      const {
        clientWidth: containerWidth,
        clientHeight: containerHeight
      } = this.container;
      const isVertical = this.props.direction === 'vertical';
      const start = (isVertical ? scrollTop : scrollLeft) || 0;
      const totalWidth = (isVertical ? scrollHeight : scrollWidth) || 0;
      const screenWidth = (isVertical ? clientHeight : clientWidth) || 0;
      const visible = screenWidth < totalWidth;
      this.bar.style.display = visible ? 'block' : 'none';
      if (visible) {
        const containerSize = (isVertical ? containerHeight : containerWidth) || 0;
        const ratio = containerSize / totalWidth;
        const barWidth = screenWidth * ratio;
        const startRelative = start * ratio;
        if (isVertical) {
          this.bar.style.top = `${startRelative}px`;
          this.bar.style.height = `${barWidth}px`;
        } else {
          this.bar.style.left = `${startRelative}px`;
          this.bar.style.width = `${barWidth}px`;
        }
      }
    }
  };

  render () {
    const {
      className,
      direction,
      width,
      height,
      style
    } = this.props;
    const {moving} = this.state;
    return (
      <div
        ref={this.initializeContainer}
        className={
          classNames(
            styles.scrollbar,
            'cp-conflict-scroller',
            className
          )
        }
        style={{
          ...style,
          width: direction === 'horizontal' ? undefined : width,
          height: direction === 'horizontal' ? height : undefined
        }}
        onMouseDown={this.onMouseDown}
      >
        <div
          className={
            classNames(
              styles.bar,
              'bar',
              {
                hovered: !!moving,
                [styles.hovered]: !!moving
              }
            )
          }
          ref={this.initializeBar}
          style={{
            width: Scrollbar.size,
            height: Scrollbar.size
          }}
        >
          {'\u00A0'}
        </div>
      </div>
    );
  }
}

Scrollbar.propTypes = {
  className: PropTypes.string,
  direction: PropTypes.oneOf(['vertical', 'horizontal']),
  width: PropTypes.number,
  height: PropTypes.number,
  onInitialized: PropTypes.func,
  style: PropTypes.object
};

export default Scrollbar;
