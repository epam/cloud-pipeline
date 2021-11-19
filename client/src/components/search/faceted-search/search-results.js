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
import {Alert, Icon, Spin} from 'antd';
import PreviewModal from '../preview/preview-modal';
import {InfiniteScroll, PresentationModes} from '../faceted-search/controls';
import DocumentListPresentation from './document-presentation/list';
import {DocumentColumns, parseExtraColumns} from './utilities/document-columns';
import {PUBLIC_URL} from '../../../config';
import styles from './search-results.css';
import OpenInToolAction from '../../special/file-actions/open-in-tool';
import compareArrays from '../../../utils/compareArrays';

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
    if (!compareArrays(prevProps.extraColumns, this.props.extraColumns)) {
      this.updateExtraColumns();
    }
  }

  componentDidMount () {
    window.addEventListener('mousemove', this.onResize);
    window.addEventListener('mouseup', this.stopResizing);
    window.addEventListener('keydown', this.onKeyDown);
    this.updateDocumentTypes();
    this.updateExtraColumns();
  }

  updateExtraColumns = () => {
    const {extraColumns = []} = this.props;
    parseExtraColumns(this.props.preferences)
      .then(extra => {
        const extraColumnsConfiguration = extraColumns.map(key => ({key, name: key}));
        if (extra && extra.length) {
          extra.forEach(column => {
            if (!extraColumnsConfiguration.find(c => c.key === column.key)) {
              extraColumnsConfiguration.push(column);
            }
          });
        }
        this.setState({extraColumnsConfiguration}, this.updateDocumentTypes);
      });
  };

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

  renderResultsItemActions = (resultItem) => {
    const {disabled} = this.props;
    return (
      <div className="cp-search-result-item-actions">
        <Icon
          type="info-circle-o"
          className={
            classNames(
              'cp-search-result-item-action',
              'cp-icon-larger'
            )
          }
          onClick={(e) => {
            if (!disabled) {
              e && e.stopPropagation();
              e && e.preventDefault();
              this.setPreview(resultItem);
            }
          }}
        />
        <OpenInToolAction
          file={resultItem.path}
          storageId={resultItem.parentId}
          className={
            classNames(
              'cp-search-result-item-action',
              'cp-icon-larger'
            )
          }
          style={{
            borderRadius: '0px',
            borderLeft: 'none',
            height: '100%'
          }}
        />
      </div>
    );
  };

  renderSearchResultItem = (resultItem) => {
    const {disabled} = this.props;
    const {extraColumnsConfiguration} = this.state;
    return (
      <a
        href={!disabled && resultItem.url ? `${PUBLIC_URL || ''}/#${resultItem.url}` : undefined}
        key={resultItem.elasticId}
        className={
          classNames(
            styles.resultItemContainer,
            'cp-panel-card',
            'cp-search-result-item',
            'cp-search-result-list-item',
            {
              disabled
            }
          )
        }
        onClick={this.navigate(resultItem)}
      >
        {this.renderResultsItemActions(resultItem)}
        <div
          id={`search-result-item-${resultItem.elasticId}`}
          className={styles.resultItem}
        >
          <DocumentListPresentation
            className={styles.title}
            document={resultItem}
            extraColumns={extraColumnsConfiguration}
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
  };

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
  };

  renderPreview = () => {
    const {preview} = this.state;
    if (!preview) {
      return null;
    }
    return (
      <PreviewModal
        preview={preview}
        onClose={this.closePreview}
      />
    );
  };

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
  };

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
  };

  stopResizing = (event) => {
    const {resizingColumn} = this.state;
    event && event.stopPropagation();
    if (resizingColumn) {
      this.setState({resizingColumn: undefined});
    }
  };

  initResizing = (event, key) => {
    const {resizingColumn} = this.state;
    event && event.stopPropagation();
    if (!resizingColumn) {
      this.setState({resizingColumn: key});
    }
  };

  renderTableRow = (resultItem, rowIndex) => {
    const {disabled} = this.props;
    const {columnWidths, resizingColumn} = this.state;
    if (!resultItem) {
      return null;
    }
    return (
      <a
        href={!disabled && resultItem.url ? `${PUBLIC_URL || ''}/#${resultItem.url}` : undefined}
        className={
          classNames(
            'cp-search-result-item',
            'cp-search-result-table-item',
            {
              disabled
            }
          )
        }
        style={{gridTemplate: this.getGridTemplate()}}
        key={rowIndex}
        onClick={this.navigate(resultItem)}
      >
        {this.columns.map(({key, className, renderFn}, index) => (
          [
            <div
              className={classNames('cp-search-results-table-cell', 'cp-search-result-item-main')}
              key={index}
              style={{width: columnWidths[key], minWidth: '0px'}}
            >
              {renderFn
                ? renderFn(resultItem[key], resultItem, this.setPreview)
                : (
                  <span
                    className={classNames('cp-ellipsis-text', className)}
                  >
                    {resultItem[key]}
                  </span>
                )
              }
            </div>,
            <div
              className={classNames(
                'cp-search-results-table-divider',
                {
                  active: resizingColumn === key
                }
              )}
            />
          ]
        ))
        }
      </a>
    );
  };

  renderTableHeader = () => {
    const {columnWidths, resizingColumn} = this.state;
    return (
      <div
        className={classNames(
          'cp-search-result-item',
          'cp-search-result-table-item',
          'cp-search-results-table-header'
        )}
        style={{gridTemplate: this.getGridTemplate(true)}}
        ref={header => (this.headerRef = header)}
      >
        {this.columns.map(({key, name}, index) => ([
          <div
            key={index}
            className={
              classNames(
                'cp-ellipsis-text',
                'cp-search-results-table-header-cell'
              )
            }
            ref={ref => (this.dividerRefs[key] = ref)}
            style={{width: columnWidths[key], minWidth: '0px'}}
          >
            {name}
          </div>,
          <div
            className={classNames(
              'cp-search-results-table-divider',
              {
                active: resizingColumn === key
              }
            )}
            onMouseDown={e => this.initResizing(e, key)}
          />
        ]))}
      </div>
    );
  };

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
        className={classNames(styles.tableContainer, 'cp-panel')}
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
  };

  renderResultsSpinner = () => {
    return (
      <div className={styles.containerSpinner}>
        <Spin />
      </div>
    );
  };

  render () {
    const {
      className,
      style,
      showResults,
      mode,
      loading
    } = this.props;
    const {preview} = this.state;
    if (!mode) {
      return null;
    }
    return (
      <div
        className={classNames(
          styles.container,
          {[styles.loading]: loading},
          className
        )}
        style={style}
        ref={preview => (this.resultsContainerRef = preview)}
      >
        {mode === PresentationModes.table ? this.renderResultsTable() : this.renderResultsList()}
        {showResults && preview && this.renderPreview()}
        {loading && this.renderResultsSpinner()}
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
  loading: PropTypes.bool,
  disabled: PropTypes.bool,
  onPageSizeChanged: PropTypes.func,
  onNavigate: PropTypes.func,
  pageSize: PropTypes.number,
  showResults: PropTypes.bool,
  style: PropTypes.object,
  onChangeDocumentType: PropTypes.func,
  onChangeBottomOffset: PropTypes.func,
  mode: PropTypes.oneOf([PresentationModes.list, PresentationModes.table]),
  documentTypes: PropTypes.array,
  extraColumns: PropTypes.array
};

SearchResults.defaultProps = {
  documents: [],
  pageSize: 20,
  documentTypes: []
};

export default inject('preferences')(observer(SearchResults));
export {DEFAULT_PAGE_SIZE} from './controls/infinite-scroll';
