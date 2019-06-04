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
import {computed} from 'mobx';
import {Row, Icon} from 'antd';
import localization from '../../../utils/localization';

const RESIZER_SIZE = 6;
const MINIMUM_CONTENT_SIZE = 50;
const CONTENT_PADDING = 0;
const MAX_SIZE_ITERATIONS = 10;

export const CONTENT_PANEL_KEY = 'content';
export const ISSUES_PANEL_KEY = 'issues';
export const METADATA_PANEL_KEY = 'metadata';

const filterRealChild = child => !!child;

@observer
export class SplitPanel extends React.Component {

  static propTypes = {
    onPanelClose: PropTypes.func,
    onPanelResize: PropTypes.func,
    onPanelResizeDelay: PropTypes.number,
    resizerSize: PropTypes.number,
    contentPadding: PropTypes.number,
    orientation: PropTypes.oneOf(['horizontal', 'vertical']),
    style: PropTypes.object,
    contentInfo: PropTypes.arrayOf(PropTypes.shape({
      key: PropTypes.string,
      title: PropTypes.string,
      closable: PropTypes.bool,
      containerStyle: PropTypes.object,
      containerClassName: PropTypes.string,
      size: PropTypes.shape({
        keepPreviousSize: PropTypes.bool,
        priority: PropTypes.number,
        pxMinimum: PropTypes.number,
        percentMinimum: PropTypes.number,
        pxMaximum: PropTypes.number,
        percentMaximum: PropTypes.number,
        pxDefault: PropTypes.number,
        percentDefault: PropTypes.number
      })
    }))
  };
  static defaultProps = {
    onPanelResizeDelay: 250
  };
  state = {
    sizes: {},
    container: undefined,
    activeResizer: null,
    activeResizerInfo: null
  };
  getChildKey = (childIndex, children) => {
    children = children || (this.props.children || []).filter(filterRealChild);
    const child = children[childIndex];
    return child.key || `child_${childIndex}`;
  };

