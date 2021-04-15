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
import {inject, observer} from 'mobx-react';
import {Alert, Icon} from 'antd';
import Preview from '../preview';
import {InfiniteScroll, PresentationModes} from '../faceted-search/controls';
import DocumentListPresentation from './document-presentation/list';
import {DocumentColumns, parseExtraColumns} from './utilities/document-columns';
import {PUBLIC_URL} from '../../../config';
import styles from './search-results.css';

const RESULT_ITEM_HEIGHT = 46;
const TABLE_ROW_HEIGHT = 32;
const TABLE_HEADER_HEIGHT = 28;
const RESULT_ITEM_MARGIN = 2;
const PREVIEW_TIMEOUT = 1000;
const HOVER_DELAY = 0;
const DIVIDER_WIDTH = 4;
const PREVIEW_POSITION = {
  left: {
    top: '84px',
    left: '75px',
    maxHeight: 'calc(100vh - 135px)'
  },
  right: {
    top: '84px',
    right: '10px',
    maxHeight: 'calc(100vh - 135px)'
  }
};

function compareDocumentTypes (prev, next) {
  const a = (prev || []).sort();
  const b = (next || []).sort();
  if (a.length !== b.length) {
    return false;
  }
  for (let i = 0; i < a.length; i++) {
    if (a[i] !== b[i]) {
      return false;
    }
  }
  return true;
}

function cellStringWithDivider (column) {
  if (!column || !column.key) {
    return '.';
  }
  return `${column.key.replaceAll(' ', '___')} .`;
}

class SearchResults extends React.Component {
  state = {
    resultsAreaHeight: undefined,
    preview: undefined,
    resizingColumn: undefined,
    draggingCell: undefined,
    columnWidths: {},
    columns: DocumentColumns.map(column => column.key),
    previewPosition: PREVIEW_POSITION.right,
    extraColumnsConfiguration: [],
    arrangedColumns: []
  };

