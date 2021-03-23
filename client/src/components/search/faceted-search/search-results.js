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
  Icon
} from 'antd';
import {PreviewIcons} from '../preview/previewIcons';
import {SearchItemTypes} from '../../../models/search';
// TODO: Preview disabled until it will be converted into a popover or something
// import Preview from '../preview';
import {PresentationModes} from '../faceted-search/controls';
import styles from './search-results.css';

const RESULT_ITEM_HEIGHT = 38;
const RESULT_ITEM_MARGIN = 3;
const PREVIEW_TIMEOUT = 1000;

class SearchResults extends React.Component {
  state = {
    resultsAreaHeight: undefined,
    hoverInfo: undefined,
    preview: undefined,
    resizingColumn: undefined,
    columnWidths: {}
  };

  dividerRefs = [];
  animationFrame;

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.page !== this.props.page) {
      this.unHoverItem(this.state.hoverInfo)();
    }
  }

  componentWillUnmount () {
    if (this.animationFrame) {
      cancelAnimationFrame(this.animationFrame);
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
      const {
        onChangePage,
        onChangeBottomOffset,
        page,
        pageSize
      } = this.props;
      if (height !== resultsAreaHeight) {
        this.setState({
          resultsAreaHeight: height
        }, () => {
          const newPageSize = 2 * Math.floor(height / (RESULT_ITEM_HEIGHT + RESULT_ITEM_MARGIN));
          if (onChangePage) {
            const currentItem = (page - 1) * pageSize;
            const newPage = Math.floor(currentItem / newPageSize) + 1;
            onChangePage(newPage, newPageSize);
          }
          if (onChangeBottomOffset) {
            onChangeBottomOffset(height - newPageSize * (RESULT_ITEM_HEIGHT + RESULT_ITEM_MARGIN));
          }
        });
      }
    }
  };

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

  getResultItemName = (resultItem) => {
    if (!resultItem) {
      return '';
    }
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
  }

  renderSearchResultItem = (resultItem) => {
    const {disabled} = this.props;
    const {hoverInfo, preview} = this.state;
    return (
      <a
        href={!disabled && resultItem.url ? `/#${resultItem.url}` : undefined}
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
                [styles.disabled]: disabled,
                [styles.hovered]: !disabled && (hoverInfo === resultItem || preview === resultItem)
              }
            )
          }
          style={{height: RESULT_ITEM_HEIGHT, marginBottom: RESULT_ITEM_MARGIN}}
          onMouseOver={this.hoverItem(resultItem)}
          onMouseEnter={this.hoverItem(resultItem)}
          onMouseLeave={this.unHoverItem(resultItem)}
          onClick={this.navigate(resultItem)}
        >
          <div style={{display: 'inline-block'}}>
            {this.renderIcon(resultItem)}
          </div>
          <span className={styles.title}>
            {this.getResultItemName(resultItem)}
          </span>
        </div>
      </a>
    );
  };

  unHoverItem = (info) => () => {
    const {hoverInfo} = this.state;
    if (hoverInfo === info) {
      this.setState({hoverInfo: undefined}, () => this.setPreview(undefined));
    }
  }

  hoverItem = (info) => () => {
    const {hoverInfo, preview} = this.state;
    if (hoverInfo !== info) {
      this.setState({hoverInfo: info}, () => {
        this.setPreview(info, !preview);
      });
    }
  };

  setPreview = (info, delayed = true) => {
    if (this.previewTimeout) {
      clearTimeout(this.previewTimeout);
    }
    if (delayed) {
      this.previewTimeout = setTimeout(
        () => {
          this.setState({preview: info});
        },
        PREVIEW_TIMEOUT
      );
    } else {
      this.setState({preview: info});
    }
  };

  doNotHidePreview = (info) => {
    if (this.previewTimeout) {
      clearTimeout(this.previewTimeout);
    }
    this.setState({preview: info, hoverInfo: info});
  };

  navigate = (item) => (e) => {
    if (this.props.disabled) {
      return;
    }
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

  renderResultsList = () => {
    const {
      documents,
      showResults,
      total
    } = this.props;
    return (
      <div
        className={styles.content}
      >
        <div
          className={
            classNames(
              styles.results,
              {
                [styles.hint]: !showResults
              }
            )
          }
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
        {/* TODO: Preview disabled until it will be converted into a popover or something */}
        {/* {
          preview && showResults && (
            <div
              className={styles.preview}
              onMouseOver={() => this.doNotHidePreview(preview)}
              onMouseLeave={this.unHoverItem(preview)}
            >
              <Preview
                item={preview}
                lightMode
              />
            </div>
          )
        } */}
      </div>);
  }

  columns = [
    {
      key: 'name',
      name: 'Name',
      renderFn: (value, resultItem) => (
        <span>
          {this.renderIcon(resultItem)}
          <b style={{marginLeft: '5px'}}>
            {this.getResultItemName(resultItem)}
          </b>
        </span>
      ),
      width: '2fr'
    },
    {key: 'type', name: 'Type', width: '1fr'},
    {key: 'url', name: 'Url', width: '2fr'},
    {key: 'elasticId', name: 'Elastic id', width: '2fr'},
    {key: 'id', name: 'Identifier', width: '2fr'}
  ]

  getGridTemplate = () => {
    const {columnWidths} = this.state;
    const columnString = `'${this.columns
      .map(c => `${c.key} .`).join(' ')}' 1fr /`;
    const widthString = `${this.columns
      .map(c => `${columnWidths[c.key] || c.width || '1fr'} 4px`).join(' ')}`;
    return columnString.concat(widthString);
  };

  onResize = (event) => {
    const {resizingColumn, columnWidths} = this.state;
    if (resizingColumn) {
      this.animationFrame = requestAnimationFrame(this.onResize);
      const rect = this.dividerRefs[resizingColumn].getBoundingClientRect();
      const divider = 5;
      const offset = event.clientX - (rect.right + divider);
      if (Math.abs(offset) > 10) {
        columnWidths[resizingColumn] = `${Math.round(rect.width + offset)}px`;
        this.setState(columnWidths);
      }
    }
  }

  stopResizing = (event) => {
    const {resizingColumn} = this.state;
    event && event.stopPropagation();
    if (resizingColumn) {
      this.setState({resizingColumn: undefined});
    }
  }

  initResizing = (event, key) => {
    const {resizingColumn} = this.state;
    event && event.stopPropagation();
    if (!resizingColumn) {
      this.setState({resizingColumn: key});
    }
  }

  renderTableRow = (resultItem, rowIndex) => {
    const {disabled} = this.props;
    const {columnWidths, resizingColumn} = this.state;
    if (!resultItem) {
      return null;
    }
    return (
      <a
        href={!disabled && resultItem.url ? `/#${resultItem.url}` : undefined}
        target="_blank"
        className={styles.tableRow}
        style={{gridTemplate: this.getGridTemplate()}}
        key={rowIndex}
      >
        {this.columns.map(({key, renderFn}, index) => (
          [
            <div
              className={styles.tableCell}
              key={index}
              style={{width: columnWidths[key]}}
            >
              {renderFn
                ? renderFn(resultItem[key], resultItem)
                : resultItem[key]
              }
            </div>,
            <div
              className={classNames(
                styles.tableDivider,
                {[styles.dividerActive]: resizingColumn === key}
              )}
            />
          ]
        ))
        }
      </a>
    );
  }

  renderTableHeader = () => {
    const {columnWidths, resizingColumn} = this.state;
    return (
      <div
        className={classNames(
          styles.tableRow,
          styles.tableHeader
        )}
        style={{gridTemplate: this.getGridTemplate()}}
        onMouseMove={(e) => this.onResize(e)}
      >
        {this.columns.map(({key, name}, index) => ([
          <div
            key={index}
            className={styles.headerCell}
            ref={ref => (this.dividerRefs[key] = ref)}
            style={{width: columnWidths[key]}}
          >
            {name}
          </div>,
          <div
            style={{userSelect: 'none'}}
            className={classNames(
              styles.tableDivider,
              {[styles.dividerActive]: resizingColumn === key}
            )}
            onMouseDown={e => this.initResizing(e, key)}
          />
        ]))}
      </div>
    );
  }

  renderResultsTable = () => {
    const {
      documents,
      showResults,
      total
    } = this.props;
    if (!showResults || !total) {
      return <Alert type="info" message="Nothing found" />;
    }
    return (
      <div
        className={styles.tableContainer}
        onMouseUp={this.stopResizing}
        onBlur={this.stopResizing}
      >
        {this.renderTableHeader()}
        {documents.map((document, index) => (
          this.renderTableRow(document, index)
        ))}
      </div>
    );
  }

  render () {
    const {
      className,
      style,
      mode
    } = this.props;
    if (!mode) {
      return null;
    }
    return (
      <div
        className={classNames(
          styles.container,
          className
        )}
        style={style}
      >
        {mode === PresentationModes.list && this.renderResultsList()}
        {mode === PresentationModes.table && this.renderResultsTable()}
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
  total: PropTypes.number,
  onChangeDocumentType: PropTypes.func,
  onChangeBottomOffset: PropTypes.func,
  mode: PropTypes.oneOf([PresentationModes.list, PresentationModes.table])
};

SearchResults.defaultProps = {
  documents: [],
  page: 1,
  pageSize: 20,
  total: 0
};

export default SearchResults;