  getContentInfo = (key, contentInfo) => {
    contentInfo = contentInfo || this.props.contentInfo;
    if (contentInfo) {
      const [info] = contentInfo.filter(i => i.key === key);
      if (info) {
        return info;
      }
    }
    return {key};
  };
  getResizerIndentifier = (resizerIndex) => {
    return `resizer_${resizerIndex};`;
  };
  renderResizer = (resizerIndex) => {
    const onMouseDown = (e) => {
      if (this.state.container) {
        const coordinate = this.isVertical
          ? e.nativeEvent.pageY - this.state.container.offsetTop
          : e.nativeEvent.pageX - this.state.container.offsetLeft;
        this.setState({
          activeResizer: resizerIndex,
          activeResizerInfo: {
            coordinate
          }
        });
      }
    };
    const style = {
      cursor: this.isVertical ? 'row-resize' : 'col-resize',
      padding: this.isVertical ? '2px 0px' : '0px 2px',
      width: this.isVertical ? undefined : this.resizerSize,
      height: this.isVertical ? this.resizerSize : undefined
    };
    return (
      <div
        id={this.getResizerIndentifier(resizerIndex)}
        onMouseDown={onMouseDown}
        key={this.getResizerIndentifier(resizerIndex)}
        style={style}>
        <div
          style={{
            backgroundColor: '#eee',
            height: '100%',
            width: '100%'
          }}>
          {'\u00A0'}
        </div>
      </div>
    );
  };
  getChildMinimumSizePx = (childIndex) => {
    const info = this.getContentInfo(this.getChildKey(childIndex));
    if (info && info.size) {
      if (info.size.pxMinimum) {
        return info.size.pxMinimum;
      } else if (info.size.percentMinimum) {
        return this.toPXSize(info.size.percentMinimum / 100.0);
      }
    }
    return MINIMUM_CONTENT_SIZE;
  };
  getChildMaximumSizePx = (childIndex) => {
    const info = this.getContentInfo(this.getChildKey(childIndex));
    if (info && info.size) {
      if (info.size.pxMaximum) {
        return info.size.pxMaximum;
      } else if (info.size.percentMaximum) {
        return this.toPXSize(info.size.percentMaximum / 100.0);
      }
    }
    return this.totalSize;
  };
  getChildSize = (childIndex) => {
    const childrenCount = (this.props.children || []).filter(filterRealChild).length;
    const key = this.getChildKey(childIndex);
    const sizes = this.state.sizes;
    let size;
    if (sizes && sizes[key]) {
      size = sizes[key];
    } else {
      size = this.toPercentSize((this.totalSize - (childrenCount - 1) * this.resizerSize) /
        childrenCount);
    }
    return size;
  };
  setChildSize = (childIndex, size, sizes, push = false) => {
    const key = this.getChildKey(childIndex);
    sizes = sizes || this.state.sizes;
    if (!sizes) {
      sizes = {};
    }
    sizes[key] = size;
    if (push) {
      this.setState({
        sizes: sizes
      });
    }
    return sizes;
  };
  renderChild = (childIndex) => {
    const content = (this.props.children || []).filter(filterRealChild)[childIndex];
    const key = this.getChildKey(childIndex);
    const size = this.getChildSize(childIndex);
    let style = {
      overflow: 'auto',
      padding: this.contentPadding
    };
    if (this.isVertical) {
      style.height = `${size * 100}%`;
    } else {
      style.width = `${size * 100}%`;
    }
    const info = this.getContentInfo(key);
    if (info && info.containerStyle !== undefined) {
      style = Object.assign(style, info.containerStyle);
    }
    let renderHeader = false;
    if (info && (info.title || info.closable)) {
      renderHeader = true;
    }
    return (
      <div
        className={info ? info.containerClassName : undefined}
        key={`child_${childIndex}`}
        style={style}>
        {
          renderHeader &&
          <Row
            type="flex"
            justify="space-between"
            align="middle"
            style={{
              backgroundColor: '#efefef',
              borderBottom: '1px solid #ddd',
              borderTop: '1px solid #ddd',
              padding: '0px 5px'
            }}>
            <span>{info.title || ''}</span>
            {
              info.closable &&
              <Icon
                type="close"
                onClick={() => this.props.onPanelClose && info && this.props.onPanelClose(info.key)}
                style={{cursor: 'pointer'}} />
            }
          </Row>
        }
        {content}
      </div>
    );
  };
  renderSplitPaneContent = () => {
    const children = [];
    const length = (this.props.children || []).filter(filterRealChild).length;
    for (let i = 0; i < length; i++) {
      children.push(this.renderChild(i));
      if (i < length - 1) {
        children.push(this.renderResizer(i));
      }
    }
    return children;
  };
  initializeSplitPane = (div) => {
    this.setState({
      container: div
    }, () => {
      if (this.state.container) {
        this.resetWidthsState((this.props.children || []).filter(filterRealChild), this.props.contentInfo);
      }
    });
  };
  toPercentSize = (px) => {
    if (!px) {
      return 0;
    }
    return px / this.totalSize;
  };
  // percent [0..1]
  toPXSize = (percent) => {
    if (!percent) {
      return 0;
    }
    return this.totalSize * percent;
  };
  resetWidthsState = (children, infos) => {
    const sizes = {};
    const getChildSizePriority = (key) => {
      const info = this.getContentInfo(key, infos);
      if (info && info.size && info.size.priority) {
        return info.size.priority;
      }
      return 0;
    };
    const sortedChildren = children
      .filter(filterRealChild)
      .sort((childA, childB) => {
        const priorityA = getChildSizePriority(childA.key);
        const priorityB = getChildSizePriority(childB.key);
        if (priorityA > priorityB) {
          return -1;
        } else if (priorityA < priorityB) {
          return 1;
        }
        return 0;
      });
    const maxSizes = {};
    const minSizes = {};
    const getChildKey = (i, array) => {
      const index = (children || []).filter(filterRealChild).indexOf(array[i]);
      return this.getChildKey(index, (children || []).filter(filterRealChild));
    };
    for (let i = 0; i < sortedChildren.length; i++) {
      const key = getChildKey(i, sortedChildren);
      const info = this.getContentInfo(key, infos);
      if (info && info.size && info.size.keepPreviousSize) {
        let width = this.state.sizes[key];
        if (width !== undefined) {
          sizes[key] = this.state.sizes[key];
          const min = this.toPercentSize(info.size.pxMinimum) ||
            (info.size.percentMinimum ? info.size.percentMinimum / 100 : 0);
          const max = this.toPercentSize(info.size.pxMaximum) ||
            (info.size.percentMaximum ? info.size.percentMaximum / 100 : 1);
          width = Math.max(min, Math.min(max, width));
          maxSizes[key] = width === max;
          minSizes[key] = width === min;
        }
      }
    }
    const setSizes = (size, iteration) => {
      if (iteration === MAX_SIZE_ITERATIONS || size === 0) {
        return;
      }
      let totalWidth = size;
      const notProcessedChildren = size > 0
        ? sortedChildren.filter(({key}) => !maxSizes[key])
        : sortedChildren.filter(({key}) => !minSizes[key]);
      for (let i = 0; i < notProcessedChildren.length; i++) {
        const key = getChildKey(i, notProcessedChildren);
        const info = this.getContentInfo(key, infos);
        let width = sizes[key];
        if (!width) {
          if (info && info.size && info.size.pxDefault) {
            width = this.toPercentSize(info.size.pxDefault);
          } else if (info && info.size && info.size.percentDefault) {
            width = info.size.percentDefault / 100;
          } else {
            width = (totalWidth / (notProcessedChildren.length - i));
          }
          if (i === notProcessedChildren.length - 1) {
            width = totalWidth;
          }
        } else if (iteration > 0) {
          width += (totalWidth / (notProcessedChildren.length - i));
        }
        if (info && info.size) {
          const min = this.toPercentSize(info.size.pxMinimum) ||
            (info.size.percentMinimum ? info.size.percentMinimum / 100 : 0);
          const max = this.toPercentSize(info.size.pxMaximum) ||
            (info.size.percentMaximum ? info.size.percentMaximum / 100 : 1);
          width = Math.max(min, Math.min(max, width));
          maxSizes[key] = width === max;
          minSizes[key] = width === min;
        }
        let addedWidth = width;
        if (iteration > 0 && sizes[key]) {
          const delta = width - sizes[key];
          sizes[key] = width;
          addedWidth = delta;
        } else {
          sizes[key] = width;
        }
        totalWidth -= addedWidth;
      }
      setSizes(totalWidth, iteration + 1);
    };
    setSizes(1, 0);
    this.setState({
      activeResizer: null,
      activeResizerInfo: null,
      sizes
    });
  };
  panelResizeTimeout;
  reportPanelResize = () => {
    if (!this.props.onPanelResize) {
      return;
    }
    if (this.panelResizeTimeout) {
      clearTimeout(this.panelResizeTimeout);
    }
    this.panelResizeTimeout = setTimeout(() => {
      this.props.onPanelResize && this.props.onPanelResize(this);
      this.panelResizeTimeout = undefined;
    }, this.props.onPanelResizeDelay);
  };
  updateState = (e) => {
    if (this.state.activeResizer !== null && this.state.activeResizerInfo) {
      if (e.stopPropagation) {
        e.stopPropagation();
      }
      if (e.preventDefault) {
        e.preventDefault();
      }
      e.cancelBubble = true;
      e.returnValue = false;
      const coordinate = this.isVertical
        ? e.pageY - this.state.container.offsetTop
        : e.pageX - this.state.container.offsetLeft;
      const delta = coordinate - this.state.activeResizerInfo.coordinate;
      let leftChildSizePx = this.toPXSize(this.getChildSize(this.state.activeResizer)) +
        delta;
      let rightChildSizePx = this.toPXSize(this.getChildSize(this.state.activeResizer + 1)) -
        delta;
      const leftChildMinimumSizePx = this.getChildMinimumSizePx(this.state.activeResizer);
      const leftChildMaximumSizePx = this.getChildMaximumSizePx(this.state.activeResizer);
      const rightChildMinimumSizePx = this.getChildMinimumSizePx(this.state.activeResizer + 1);
      const rightChildMaximumSizePx = this.getChildMaximumSizePx(this.state.activeResizer + 1);
      if (leftChildSizePx >= leftChildMinimumSizePx &&
        leftChildSizePx <= leftChildMaximumSizePx &&
        rightChildSizePx >= rightChildMinimumSizePx &&
        rightChildSizePx <= rightChildMaximumSizePx) {
        const leftChildWidth = this.toPercentSize(leftChildSizePx);
        const rightChildWidth = this.toPercentSize(rightChildSizePx);
        const info = this.setChildSize(this.state.activeResizer, leftChildWidth);
        this.setChildSize(this.state.activeResizer + 1, rightChildWidth, info);
        this.setState({
          sizes: info,
          activeResizerInfo: {
            coordinate: coordinate
          }
        }, this.reportPanelResize);
      }
      return false;
    }
    return true;
  };
  onMouseMove = (e) => {
    return this.updateState(e);
  };
  onMouseUp = (e) => {
    this.updateState(e);
    if (this.state.activeResizer !== null && this.state.activeResizerInfo) {
      this.setState({
        activeResizer: null,
        activeResizerInfo: null
      });
    }
  };

