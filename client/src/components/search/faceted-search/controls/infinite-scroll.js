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
const MORE_TRIGGER_HEIGHT = MORE_PLACEHOLDER_HEIGHT / 2.0;

class InfiniteScroll extends React.Component {
  state = {
    scrollerHeight: 0,
    itemsOnPage: DEFAULT_PAGE_SIZE
  };

  scroller;
  currentDocumentId;
  blockUpdateTrigger = true;

  componentDidMount () {
    window.addEventListener('resize', this.changePageSize);
    const {onInitialized, initialTopDocument} = this.props;
    if (onInitialized) {
      onInitialized(this);
    }
    this.currentDocumentId = initialTopDocument;
    this.scrollToCurrent();
  }

  componentWillUnmount () {
    window.removeEventListener('resize', this.changePageSize);
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.elements !== this.props.elements ||
      prevProps.resetPositionToken !== this.props.resetPositionToken
    ) {
      this.scrollToCurrent(prevProps.resetPositionToken !== this.props.resetPositionToken);
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
      simpleCheck('headerRenderer') ||
      simpleCheck('rowRenderer') ||
      simpleCheck('style') ||
      simpleCheck('elements') ||
      simpleCheck('resetPositionToken');
  }

  scrollToCurrent = (top = false) => {
    this.blockUpdateTrigger = true;
    if (this.scroller) {
      const {
        hasElementsBefore,
        headerHeight
      } = this.props;
      if (top) {
        this.scroller.scrollTop = hasElementsBefore
          ? MORE_PLACEHOLDER_HEIGHT
          : 0;
      } else {
        const scrollTo = [...this.scroller.getElementsByClassName('infinite-scroll-element')]
          .find(e => e.dataset &&
            e.dataset.hasOwnProperty('documentId') &&
            e.dataset['documentId'] === this.currentDocumentId
          );
        if (scrollTo) {
          this.scroller.scrollTop = scrollTo.offsetTop - headerHeight;
        }
      }
      setTimeout(() => {
        this.blockUpdateTrigger = false;
        this.onScroll({target: this.scroller});
      }, SCROLL_BLOCK_DELAY);
    } else {
      this.blockUpdateTrigger = false;
    }
  };

  onScroll = (e) => {
    const {pending, reportCurrentDocument} = this.props;
    if (this.blockUpdateTrigger || pending) {
      return;
    }
    const obj = e.target;
    if (obj) {
      const {
        hasElementsAfter,
        hasElementsBefore,
        elements,
        pageSize,
        headerHeight = 0
      } = this.props;
      if (elements && elements.length > 0) {
        const page = Math.floor(pageSize / 2.0);
        const correctElementIndex = index => Math.max(0, Math.min(elements.length - 1, index));
        const getClosestDocumentId = () => {
          const all = [...obj.getElementsByClassName('infinite-scroll-element')]
            .filter(e => e.dataset && e.dataset.hasOwnProperty('documentId'))
            .map(e => ({
              id: e.dataset['documentId'],
              delta: e.offsetTop - headerHeight + e.offsetHeight - obj.scrollTop
            }));
          const info = all
            .filter(o => o.delta >= 0)
            .sort((a, b) => a.delta - b.delta);
          const doc = info.length ? info[0] : info.pop();
          if (doc) {
            return doc.id;
          }
          return undefined;
        };
        this.currentDocumentId = getClosestDocumentId();
        if (typeof reportCurrentDocument === 'function') {
          reportCurrentDocument(this.currentDocumentId);
        }
        if (hasElementsBefore && obj.scrollTop <= MORE_TRIGGER_HEIGHT) {
          this.reportScroll(elements[correctElementIndex(page + 1)], false);
        } else if (
          hasElementsAfter &&
          obj.scrollTop >= (
            obj.scrollHeight - obj.clientHeight - MORE_TRIGGER_HEIGHT
          )
        ) {
          this.reportScroll(elements[correctElementIndex(elements.length - page - 1)], true);
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
      rowRenderer,
      rowKey
    } = this.props;
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
                key={rowKey(element)}
                data-document-id={rowKey(element)}
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
  rowKey: PropTypes.func.isRequired,
  rowMargin: PropTypes.number,
  headerRenderer: PropTypes.func,
  headerHeight: PropTypes.number,
  rowRenderer: PropTypes.func,
  style: PropTypes.object,
  reportCurrentDocument: PropTypes.func,
  initialTopDocument: PropTypes.string,
  resetPositionToken: PropTypes.string
};

InfiniteScroll.defaultProps = {
  rowHeight: 38,
  rowMargin: 3,
  rowKey: o => o.elasticId
};

export default InfiniteScroll;
export {DEFAULT_PAGE_SIZE};
