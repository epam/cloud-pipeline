/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import {isObservableArray} from 'mobx';
import styles from './split-panel.css';

const DIVIDER_SIZE = 7;

function asArray (object) {
  if (!object) {
    return [];
  }
  if (Array.isArray(object) || isObservableArray(object)) {
    return object;
  }
  return [object];
}

function SplitPanelChild ({className, children, style}) {
  return (
    <div
      className={className}
      style={style}
    >
      {children}
    </div>
  );
}

SplitPanelChild.propTypes = {
  className: PropTypes.string,
  defaultSize: PropTypes.number,
  style: PropTypes.object
};

function getPanelChildClassNames (vertical, ...classes) {
  return classNames(
    {
      [styles.vertical]: vertical,
      [styles.horizontal]: !vertical
    },
    ...classes
  );
}

function SplitPanelDivider ({panelKey, vertical, onMouseDown}) {
  return (
    <div
      className={getPanelChildClassNames(vertical, styles.divider)}
      data-panel-key={panelKey}
      style={
        vertical ? {height: DIVIDER_SIZE} : {width: DIVIDER_SIZE}
      }
      onMouseDown={onMouseDown}
    >
      <div
        className={
          classNames(
            styles.inner,
            'cp-divider',
            {
              'vertical': !vertical,
              'horizontal': vertical
            }
          )
        }
      >
        {'\u00A0'}
      </div>
    </div>
  );
}

SplitPanelDivider.propTypes = {
  panelKey: PropTypes.string,
  vertical: PropTypes.bool,
  onMouseDown: PropTypes.func
};

function getChildKey (child, childIndex) {
  const {
    key = `child-${childIndex}`
  } = child || {};
  return key;
}

function getGrid (vertical, children = [], sizes = {}) {
  const main = children
    .map((child, childIndex) => {
      const key = getChildKey(child, childIndex);
      let size = sizes[key];
      if (typeof size === 'undefined') {
        size = '1fr';
      } else if (!Number.isNaN(Number(size))) {
        size = `${size}px`;
      }
      return [
        `[${key}] ${size}`,
        `[${key}-divider] ${DIVIDER_SIZE}px`
      ];
    })
    .reduce((r, c) => ([...r, ...c]), [])
    .slice(0, -1)
    .join(' ');
  if (vertical) {
    return {
      gridTemplateRows: main
    };
  }
  return {
    gridTemplateColumns: main
  };
}

function getSizeConfig (key, sizes = {}, fractionSize = 0) {
  const aSize = sizes[key] || '1fr';
  if (/fr$/i.test(aSize)) {
    const fraction = Number(`${aSize}`.slice(0, -2));
    return {
      fraction: true,
      px: fraction * fractionSize,
      key
    };
  }
  if (!Number.isNaN(Number(aSize))) {
    return {
      fraction: false,
      px: Number(aSize),
      key
    };
  }
  return undefined;
}

function getFractionsInfo (options = {}) {
  const {
    vertical,
    container,
    children,
    sizes = {}
  } = options;
  const childrenArray = asArray(children);
  const sizeProperty = vertical ? 'clientHeight' : 'clientWidth';
  let fractionsTotal = container[sizeProperty] - (childrenArray.length - 1) * DIVIDER_SIZE;
  let fractions = 0;
  childrenArray.forEach((child, childIndex) => {
    const key = getChildKey(child, childIndex);
    const aSize = sizes[key] || '1fr';
    if (/fr$/i.test(aSize)) {
      const fraction = Number(`${aSize}`.slice(0, -2));
      fractions += fraction;
    } else if (!Number.isNaN(Number(aSize))) {
      fractionsTotal -= Number(aSize);
    }
  });
  return {
    fractionsTotal,
    fractions,
    fractionSize: fractions === 0 ? 0 : (fractionsTotal / fractions)
  };
}

class SplitPanel extends React.Component {
  static Pane = SplitPanelChild;
  state = {
    sizes: {},
    resize: undefined
  };

