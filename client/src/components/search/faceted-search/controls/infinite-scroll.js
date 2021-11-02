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

const DEFAULT_PAGE_SIZE = 50;
const SCROLL_BLOCK_DELAY = 100;

const MORE_PLACEHOLDER_HEIGHT = 38;

class InfiniteScroll extends React.Component {
  state = {
    rowKeyFn: (o, i) => `${i}`,
    scrollerHeight: 0,
    itemsOnPage: DEFAULT_PAGE_SIZE
  };

  scroller;
  currentDocumentId;
  blockUpdateTrigger = true;

  componentDidMount () {
    window.addEventListener('resize', this.changePageSize);
    this.updateState();
    const {onInitialized} = this.props;
    if (onInitialized) {
      onInitialized(this);
    }
    this.scrollToCurrent();
  }

  componentWillUnmount () {
    window.removeEventListener('resize', this.changePageSize);
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    let state = {};
    if (prevProps.rowKey !== this.props.rowKey) {
      state = this.updateRowKey();
      // eslint-disable-next-line
      this.setState(state);
    } else if (prevProps.elements !== this.props.elements) {
      this.scrollToCurrent();
    }
  }

  shouldComponentUpdate (nextProps, nextState, nextContext) {
    const simpleCheck = o => this.props[o] !== nextProps[o];
    return simpleCheck('className') ||
      simpleCheck('error') ||
      simpleCheck('pageSize') ||
      simpleCheck('pending') ||
      simpleCheck('rowHeight') ||
      simpleCheck('rowMargin') ||
      simpleCheck('rowKey') ||
      simpleCheck('headerRenderer') ||
      simpleCheck('rowRenderer') ||
      simpleCheck('style') ||
      simpleCheck('elements');
  }

  scrollToCurrent = () => {
    this.blockUpdateTrigger = true;
    if (this.scroller) {
      const scrollTo = [...this.scroller.getElementsByClassName('infinite-scroll-element')]
        .find(e => e.dataset &&
          e.dataset.hasOwnProperty('documentId') &&
          e.dataset['documentId'] === this.currentDocumentId
        );
      if (scrollTo) {
        this.scroller.scrollTop = scrollTo.offsetTop;
      }
    }
    setTimeout(() => {
      this.blockUpdateTrigger = false;
    }, SCROLL_BLOCK_DELAY);
  };

  updateState = () => {
    const state = this.updateRowKey(false);
    this.setState(state);
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

  onScroll = (e) => {
    if (this.blockUpdateTrigger) {
      return;
    }
    const obj = e.target;
    if (obj) {
      const {
        hasElementsAfter,
        hasElementsBefore,
        elements,
        pageSize
      } = this.props;
      if (elements && elements.length > 0) {
        const page = Math.floor(pageSize / 2.0);
        const correctElementIndex = index => Math.max(0, Math.min(elements.length - 1, index));
        const getClosestDocumentId = () => {
          const info = [...obj.getElementsByClassName('infinite-scroll-element')]
            .filter(e => e.dataset && e.dataset.hasOwnProperty('documentId'))
            .map(e => ({
              id: e.dataset['documentId'],
              position: e.offsetTop,
              delta: Math.abs(e.offsetTop - obj.scrollTop)
            }))
            .sort((a, b) => a.delta - b.delta);
          return info.length ? info[0].id : undefined;
        };
        this.currentDocumentId = getClosestDocumentId(obj.scrollTop);
        if (hasElementsBefore && obj.scrollTop <= MORE_PLACEHOLDER_HEIGHT) {
          this.reportScroll(elements[correctElementIndex(page)], false);
        } else if (
          hasElementsAfter &&
          (obj.scrollTop + obj.clientHeight + MORE_PLACEHOLDER_HEIGHT) >= obj.scrollHeight
        ) {
          this.reportScroll(elements[correctElementIndex(elements.length - page)], true);
        }
      }
    }
  };

  initializeScroller = (o) => {
    if (o !== this.scroller) {
      this.scroller = o;
      this.changePageSize();
      this.scrollToCurrent();
    }
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
      this.setState({
        scrollerHeight,
        itemsOnPage
      }, () => {
        this.reportPageChange();
      });
    }
  }

  reportPageChange = () => {
    const {
      onPageSizeChanged,
      pageSize
    } = this.props;
    const {
      itemsOnPage
    } = this.state;
    if (onPageSizeChanged && pageSize < (itemsOnPage * 3)) {
      onPageSizeChanged((itemsOnPage * 3) || DEFAULT_PAGE_SIZE);
    }
  };

  reportScroll = (document, forward = true) => {
    const {onScrollToEnd} = this.props;
    if (onScrollToEnd) {
      onScrollToEnd(document, forward);
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
      hasElementsAfter,
      hasElementsBefore,
      rowMargin,
      headerRenderer,
      rowRenderer
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
          {headerRenderer && headerRenderer()}
          {
            hasElementsBefore && (
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
                  height: MORE_PLACEHOLDER_HEIGHT,
                  marginBottom: rowMargin
                }}
              >
                {error || (<Icon type="loading" />)}
              </div>
            )
          }
          {
            rowRenderer && (elements || []).map((element, index) => (
              <div
                className="infinite-scroll-element"
                key={rowKeyFn(element, index)}
                data-document-id={rowKeyFn(element)}
                style={{
                  marginBottom: rowMargin
                }}
              >
                {rowRenderer(element, index)}
              </div>
            ))
          }
          {
            hasElementsAfter && (
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
                  height: MORE_PLACEHOLDER_HEIGHT,
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
  elements: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  error: PropTypes.string,
  hasElementsAfter: PropTypes.bool,
  hasElementsBefore: PropTypes.bool,
  onInitialized: PropTypes.func,
  onPageSizeChanged: PropTypes.func,
  onScrollToEnd: PropTypes.func,
  pageSize: PropTypes.number,
  pending: PropTypes.bool,
  rowHeight: PropTypes.number,
  rowKey: PropTypes.oneOfType([PropTypes.string, PropTypes.func]),
  rowMargin: PropTypes.number,
  headerRenderer: PropTypes.func,
  rowRenderer: PropTypes.func,
  style: PropTypes.object
};

InfiniteScroll.defaultProps = {
  rowHeight: 38,
  rowMargin: 3,
  rowKey: 'elasticId'
};

export default InfiniteScroll;
export {DEFAULT_PAGE_SIZE};