  dividerRefs = {};
  headerRef = null;
  resultsContainerRef = null;
  tableWidth = undefined;
  animationFrame;
  infiniteScroll;
  hoverTimeout;
  rects = {
    dragger: null,
    header: null,
    cells: []
  };

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (!compareDocumentTypes(prevProps.documentTypes, this.props.documentTypes)) {
      this.updateDocumentTypes();
    }
    if (
      prevState.columnWidths !== this.state.columnWidths ||
      prevState.resizingColumn !== this.state.resizingColumn ||
      prevState.columns !== this.state.columns ||
      prevState.arrangedColumns !== this.state.arrangedColumns ||
      prevState.draggingCell !== this.state.draggingCell
    ) {
      if (this.infiniteScroll) {
        this.infiniteScroll.forceUpdate();
      }
    }
  }

  componentDidMount () {
    window.addEventListener('mousemove', this.onResize);
    window.addEventListener('mouseup', this.stopResizing);
    window.addEventListener('keydown', this.onKeyDown);
    this.updateDocumentTypes();
    parseExtraColumns(this.props.preferences)
      .then(extra => {
        if (extra && extra.length) {
          this.setState({extraColumnsConfiguration: extra}, this.updateDocumentTypes);
        }
      });
  }

  componentWillUnmount () {
    if (this.animationFrame) {
      cancelAnimationFrame(this.animationFrame);
    }
    window.removeEventListener('mousemove', this.onResize);
    window.removeEventListener('mouseup', this.stopResizing);
    window.removeEventListener('keydown', this.onKeyDown);
  }

  get columnsConfiguration () {
    const {extraColumnsConfiguration = []} = this.state;
    return [...DocumentColumns, ...extraColumnsConfiguration];
  }

  get columns () {
    const {columns} = this.state;
    if (!columns || !columns.size) {
      return this.columnsConfiguration;
    }
    return this.columnsConfiguration.filter(k => columns.has(k.key));
  }

  get arrangedColumns () {
    const {arrangedColumns} = this.state;
    if (arrangedColumns && arrangedColumns.length) {
      return arrangedColumns;
    }
    return this.columns;
  }

  updateDocumentTypes = () => {
    const {documentTypes} = this.props;
    if (!documentTypes || !documentTypes.length) {
      this.setState({columns: new Set(this.columnsConfiguration.map(column => column.key))});
    } else {
      const columns = this.columnsConfiguration
        .filter(column => !column.types || documentTypes.find(type => column.types.has(type)))
        .map(column => column.key);
      this.setState({columns: new Set(columns)});
    }
  };

  onInitializeInfiniteScroll = (infiniteScroll) => {
    this.infiniteScroll = infiniteScroll;
  };

  renderSearchResultItem = (resultItem) => {
    const {disabled} = this.props;
    return (
      <a
        href={!disabled && resultItem.url ? `${PUBLIC_URL || ''}/#${resultItem.url}` : undefined}
        key={resultItem.elasticId}
        className={styles.resultItemContainer}
        onClick={this.navigate(resultItem)}
      >
        <Icon
          type="info-circle-o"
          className={styles.previewBtn}
          style={{height: RESULT_ITEM_HEIGHT, marginBottom: RESULT_ITEM_MARGIN}}
          onClick={(e) => {
            e.stopPropagation();
            e.preventDefault();
            this.setPreview(resultItem);
          }}
        />
        <div
          id={`search-result-item-${resultItem.elasticId}`}
          className={
            classNames(
              styles.resultItem,
              {[styles.disabled]: disabled}
            )
          }
          style={{height: RESULT_ITEM_HEIGHT, marginBottom: RESULT_ITEM_MARGIN}}
        >
          <DocumentListPresentation
            className={styles.title}
            document={resultItem}
          />
        </div>
      </a>
    );
  };

  onKeyDown = (event) => {
    const {preview} = this.state;
    if (preview && event.key && event.key.toLowerCase() === 'escape') {
      this.closePreview();
    }
  }

  setPreview = (info, delayed) => {
    if (this.previewTimeout) {
      clearTimeout(this.previewTimeout);
    }
    this.previewTimeout = null;
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

  closePreview = () => {
    this.setState({preview: undefined});
  }

  onPreviewWrapperClick = (event) => {
    if (event && event.target === event.currentTarget) {
      this.closePreview();
    }
  }

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
    const {preview} = this.state;
    if (!preview) {
      return null;
    }
    return (
      <div
        className={styles.previewWrapper}
        onClick={(e) => this.onPreviewWrapperClick(e)}
      >
        <div
          className={styles.preview}
        >
          <Preview
            item={preview}
            lightMode
          />
        </div>
      </div>
    );
  }

  renderResultsList = () => {
    const {
      documents,
      disabled,
      error,
      hasElementsAfter,
      hasElementsBefore,
      showResults,
      onLoadData,
      onPageSizeChanged,
      pageSize
    } = this.props;
    if (error) {
      return (
        <Alert type="error" message={error} />
      );
    }
    if (showResults && (documents || []).length === 0) {
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
            disabled={disabled}
            error={error}
            hasElementsAfter={hasElementsAfter}
            hasElementsBefore={hasElementsBefore}
            onScrollToEnd={onLoadData}
            onPageSizeChanged={onPageSizeChanged}
            elements={documents}
            pageSize={pageSize}
            rowRenderer={this.renderSearchResultItem}
            rowMargin={RESULT_ITEM_MARGIN}
            rowHeight={RESULT_ITEM_HEIGHT}
            onInitialized={this.onInitializeInfiniteScroll}
          />
        </div>
      </div>
    );
  }

  getGridTemplate = (headerTemplate) => {
    const {columnWidths, draggingCell} = this.state;
    const rowHeight = headerTemplate
      ? TABLE_HEADER_HEIGHT
      : TABLE_ROW_HEIGHT;
    const cellDefault = '100px';
    const columns = draggingCell && draggingCell.key
      ? this.arrangedColumns.filter(column => column.key !== draggingCell.key)
      : this.arrangedColumns;
    const columnString = `'${columns
      .map(c => `${c.key} .`).join(' ')}' ${rowHeight}px /`;
    const widthString = `${columns
      .map(c => `${columnWidths[c.key] || c.width || cellDefault} ${DIVIDER_WIDTH}px`)
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
      const step = 2;
      const offset = event.clientX - (rect.right + DIVIDER_WIDTH);
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
    window.removeEventListener('mousemove', this.onResize);
    window.removeEventListener('mouseup', this.stopResizing);
  }

  initResizing = (event, column) => {
    const {resizingColumn} = this.state;
    event && event.stopPropagation();
    window.addEventListener('mousemove', this.onResize);
    window.addEventListener('mouseup', this.stopResizing);
    if (!resizingColumn) {
      this.setState({resizingColumn: column.key});
    }
  }

  getDropIndex = (dropX) => {
    const {draggingCell} = this.state;
    const {cells} = this.rects;
    if (!cells || !cells.length) {
      return -1;
    }
    try {
      const initialIndex = cells.findIndex(({key}) => key === draggingCell.key);
      const targetIndex = cells.findIndex(({rect}) => {
        return rect.left - DIVIDER_WIDTH <= dropX && rect.right + DIVIDER_WIDTH >= dropX;
      });
      if (initialIndex === targetIndex) {
        return -1;
      }
      if (targetIndex >= 0) {
        return dropX > cells[targetIndex].rect.left + (cells[targetIndex].rect.width / 2)
          ? targetIndex + 1
          : targetIndex;
      }
    } catch (___) {}
    return -1;
  }

  rearrangeTable = (init, target) => {
    const columns = [...this.arrangedColumns];
    const movingColumn = columns.splice(init, 1)[0];
    columns.splice(target, 0, movingColumn);
    this.setState({arrangedColumns: columns});
  }

  initCellDragging = (event, column) => {
    if (!event) {
      return;
    }
    event.persist();
    this.setState({draggingCell: column}, () => {
      if (this.draggerRef) {
        this.rects.header = this.draggerRef.parentElement.getBoundingClientRect();
        this.rects.dragger = this.draggerRef.getBoundingClientRect();
        this.rects.cells = Object.entries(this.dividerRefs).map(([key, value]) => {
          if (value && value instanceof HTMLElement) {
            return {
              rect: value.getBoundingClientRect(),
              key
            };
          }
        }).sort((a, b) => a.rect.left - b.rect.left);
        window.addEventListener('mousemove', this.startCellDragging, false);
        window.addEventListener('mouseup', this.stopCellDragging, false);
        this.startCellDragging(event);
      }
    });
  }

  startCellDragging = (event) => {
    const {header, dragger} = this.rects;
    if (!event || !header || !dragger) {
      return;
    }
    const scrollWidth = 15;
    const draggerX = event.clientX - header.x + (dragger.width / 2) > header.width - scrollWidth
      ? header.right - header.x - dragger.width - scrollWidth
      : Math.max(3, event.clientX - header.x - (dragger.width / 2));
    this.draggerRef.style.left = `${draggerX}px`;
  }

  stopCellDragging = (event) => {
    const {draggingCell} = this.state;
    const dropX = Math.max(this.rects.header.x, event.clientX);
    const targetIndex = this.getDropIndex(dropX);
    const initIndex = this.arrangedColumns.findIndex(({key}) => key === draggingCell.key);
    if (targetIndex >= 0) {
      this.rearrangeTable(initIndex, targetIndex);
    }
    this.setState({draggingCell: null}, () => {
      this.rects.header = null;
      this.rects.dragger = null;
      this.rects.cells = [];
    });
    window.removeEventListener('mousemove', this.startCellDragging, false);
    window.removeEventListener('mouseup', this.stopCellDragging, false);
  }

  renderTableRow = (resultItem, rowIndex) => {
    const {disabled} = this.props;
    const {
      columnWidths,
      resizingColumn,
      draggingCell
    } = this.state;
    if (!resultItem) {
      return null;
    }
    return (
      <a
        href={!disabled && resultItem.url ? `${PUBLIC_URL || ''}/#${resultItem.url}` : undefined}
        className={styles.tableRow}
        style={{gridTemplate: this.getGridTemplate()}}
        key={rowIndex}
        onClick={this.navigate(resultItem)}
      >
        {this.arrangedColumns.map((column, index) => (
          [
            <div
              className={classNames(
                styles.tableCell,
                {[styles.moving]: draggingCell && draggingCell.key === column.key}
              )}
              key={index}
              style={{
                width: columnWidths[column.key],
                minWidth: '0px',
                gridArea: column.key
              }}
            >
              {column.renderFn
                ? column.renderFn(resultItem[column.key], resultItem)
                : <span className={styles.cellValue}>{resultItem[column.key]}</span>
              }
            </div>,
            <div
              className={classNames(
                styles.tableDivider,
                {[styles.dividerActive]: resizingColumn === column.key}
              )}
            />
          ]
        ))
        }
      </a>
    );
  }

  renderTableHeader = () => {
    const {
      columnWidths,
      resizingColumn,
      draggingCell
    } = this.state;
    return (
      <div
        className={classNames(
          styles.tableRow,
          styles.tableHeader
        )}
        style={{gridTemplate: this.getGridTemplate(true)}}
        ref={header => (this.headerRef = header)}
        onDragStart={this.startCellDragging}
        onDragEnd={this.stopCellDragging}
      >
        {this.arrangedColumns.map((column, index) => ([
          <div
            key={index}
            className={classNames(
              styles.headerCell,
              {[styles.moving]: draggingCell && draggingCell.key === column.key}
            )}
            ref={ref => (this.dividerRefs[column.key] = ref)}
            style={{
              width: columnWidths[column.key],
              minWidth: '0px',
              gridArea: column.key
            }}
            onMouseDown={e => this.initCellDragging(e, column)}
          >
            {column.name}
          </div>,
          <div
            className={classNames(
              styles.tableDivider,
              {[styles.dividerActive]: resizingColumn === column.key}
            )}
            onMouseDown={e => this.initResizing(e, column)}
          />
        ]))}
      </div>
    );
  }

  renderResultsTable = () => {
    const {
      documents,
      disabled,
      error,
      hasElementsAfter,
      hasElementsBefore,
      onPageSizeChanged,
      onLoadData,
      pageSize,
      showResults
    } = this.props;
    if (error) {
      return (
        <Alert type="error" message={error} />
      );
    }
    if (showResults && (documents || []).length === 0) {
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
          disabled={disabled}
          error={error}
          hasElementsAfter={hasElementsAfter}
          hasElementsBefore={hasElementsBefore}
          onScrollToEnd={onLoadData}
          onPageSizeChanged={onPageSizeChanged}
          elements={documents}
          pageSize={pageSize}
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
    const {
      preview,
      draggingCell
    } = this.state;
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
        {draggingCell ? (
          <div
            className={classNames(styles.headerCell, styles.dragger)}
            ref={ref => (this.draggerRef = ref)}
          >
            {draggingCell.name || ''}
          </div>
        ) : null}
      </div>
    );
  }
}

SearchResults.propTypes = {
  className: PropTypes.string,
  documents: PropTypes.array,
  error: PropTypes.string,
  hasElementsAfter: PropTypes.bool,
  hasElementsBefore: PropTypes.bool,
  onLoadData: PropTypes.func,
  onPageSizeChanged: PropTypes.func,
  onNavigate: PropTypes.func,
  pageSize: PropTypes.number,
  showResults: PropTypes.bool,
  style: PropTypes.object,
  onChangeDocumentType: PropTypes.func,
  onChangeBottomOffset: PropTypes.func,
  mode: PropTypes.oneOf([PresentationModes.list, PresentationModes.table]),
  documentTypes: PropTypes.array
};

SearchResults.defaultProps = {
  documents: [],
  pageSize: 20,
  documentTypes: []
};

export default inject('preferences')(observer(SearchResults));
export {DEFAULT_PAGE_SIZE} from './controls/infinite-scroll';
