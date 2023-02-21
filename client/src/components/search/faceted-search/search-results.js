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
import {Alert, Checkbox, Icon, Spin} from 'antd';
import PreviewModal from '../preview/preview-modal';
import {InfiniteScroll, PresentationModes} from '../faceted-search/controls';
import DocumentListPresentation from './document-presentation/list';
import {ExcludedSortingKeys} from './utilities';
import {PUBLIC_URL} from '../../../config';
import styles from './search-results.css';
import OpenInToolAction from '../../special/file-actions/open-in-tool';
import compareArrays from '../../../utils/compareArrays';
import * as elasticItemUtilities from '../utilities/elastic-item-utilities';

const RESULT_ITEM_HEIGHT = 46;
const TABLE_ROW_HEIGHT = 32;
const TABLE_HEADER_HEIGHT = 28;
const RESULT_ITEM_MARGIN = 2;
const PREVIEW_TIMEOUT = 1000;

function cellStringWithDivider (column) {
  if (!column || !column.key) {
    return '.';
  }
  return `${column.key.replace(/\s/g, '___')} .`;
}

function getColumnNames (columns = []) {
  return columns.map(column => column.key);
}

class SearchResults extends React.Component {
  state = {
    resultsAreaHeight: undefined,
    preview: undefined,
    resizingColumn: undefined,
    columnWidths: {}
  };

