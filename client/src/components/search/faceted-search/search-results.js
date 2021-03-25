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
import {InfiniteScroll, PresentationModes} from '../faceted-search/controls';
import styles from './search-results.css';

const RESULT_ITEM_HEIGHT = 38;
const TABLE_ROW_HEIGHT = 32;
const TABLE_HEADER_HEIGHT = 28;
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
  headerRef = null;
  tableWidth = undefined;
  animationFrame;

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.offset !== this.props.offset) {
      this.unHoverItem(this.state.hoverInfo)();
    }
  }

  componentDidMount () {
    window.addEventListener('mousemove', this.onResize);
    window.addEventListener('mouseup', this.stopResizing);
  }

  componentWillUnmount () {
    if (this.animationFrame) {
      cancelAnimationFrame(this.animationFrame);
    }
    window.removeEventListener('mousemove', this.onResize);
    window.removeEventListener('mouseup', this.stopResizing);
  }

  onInfiniteScrollOffsetChanged = (offset, pageSize) => {
    const {
      onChangeOffset,
      offset: currentOffset,
      pageSize: currentPageSize
    } = this.props;
    if (onChangeOffset && (currentOffset !== offset || currentPageSize !== pageSize)) {
      onChangeOffset(offset, pageSize);
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
      documentsOffset,
      error,
      showResults,
      total,
      offset
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
        >
          {
            showResults && total === 0 && (
              <Alert type="info" message="Nothing found" />
            )
          }
          <InfiniteScroll
            dataOffset={documentsOffset}
            error={error}
            offset={offset}
            total={total}
            style={{height: '100%'}}
            onOffsetChanged={this.onInfiniteScrollOffsetChanged}
            elements={documents}
            rowRenderer={this.renderSearchResultItem}
            rowMargin={RESULT_ITEM_MARGIN}
            rowHeight={RESULT_ITEM_HEIGHT}
          />
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
      width: '25%'
    },
    {key: 'type', name: 'Type', width: '15%'},
    {key: 'url', name: 'Url', width: '15%'},
    {key: 'elasticId', name: 'Elastic id', width: '15%'},
    {key: 'id', name: 'Identifier', width: '15%'}
  ]

  getGridTemplate = (headerTemplate) => {
    const {columnWidths} = this.state;
    const rowHeight = headerTemplate
      ? TABLE_HEADER_HEIGHT
      : TABLE_ROW_HEIGHT;
    const cellDefault = '100px';
    const divider = '4px';
    const columnString = `'${this.columns
      .map(c => `${c.key} .`).join(' ')}' ${rowHeight}px /`;
    const widthString = `${this.columns
      .map(c => `${columnWidths[c.key] || c.width || cellDefault} ${divider}`)
      .join(' ')}`;
    return columnString.concat(widthString);
  };

  onResize = (event) => {
    const {resizingColumn, columnWidths} = this.state;
    if (resizingColumn) {
      this.animationFrame = requestAnimationFrame(this.onResize);
      const rect = this.dividerRefs[resizingColumn].getBoundingClientRect();
      if (!this.tableWidth) {
        this.tableWidth = this.headerRef.getBoundingClientRect().width;
      }
      const divider = 5;
      const step = 2;
      const offset = event.clientX - (rect.right + divider);
      const maxWidth = this.tableWidth / 3;
      const minWidth = 50;
      if ((rect.width + offset) > maxWidth ||
        (rect.width + offset) < minWidth) {
        return null;
      }
      if (Math.abs(offset) > step) {
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
              style={{width: columnWidths[key], minWidth: '0px'}}
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
        style={{gridTemplate: this.getGridTemplate(true)}}
        ref={header => (this.headerRef = header)}
      >
        {this.columns.map(({key, name}, index) => ([
          <div
            key={index}
            className={styles.headerCell}
            ref={ref => (this.dividerRefs[key] = ref)}
            style={{width: columnWidths[key], minWidth: '0px'}}
          >
            {name}
          </div>,
          <div
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
      documentsOffset,
      error,
      total,
      offset
    } = this.props;
    return (
      <div
        className={styles.tableContainer}
        onBlur={this.stopResizing}
      >
        <InfiniteScroll
          dataOffset={documentsOffset}
          error={error}
          offset={offset}
          total={total}
          style={{height: '100%'}}
          onOffsetChanged={this.onInfiniteScrollOffsetChanged}
          elements={documents}
          headerRenderer={this.renderTableHeader}
          rowRenderer={this.renderTableRow}
          rowMargin={0}
          rowHeight={TABLE_ROW_HEIGHT}
        />
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
        {mode === PresentationModes.table ? this.renderResultsTable() : this.renderResultsList()}
      </div>
    );
  }
}

SearchResults.propTypes = {
  className: PropTypes.string,
  documents: PropTypes.array,
  documentsOffset: PropTypes.number,
  error: PropTypes.string,
  onChangeOffset: PropTypes.func,
  onNavigate: PropTypes.func,
  offset: PropTypes.number,
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
  documentsOffset: 0,
  offset: 0,
  pageSize: 20,
  total: 0
};

export default SearchResults;
