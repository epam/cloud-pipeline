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
    columnWidths: {},
    columns: DocumentColumns.map(column => column.key),
    extraColumnsConfiguration: []
  };

  dividerRefs = [];
  headerRef = null;
  resultsContainerRef = null;
  tableWidth = undefined;
  animationFrame;
  infiniteScroll;

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (!compareDocumentTypes(prevProps.documentTypes, this.props.documentTypes)) {
      this.updateDocumentTypes();
    }
    if (
      prevState.columnWidths !== this.state.columnWidths ||
      prevState.resizingColumn !== this.state.resizingColumn ||
      prevState.columns !== this.state.columns
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
    const {columnWidths} = this.state;
    const rowHeight = headerTemplate
      ? TABLE_HEADER_HEIGHT
      : TABLE_ROW_HEIGHT;
    const cellDefault = '100px';
    const divider = '4px';
    const columnString = `'${this.columns
      .map(cellStringWithDivider).join(' ')}' ${rowHeight}px /`;
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
        href={!disabled && resultItem.url ? `${PUBLIC_URL || ''}/#${resultItem.url}` : undefined}
        className={styles.tableRow}
        style={{gridTemplate: this.getGridTemplate()}}
        key={rowIndex}
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
                ? renderFn(resultItem[key], resultItem, this.setPreview)
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