  dividerRefs = [];
  headerRef = null;
  resultsContainerRef = null;
  tableWidth = undefined;
  animationFrame;
  infiniteScroll;
  currentDocumentId;

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevState.columnWidths !== this.state.columnWidths ||
      prevState.resizingColumn !== this.state.resizingColumn ||
      !compareArrays(getColumnNames(this.props.columns), getColumnNames(prevProps.columns)) ||
      prevProps.selectedItems !== this.props.selectedItems
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
  }

  componentWillUnmount () {
    if (this.animationFrame) {
      cancelAnimationFrame(this.animationFrame);
    }
    window.removeEventListener('mousemove', this.onResize);
    window.removeEventListener('mouseup', this.stopResizing);
    window.removeEventListener('keydown', this.onKeyDown);
  }

  setCurrentDocument = (currentDocumentId) => {
    this.currentDocumentId = currentDocumentId;
  }

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
        {this.renderRowSelectionCheckbox(resultItem)}
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
    const {disabled, extraColumns} = this.props;
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
            extraColumns={extraColumns}
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
      pageSize,
      resetPositionToken
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
            reportCurrentDocument={this.setCurrentDocument}
            initialTopDocument={this.currentDocumentId}
            resetPositionToken={resetPositionToken}
            headerHeight={0}
          />
        </div>
      </div>
    );
  };

  getGridTemplate = (headerTemplate) => {
    const {columns = []} = this.props;
    const {columnWidths} = this.state;
    const rowHeight = headerTemplate
      ? TABLE_HEADER_HEIGHT
      : TABLE_ROW_HEIGHT;
    const cellDefault = '100px';
    const divider = '4px';
    const columnString = `'${columns
      .map(cellStringWithDivider).join(' ')}' ${rowHeight}px /`;
    const widthString = `${columns
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

  itemSelected = (item) => {
    const {selectedItems = []} = this.props;
    const findItemFn = elasticItemUtilities.filterMatchingItemsFn(item);

    return this.itemSelectionAvailable(item) &&
      selectedItems.length > 0 &&
      selectedItems.some(findItemFn);
  }

  itemSelectionAvailable = (item) => {
    const {
      dataStorageSharingEnabled,
      notDownloadableStorages,
      preferences
    } = this.props;

    return (dataStorageSharingEnabled && elasticItemUtilities.itemSharingAvailable(item)) ||
      elasticItemUtilities.itemIsDownloadable(item, preferences, notDownloadableStorages);
  };

  onRowSelectionChange = (item, event) => {
    const {onSelectItem, onDeselectItem} = this.props;
    if (event.target.checked) {
      onSelectItem && onSelectItem(item);
    } else if (!event.target.checked) {
      onDeselectItem && onDeselectItem(item);
    }
    if (this.infiniteScroll) {
      this.infiniteScroll.forceUpdate();
    }
  };

  renderRowSelectionCheckbox = (item) => {
    if (!this.itemSelectionAvailable(item)) {
      return null;
    }
    const handleClick = e => e.stopPropagation();
    return (
      <div
        style={{padding: '10px 5px'}}
        onClick={(e) => {
          e.stopPropagation();
          e.preventDefault();
        }}
      >
        <Checkbox
          checked={this.itemSelected(item)}
          onChange={e => this.onRowSelectionChange(item, e)}
          onClick={handleClick}
        />
      </div>
    );
  };

  renderTableRow = (resultItem, rowIndex) => {
    const {disabled, columns = []} = this.props;
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
        {columns.map(({key, className, renderFn}, index) => (
          [
            <div
              className={classNames('cp-search-results-table-cell', 'cp-search-result-item-main')}
              key={index}
              style={{width: columnWidths[key], minWidth: '0px'}}
            >
              {renderFn
                ? renderFn(
                  resultItem[key],
                  resultItem,
                  this.setPreview,
                  this.renderRowSelectionCheckbox
                )
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

  onHeaderCellClick = (key, event) => {
    const {onChangeSortingOrder, pending} = this.props;
    event && event.stopPropagation();
    if (ExcludedSortingKeys.includes(key) || pending) {
      return;
    }
    return onChangeSortingOrder && onChangeSortingOrder(key, true);
  };

  renderTableHeader = () => {
    const {columnWidths, resizingColumn} = this.state;
    const {sortingOrder, columns = []} = this.props;
    const renderSortingIcon = (key) => {
      const currentIndex = sortingOrder
        .findIndex(sorting => sorting.field === key);
      const currentSorting = sortingOrder[currentIndex];
      if (!currentSorting) {
        return null;
      }
      return (
        <span
          className={styles.sortingContainer}
        >
          <Icon
            className={styles.sortingIcon}
            type={
              currentSorting.asc
                ? 'caret-up'
                : 'caret-down'
            }
          />
          {sortingOrder.length > 1 ? (
            <sup className={styles.sortingNumber}>
              {currentIndex + 1}
            </sup>
          ) : null}
        </span>
      );
    };
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
        {columns.map(({key, name}, index) => ([
          <div
            key={index}
            className={
              classNames(
                'cp-ellipsis-text',
                'cp-search-results-table-header-cell'
              )
            }
            ref={ref => (this.dividerRefs[key] = ref)}
            style={{
              width: columnWidths[key],
              minWidth: '0px',
              cursor: ExcludedSortingKeys.includes(key)
                ? 'default'
                : 'pointer'
            }}
            onClick={(event) => this.onHeaderCellClick(key, event)}
          >
            {renderSortingIcon(key)}
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
      showResults,
      resetPositionToken
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
          headerHeight={TABLE_HEADER_HEIGHT}
          onInitialized={this.onInitializeInfiniteScroll}
          reportCurrentDocument={this.setCurrentDocument}
          initialTopDocument={this.currentDocumentId}
          resetPositionToken={resetPositionToken}
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
  resetPositionToken: PropTypes.string,
  onLoadData: PropTypes.func,
  loading: PropTypes.bool,
  disabled: PropTypes.bool,
  onPageSizeChanged: PropTypes.func,
  onNavigate: PropTypes.func,
  onSelectItem: PropTypes.func,
  onDeselectItem: PropTypes.func,
  selectedItems: PropTypes.array,
  dataStorageSharingEnabled: PropTypes.bool,
  notDownloadableStorages: PropTypes.arrayOf(PropTypes.number),
  pageSize: PropTypes.number,
  showResults: PropTypes.bool,
  style: PropTypes.object,
  onChangeDocumentType: PropTypes.func,
  onChangeBottomOffset: PropTypes.func,
  mode: PropTypes.oneOf([PresentationModes.list, PresentationModes.table]),
  columns: PropTypes.array,
  extraColumns: PropTypes.array,
  onChangeSortingOrder: PropTypes.func,
  sortingOrder: PropTypes.arrayOf(PropTypes.shape({
    field: PropTypes.string,
    order: PropTypes.string
  }))
};

SearchResults.defaultProps = {
  documents: [],
  pageSize: 20
};

export default inject('preferences')(observer(SearchResults));
export {DEFAULT_PAGE_SIZE} from './controls/infinite-scroll';