  componentDidMount () {
    window.addEventListener('mousemove', this.handleMouseMove);
    window.addEventListener('mouseup', this.handleMouseUp);
    this.updateChildrenSizes();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.children !== this.props.children) {
      this.updateChildrenSizes();
    }
  }

  componentWillUnmount () {
    window.removeEventListener('mousemove', this.handleMouseMove);
    window.removeEventListener('mouseup', this.handleMouseUp);
    this.container = undefined;
  }

  updateChildrenSizes = () => {
    const {
      children
    } = this.props;
    const {
      sizes: currentSizes = {}
    } = this.state;
    const newSizes = {...currentSizes};
    const childArray = asArray(children);
    childArray.forEach((child, childIndex) => {
      const key = getChildKey(child, childIndex);
      if (newSizes[key]) {
        return;
      }
      const {
        props = {}
      } = child;
      const {
        defaultSize
      } = props;
      if (defaultSize !== undefined && !Number.isNaN(Number(defaultSize))) {
        newSizes[key] = Number(defaultSize);
      }
    });
    this.setState({
      sizes: newSizes
    });
  };

  initializeContainer = (container) => {
    this.container = container;
  }

  handleMouseDown = (event) => {
    if (!event || !event.nativeEvent || !event.nativeEvent.target || !this.container) {
      return;
    }
    const target = event.nativeEvent.target;
    const panelKey = target.dataset.panelKey;
    const {
      children,
      vertical
    } = this.props;
    const childrenArray = asArray(children);
    const idx = childrenArray
      .findIndex((aChild, childIndex) => getChildKey(aChild, childIndex) === panelKey);
    if (idx === -1) {
      return;
    }
    const nextPanelKey = childrenArray[idx + 1]
      ? getChildKey(childrenArray[idx + 1], idx + 1)
      : undefined;
    if (!nextPanelKey) {
      return;
    }
    const {
      sizes = {}
    } = this.state;
    const {fractionSize} = getFractionsInfo({
      vertical,
      children,
      sizes,
      container: this.container
    });
    const left = getSizeConfig(panelKey, sizes, fractionSize);
    const right = getSizeConfig(nextPanelKey, sizes, fractionSize);
    if (!left || !right) {
      return;
    }
    this.setState({
      resize: {
        left,
        right,
        screen: vertical ? event.nativeEvent.screenY : event.nativeEvent.screenX
      }
    });
    event.preventDefault();
    event.stopPropagation();
  };

  handleMouseMove = (event) => {
    if (!event || !this.state.resize || !this.container) {
      return undefined;
    }
    event.preventDefault();
    event.stopPropagation();
    const {
      vertical,
      children
    } = this.props;
    const screen = vertical ? event.screenY : event.screenX;
    const {
      resize,
      sizes = {}
    } = this.state;
    const MIN_SIZE = 100;
    const delta = Math.max(
      -resize.left.px + MIN_SIZE,
      Math.min(
        resize.right.px - MIN_SIZE,
        screen - resize.screen
      )
    );
    const newLeftSize = resize.left.px + delta;
    const newRightSize = resize.right.px - delta;
    const newSizes = {
      ...sizes,
      [resize.left.key]: newLeftSize,
      [resize.right.key]: newRightSize
    };
    const grid = getGrid(vertical, children, newSizes);
    Object.entries(grid || {}).forEach(([cssKey, cssValue]) => {
      this.container.style[cssKey] = cssValue;
    });
    return {
      left: newLeftSize,
      right: newRightSize
    };
  };

  handleMouseUp = (event) => {
    const payload = this.handleMouseMove(event);
    const {
      resize,
      sizes = {}
    } = this.state;
    if (!payload || !resize) {
      this.setState({
        resize: undefined
      });
      return;
    }
    const {
      left,
      right
    } = resize;
    const {
      left: leftPX,
      right: rightPX
    } = payload;
    const newSizePx = {
      ...sizes,
      [left.key]: leftPX,
      [right.key]: rightPX
    };
    const {
      vertical,
      children
    } = this.props;
    const {
      fractionsTotal,
      fractionSize
    } = getFractionsInfo({
      vertical,
      children,
      sizes: newSizePx,
      container: this.container
    });
    let resultedFractionsTotal = fractionsTotal +
      (left.fraction ? leftPX : 0) +
      (right.fraction ? rightPX : 0);
    const config = asArray(children).map((child, childIndex) => {
      const key = getChildKey(child, childIndex);
      let {
        fraction,
        px
      } = getSizeConfig(key, newSizePx, fractionSize);
      if (key === left.key && left.fraction) {
        fraction = true;
      }
      if (key === right.key && right.fraction) {
        fraction = true;
      }
      if (fraction) {
        return {
          key,
          fraction: px / resultedFractionsTotal
        };
      }
      return {
        key,
        px
      };
    });
    const minFraction = Math.min(...config.filter(a => a.fraction).map(a => a.fraction), Infinity);
    const newSize = config
      .map(item => {
        if (item.fraction) {
          return {
            [item.key]: `${item.fraction / minFraction}fr`
          };
        }
        return {
          [item.key]: Number(item.px)
        };
      })
      .reduce((r, c) => ({...r, ...c}), {});
    this.setState({
      resize: undefined,
      sizes: newSize || this.state.sizes
    });
  };

  render () {
    const {
      className,
      style,
      children,
      vertical
    } = this.props;
    const {
      sizes = {}
    } = this.state;
    const childrenArray = asArray(children);
    return (
      <div
        className={
          classNames(
            className,
            styles.splitPanel,
            'cp-panel',
            'cp-panel-borderless'
          )
        }
        style={Object.assign(getGrid(vertical, childrenArray, sizes), style)}
        ref={this.initializeContainer}
      >
        {
          childrenArray
            .map((child, childIndex) => {
              const key = getChildKey(child, childIndex);
              const dividerKey = `${key}-divider`;
              return [
                <div
                  key={key}
                  data-panel={key}
                  className={getPanelChildClassNames(vertical, styles.panel)}
                  style={
                    vertical ? {gridRow: key} : {gridColumn: key}
                  }
                >
                  {child}
                </div>,
                <SplitPanelDivider
                  key={dividerKey}
                  panelKey={key}
                  onMouseDown={this.handleMouseDown}
                  vertical={vertical}
                  style={
                    vertical ? {gridRow: dividerKey} : {gridColumn: dividerKey}
                  }
                />
              ];
            })
            .reduce((r, c) => ([...r, ...c]), [])
            .slice(0, -1)
        }
      </div>
    );
  }
}

SplitPanel.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  vertical: PropTypes.bool
};

export default SplitPanel;