  @computed
  get isVertical () {
    return this.props.orientation === 'vertical';
  }

  @computed
  get resizerSize () {
    return this.props.resizerSize || RESIZER_SIZE;
  }

  @computed
  get contentPadding () {
    return this.props.contentPadding || CONTENT_PADDING;
  }

  @computed
  get totalSize () {
    if (this.state.container) {
      return this.isVertical
        ? this.state.container.offsetHeight || this.state.container.clientHeight
        : this.state.container.offsetWidth || this.state.container.clientWidth;
    }
    return undefined;
  }

  render () {
    return (
      <div
        className="split-panel"
        ref={this.initializeSplitPane}
        style={Object.assign(
          {
            width: '100%',
            height: '100%',
            display: 'flex',
            flexDirection: this.isVertical ? 'column' : 'row'
          }, this.props.style || {})}>
        {
          this.totalSize ? this.renderSplitPaneContent() : undefined
        }
      </div>
    );
  }

  componentWillReceiveProps (nextProps) {
    const nextPropsChildrenLength = (nextProps.children || []).filter(filterRealChild).length;
    const currentPropsChildrenLength = (this.props.children || []).filter(filterRealChild).length;
    if (nextPropsChildrenLength !== currentPropsChildrenLength) {
      this.resetWidthsState((nextProps.children || []).filter(filterRealChild), nextProps.contentInfo);
    }
  }

