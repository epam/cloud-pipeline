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
import {Icon} from 'antd';
import classNames from 'classnames';
import styles from './controls.css';

class InfiniteScroll extends React.Component {
  state = {
    offset: 0,
    rowKeyFn: (o, i) => `${i}`,
    scrollerHeight: 0,
    itemsToDisplay: 0,
    itemsOnPage: 0
  };

  scroller;

  componentDidMount () {
    this.updateState();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    let state = {};
    if (prevProps.rowKey !== this.props.rowKey) {
      state = {...this.updateRowKey()};
    }
    const offsetUpdated = (
      this.props.offset !== this.state.offset &&
      prevProps.offset !== this.props.offset
    ) ||
      prevProps.dataOffset !== this.props.dataOffset;
    if (offsetUpdated) {
      state = {...state, ...this.updateOffset()};
    }
    if (Object.keys(state).length > 0) {
      const offsetWasNotInRange = prevProps.elements.length > 0 &&
        (
          prevState.offset < prevProps.dataOffset ||
          prevState.offset >= prevProps.dataOffset + (prevProps.elements || []).length
        );
      // eslint-disable-next-line
      this.setState(state, () => {
        offsetUpdated && this.scrollToCurrentOffset(offsetWasNotInRange);
      });
    }
  }

  updateState = () => {
    const state = {
      ...this.updateRowKey(false),
      ...this.updateOffset(false)
    };
    this.setState(state, () => this.scrollToCurrentOffset(true));
  };

  updateRowKey = (update = true) => {
    const {rowKey} = this.props;
    const state = {};
    let changed;
    if (typeof rowKey === 'function') {
      state.rowKeyFn = rowKey;
    } else if (typeof rowKey === 'string') {
      state.rowKeyFn = o => o[rowKey];
    }
    if (changed && update) {
      this.setState(state);
    }
    return state;
  };

  updateOffset = (update = true) => {
    const {offset: propsOffset} = this.props;
    const state = {
      offset: propsOffset
    };
    if (update) {
      this.setState(state);
    }
    return state;
  };

  scrollToCurrentOffset = (offsetWasNotInRange) => {
    if (this.scroller) {
      const {offset, itemsOnPage} = this.state;
      const {
        dataOffset,
        elements = [],
        rowHeight,
        rowMargin
      } = this.props;
      const offsetIsNotInRange = elements.length > 0 &&
        (
          offset < dataOffset ||
          offset >= dataOffset + (elements || []).length
        );
      if (offsetIsNotInRange) {
        return;
      }
      const extra = (dataOffset > 0 ? 1 : 0);
      const delta = offsetWasNotInRange
        ? 0
        : (this.scroller.scrollTop || 0) % (rowHeight + rowMargin);
      this.scroller.scrollTop = Math.max(
        0,
        Math.min(
          (offset + extra - dataOffset) * (rowHeight + rowMargin) + delta,
          (elements.length + extra - itemsOnPage) * (rowHeight + rowMargin)
        )
      );
      this.forceUpdate();
    }
  };

  onScroll = (e) => {
    const obj = e.target;
    if (obj) {
      const {dataOffset, rowHeight: height, rowMargin} = this.props;
      const {offset: currentOffset} = this.state;
      const rowHeight = height + rowMargin;
      const y = obj.scrollTop - (dataOffset > 0 ? rowHeight : 0);
      const offset = dataOffset + Math.floor(y / rowHeight);
      if (offset !== currentOffset) {
        this.setState({offset}, () => {
          this.reportPageChange();
        });
      }
    }
  };

  initializeScroller = (o) => {
    this.scroller = o;
    this.changePageSize();
  }

  changePageSize = () => {
    if (this.scroller) {
      const {
        rowHeight,
        rowMargin
      } = this.props;
      const scrollerHeight = this.scroller.clientHeight;
      const itemHeightWithMargin = rowHeight + rowMargin;
      const itemsOnPage = Math.floor(scrollerHeight / itemHeightWithMargin);
      const itemsToDisplay = itemsOnPage * 3;
      this.setState({
        scrollerHeight,
        itemsToDisplay,
        itemsOnPage
      }, () => {
        this.reportPageChange();
      });
    }
  }

  reportPageChange = (delayed = false) => {
    if (this.pageChangeDelay) {
      clearTimeout(this.pageChangeDelay);
      this.pageChangeDelay = null;
    }
    if (delayed) {
      this.pageChangeDelay = setTimeout(
        () => this.reportPageChange(),
        100
      );
    } else {
      const {
        offset,
        itemsOnPage
      } = this.state;
      const {
        onOffsetChanged
      } = this.props;
      if (onOffsetChanged) {
        onOffsetChanged(offset, itemsOnPage);
      }
    }
  };

  getRowKey = (item, index) => {
    const {rowKeyFn} = this.state;
    if (rowKeyFn) {
      return rowKeyFn(item, index);
    }
    return `${index}`;
  };

  render () {
    const {
      className,
      elements,
      error,
      style,
      dataOffset,
      rowHeight,
      rowMargin,
      headerRenderer,
      rowRenderer,
      total
    } = this.props;
    const {
      rowKeyFn
    } = this.state;
    return (
      <div
        className={
          classNames(
            styles.infiniteScroll,
            className
          )
        }
        style={style}
        ref={this.initializeScroller}
        onScroll={this.onScroll}
      >
        <div
          className={styles.scrollContainer}
        >
          {
            dataOffset > 0 && (
              <div
                className={
                  classNames(
                    styles.more,
                    {
                      [styles.error]: !!error
                    }
                  )
                }
                style={{
                  height: rowHeight,
                  marginBottom: rowMargin
                }}
              >
                {error || (<Icon type="loading" />)}
              </div>
            )
          }
          {headerRenderer && headerRenderer()}
          {
            rowRenderer && (elements || []).map((element, index) => (
              <div
                key={rowKeyFn(element, index)}
                style={{
                  height: rowHeight,
                  marginBottom: rowMargin
                }}
              >
                {rowRenderer(element, index)}
              </div>
            ))
          }
          {
            dataOffset + (elements || []).length < total && (
              <div
                className={
                  classNames(
                    styles.more,
                    {
                      [styles.error]: !!error
                    }
                  )
                }
                style={{
                  height: rowHeight,
                  marginBottom: rowMargin
                }}
              >
                {error || (<Icon type="loading" />)}
              </div>
            )
          }
        </div>
      </div>
    );
  }
}

InfiniteScroll.propTypes = {
  className: PropTypes.string,
  dataOffset: PropTypes.number,
  elements: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  error: PropTypes.string,
  offset: PropTypes.number,
  onOffsetChanged: PropTypes.func,
  pageSize: PropTypes.number,
  pending: PropTypes.bool,
  rowHeight: PropTypes.number,
  rowKey: PropTypes.oneOfType([PropTypes.string, PropTypes.func]),
  rowMargin: PropTypes.number,
  headerRenderer: PropTypes.func,
  rowRenderer: PropTypes.func,
  style: PropTypes.object,
  total: PropTypes.number
};

InfiniteScroll.defaultProps = {
  rowHeight: 38,
  rowMargin: 3,
  rowKey: 'elasticId'
};

export default InfiniteScroll;
