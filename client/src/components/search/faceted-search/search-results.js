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
import {
  Alert,
  Icon,
  Pagination
} from 'antd';
import {PreviewIcons} from '../preview/previewIcons';
import {SearchItemTypes} from '../../../models/search';
import styles from './search-results.css';

const RESULT_ITEM_HEIGHT = 55;
const RESULT_ITEM_MARGIN = 5;

class SearchResults extends React.Component {
  state = {
    resultsAreaHeight: undefined,
    hoveredIndex: undefined
  };

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.page !== this.props.page) {
      this.unHoverItem(this.state.hoveredIndex)();
    }
  }

  initializeResultsArea = (area) => {
    this.area = area;
    this.resizeResultsArea();
  }

  resizeResultsArea = () => {
    if (this.area) {
      const height = this.area.clientHeight;
      const {resultsAreaHeight} = this.state;
      const {onChangePage, page, pageSize} = this.props;
      if (height !== resultsAreaHeight) {
        this.setState({
          resultsAreaHeight: height
        }, () => {
          const newPageSize = Math.floor(height / (RESULT_ITEM_HEIGHT + RESULT_ITEM_MARGIN));
          if (onChangePage) {
            const currentItem = (page - 1) * pageSize;
            const newPage = Math.floor(currentItem / newPageSize) + 1;
            onChangePage(newPage, newPageSize);
          }
        });
      }
    }
  };

  onChangePagination = (page, pageSize) => {
    const {onChangePage} = this.props;
    if (onChangePage) {
      onChangePage(page, pageSize);
    }
  }

  renderIcon = (resultItem) => {
    if (PreviewIcons[resultItem.type]) {
      return (
        <Icon
          className={styles.icon}
          type={PreviewIcons[resultItem.type]} />
      );
    }
    return null;
  };

  renderSearchResultItem = (resultItem) => {
    const {hoveredIndex} = this.state;
    const renderName = () => {
      switch (resultItem.type) {
        case SearchItemTypes.run: {
          if (resultItem.description) {
            const parts = resultItem.description.split('/');
            if (parts.length > 1) {
              return `${resultItem.name} - ${parts.pop()}`;
            }
            return `${resultItem.name} - ${resultItem.description}`;
          }
          return resultItem.name || `Run ${resultItem.elasticId}`;
        }
        case SearchItemTypes.NFSFile:
        case SearchItemTypes.gsFile:
        case SearchItemTypes.azFile:
        case SearchItemTypes.s3File: {
          const path = (resultItem.name || '');
          return path.split('/').pop().split('\\').pop();
        }
        default: return resultItem.name;
      }
    };
    return (
      <a
        href={`/#${resultItem.url}`}
        target="_blank"
        key={resultItem.elasticId}
        className={styles.resultItemContainer}
      >
        <div
          id={`search-result-item-${resultItem.elasticId}`}
          className={
            classNames(
              styles.resultItem,
              {
                [styles.hovered]: hoveredIndex === resultItem.elasticId
              }
            )
          }
          style={{height: RESULT_ITEM_HEIGHT, marginBottom: RESULT_ITEM_MARGIN}}
          onMouseOver={this.hoverItem(resultItem.elasticId)}
          onMouseEnter={this.hoverItem(resultItem.elasticId)}
          onMouseLeave={this.unHoverItem(resultItem.elasticId)}
          onClick={this.navigate(resultItem)}
        >
          <div style={{display: 'inline-block'}}>
            {this.renderIcon(resultItem)}
          </div>
          <span className={styles.title}>
            {renderName()}
          </span>
        </div>
      </a>
    );
  };

  unHoverItem = (index) => () => {
    const {hoveredIndex} = this.state;
    if (hoveredIndex === index) {
      this.setState({hoveredIndex: undefined});
    }
  }

  hoverItem = (index) => () => {
    const {hoveredIndex} = this.state;
    if (hoveredIndex !== index) {
      this.setState({hoveredIndex: index});
    }
  };

  navigate = (item) => (e) => {
    if (e && (e.ctrlKey || e.metaKey)) {
      return;
    }
    if (e) {
      e.preventDefault();
      e.stopPropagation();
    }
    const {onNavigate} = this.props;
    if (onNavigate) {
      onNavigate(item);
    }
  }

  render () {
    const {
      className,
      disabled,
      documents,
      style,
      showResults,
      total,
      page,
      pageSize
    } = this.props;
    return (
      <div
        className={classNames(
          styles.container,
          className
        )}
        style={style}
      >
        <div
          className={styles.results}
          ref={this.initializeResultsArea}
        >
          {
            showResults && total === 0 && (
              <Alert type="info" message="Nothing found" />
            )
          }
          {
            showResults && total > 0 && documents.map(this.renderSearchResultItem)
          }
        </div>
        <div
          className={styles.pagination}
        >
          {
            showResults && (
              <Pagination
                disabled={disabled}
                current={page}
                total={total}
                pageSize={pageSize}
                onChange={this.onChangePagination}
              />
            )
          }
        </div>
      </div>
    );
  }
}

SearchResults.propTypes = {
  className: PropTypes.string,
  documents: PropTypes.array,
  onChangePage: PropTypes.func,
  onNavigate: PropTypes.func,
  page: PropTypes.number,
  pageSize: PropTypes.number,
  showResults: PropTypes.bool,
  style: PropTypes.object,
  total: PropTypes.number
};

SearchResults.defaultProps = {
  documents: [],
  page: 1,
  pageSize: 20,
  total: 0
};

export default SearchResults;