  componentDidMount () {
    window.addEventListener('mousemove', this.onMouseMove);
    window.addEventListener('mouseup', this.onMouseUp);
  }

  componentWillUnmount () {
    window.removeEventListener('mousemove', this.onMouseMove);
    window.removeEventListener('mouseup', this.onMouseUp);
  }
}

@localization.localizedComponent
export class ContentIssuesMetadataPanel extends localization.LocalizedReactComponent {

  static propTypes = {
    onPanelClose: PropTypes.func,
    style: PropTypes.object,
  };

  render () {
    return (
      <SplitPanel
        style={this.props.style}
        onPanelClose={this.props.onPanelClose}
        contentInfo={[
          {
            key: CONTENT_PANEL_KEY,
            size: {
              priority: 0,
              percentMinimum: 33,
              percentDefault: 75
            }
          },
          {
            key: ISSUES_PANEL_KEY,
            title: `${this.localizedString('Issue')}s`,
            closable: true,
            containerStyle: {
              display: 'flex',
              flexDirection: 'column'
            },
            size: {
              keepPreviousSize: true,
              priority: 1,
              percentDefault: 25,
              pxMinimum: 200
            }
          },
          {
            key: METADATA_PANEL_KEY,
            title: 'Attributes',
            closable: true,
            containerStyle: {
              display: 'flex',
              flexDirection: 'column'
            },
            size: {
              keepPreviousSize: true,
              priority: 2,
              percentDefault: 25,
              pxMinimum: 200
            }
          }
        ]}>
        {this.props.children}
      </SplitPanel>
    );
  }
}

export class ContentMetadataPanel extends React.Component {

  static propTypes = {
    onPanelClose: PropTypes.func,
    contentContainerStyle: PropTypes.object,
    orientation: PropTypes.oneOf(['horizontal', 'vertical']),
    style: PropTypes.object
  };

  getContentContainerStyle = () => {
    const defaultStyle = {
      display: 'flex',
      flexDirection: 'column'
    };
    if (this.props.contentContainerStyle) {
      return Object.assign({}, defaultStyle, this.props.contentContainerStyle);
    } else {
      return defaultStyle;
    }
  };

  render () {
    return (
      <SplitPanel
        style={this.props.style}
        orientation={this.props.orientation}
        onPanelClose={this.props.onPanelClose}
        contentInfo={[
          {
            key: CONTENT_PANEL_KEY,
            containerStyle: this.getContentContainerStyle(),
            size: {
              priority: 0,
              percentMinimum: 33,
              percentDefault: 75
            }
          },
          {
            key: METADATA_PANEL_KEY,
            title: 'Attributes',
            closable: !!this.props.onPanelClose,
            containerStyle: {
              display: 'flex',
              flexDirection: 'column'
            },
            size: {
              keepPreviousSize: true,
              priority: 2,
              percentDefault: 25,
              pxMinimum: 200
            }
          }
        ]}>
        {this.props.children}
      </SplitPanel>
    );
  }
}
