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
import Preview from '../preview';
import {InfiniteScroll, PresentationModes} from '../faceted-search/controls';
import DocumentListPresentation from './document-presentation/list';
import getDocumentName from './document-presentation/utilities/get-document-name';
import styles from './search-results.css';

const RESULT_ITEM_HEIGHT = 32;
const TABLE_ROW_HEIGHT = 32;
const TABLE_HEADER_HEIGHT = 28;
const RESULT_ITEM_MARGIN = 2;
const PREVIEW_TIMEOUT = 1000;
const HOVER_DELAY = 300;
const PREVIEW_POSITION = {
  left: {
    top: '85px',
    left: '140px',
    maxHeight: 'calc(100vh - 135px)'
  },
  right: {
    top: '85px',
    right: '50px',
    maxHeight: 'calc(100vh - 135px)'
  }
};

class SearchResults extends React.Component {
  state = {
    resultsAreaHeight: undefined,
    hoverInfo: undefined,
    preview: undefined,
    resizingColumn: undefined,
    columnWidths: {},
    previewPosition: PREVIEW_POSITION.right
  };

  dividerRefs = [];
  headerRef = null;
  resultsContainerRef = null;
  tableWidth = undefined;
  animationFrame;
  infiniteScroll;
  hoverTimeout;

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.offset !== this.props.offset) {
      this.unHoverItem(this.state.hoverInfo, true)();
    }
    if (
      prevState.hoverInfo !== this.state.hoverInfo ||
      prevState.preview !== this.state.preview ||
      prevState.columnWidths !== this.state.columnWidths ||
      prevState.resizingColumn !== this.state.resizingColumn
    ) {
      if (this.infiniteScroll) {
        this.infiniteScroll.forceUpdate();
      }
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

  onInitializeInfiniteScroll = (infiniteScroll) => {
    this.infiniteScroll = infiniteScroll;
  };

  renderSearchResultItem = (resultItem) => {
    const {disabled} = this.props;
    const {hoverInfo, preview} = this.state;
    return (
      <a
        href={!disabled && resultItem.url ? `/#${resultItem.url}` : undefined}
        key={resultItem.elasticId}
        className={styles.resultItemContainer}
        onMouseOver={(e) => this.hoverItem(resultItem, e)}
        onMouseEnter={(e) => this.hoverItem(resultItem, e)}
        onMouseLeave={this.unHoverItem(resultItem)}
        onClick={this.navigate(resultItem)}
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
        >
          <div style={{display: 'inline-block'}}>
            {this.renderIcon(resultItem)}
          </div>
          <DocumentListPresentation
            className={styles.title}
            document={resultItem}
          />
        </div>
      </a>
    );
  };

  unHoverItem = (info, forceUnhover) => () => {
    const {hoverInfo} = this.state;
    if (forceUnhover) {
      return this.setState(
        {hoverInfo: undefined}, () => this.setPreview(undefined, false)
      );
    }
    if (hoverInfo === info) {
      return this.setState(
        {hoverInfo: undefined}, () => this.setPreview(undefined)
      );
    }
  }

  getPreviewPosition = (cursorX) => {
    if (this.resultsContainerRef && cursorX) {
      const container = this.resultsContainerRef.getBoundingClientRect();
      const containerCenterX = (container.width / 2) + container.left;
      return cursorX > containerCenterX
        ? PREVIEW_POSITION.left
        : PREVIEW_POSITION.right;
    }
    return PREVIEW_POSITION.right;
  }

  hoverItem = (info, event) => {
    const {hoverInfo, preview} = this.state;
    if (hoverInfo !== info) {
      const previewPosition = this.getPreviewPosition(event.pageX);
      if (this.hoverTimeout) {
        clearTimeout(this.hoverTimeout);
      }
      this.hoverTimeout = setTimeout(() => {
        this.setState({
          hoverInfo: info,
          previewPosition
        }, () => {
          this.setPreview(info, !preview);
        });
      }, HOVER_DELAY);
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
    if (this.hoverTimeout) {
      clearTimeout(this.hoverTimeout);
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

  renderPreview = () => {
    const {preview, previewPosition} = this.state;
    return (
      <div
        className={styles.preview}
        style={previewPosition}
        onMouseOver={() => this.doNotHidePreview(preview)}
        onMouseLeave={this.unHoverItem(preview)}
      >
        <Preview
          item={preview}
          lightMode
        />
      </div>
    );
  }

  renderResultsList = () => {
    const {
      documents,
      documentsOffset,
      disabled,
      error,
      showResults,
      total,
      offset
    } = this.props;
    if (error) {
      return (
        <Alert type="error" message={error} />
      );
    }
    if (showResults && total === 0) {
      return (
        <Alert type="info" message="Nothing found" />
      );
    }
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
          <InfiniteScroll
            className={classNames(styles.infiniteScroll, styles.list)}
            dataOffset={documentsOffset}
            disabled={disabled}
            error={error}
            offset={offset}
            total={total}
            onOffsetChanged={this.onInfiniteScrollOffsetChanged}
            elements={documents}
            rowRenderer={this.renderSearchResultItem}
            rowMargin={RESULT_ITEM_MARGIN}
            rowHeight={RESULT_ITEM_HEIGHT}
            onInitialized={this.onInitializeInfiniteScroll}
          />
        </div>
      </div>
    );
  }

  columns = [
    {
      key: 'name',
      name: 'Name',
      renderFn: (value, resultItem) => (
        <span className={styles.cellValue}>
          {this.renderIcon(resultItem)}
          <b style={{marginLeft: '5px'}}>
            {getDocumentName(resultItem)}
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
        this.setState({columnWidths: {...columnWidths}});
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
        className={styles.tableRow}
        style={{gridTemplate: this.getGridTemplate()}}
        key={rowIndex}
        onMouseOver={(e) => this.hoverItem(resultItem, e)}
        onMouseEnter={(e) => this.hoverItem(resultItem, e)}
        onMouseLeave={this.unHoverItem(resultItem)}
        onClick={this.navigate(resultItem)}
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
                : <span className={styles.cellValue}>{resultItem[key]}</span>
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
      disabled,
      error,
      total,
      offset,
      showResults
    } = this.props;
    if (error) {
      return (
        <Alert type="error" message={error} />
      );
    }
    if (showResults && total === 0) {
      return (
        <Alert type="info" message="Nothing found" />
      );
    }
    return (
      <div
        className={styles.tableContainer}
        onBlur={this.stopResizing}
      >
        <InfiniteScroll
          className={classNames(styles.infiniteScroll, styles.table)}
          dataOffset={documentsOffset}
          disabled={disabled}
          error={error}
          offset={offset}
          total={total}
          onOffsetChanged={this.onInfiniteScrollOffsetChanged}
          elements={documents}
          headerRenderer={this.renderTableHeader}
          rowRenderer={this.renderTableRow}
          rowMargin={0}
          rowHeight={TABLE_ROW_HEIGHT}
          onInitialized={this.onInitializeInfiniteScroll}
        />
      </div>
    );
  }

  render () {
    const {
      className,
      style,
      showResults,
      mode
    } = this.props;
    const {preview} = this.state;
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
        ref={preview => (this.resultsContainerRef = preview)}
      >
        {mode === PresentationModes.table ? this.renderResultsTable() : this.renderResultsList()}
        {showResults && preview && this.renderPreview()}
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
