/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';
import {observable} from 'mobx';
import classNames from 'classnames';
import {
  Icon,
  Input,
  message,
  Tooltip,
  Row
} from 'antd';
import Preview from './preview';
import {PreviewIcons} from './preview/previewIcons';
import {SearchItemTypes} from '../../models/search';
import {SearchGroupTypes} from './searchGroupTypes';
import localization from '../../utils/localization';
import styles from './search.css';
import getStyle from '../../utils/browserDependentStyle';
import {facetedQueryString} from './faceted-search/utilities';
import {DocumentTypeFilterName} from './faceted-search/filter';
import getItemUrl from './faceted-search/utilities/get-item-url';
import '../../staticStyles/Search.css';

const PAGE_SIZE = 50;
const INSTANT_SEARCH_DELAY = 1000;
const PREVIEW_AVAILABLE_DELAY = 500;

class SearchDialogBlocker {
  @observable blocked = false;
}

const searchDialogBlocker = new SearchDialogBlocker();

export {searchDialogBlocker};

@localization.localizedComponent
@inject('searchEngine', 'preferences')
@observer
export default class SearchDialog extends localization.LocalizedReactComponent {
  static propTypes = {
    onInitialized: PropTypes.func,
    onVisibilityChanged: PropTypes.func,
    blockInput: PropTypes.bool
  };

  state = {
    visible: false,
    searchString: null,
    searching: false,
    searchResults: [],
    searchResultsFor: null,
    hasMore: false,
    hoveredIndex: null,
    previewAvailable: false,
    selectedGroupTypes: [],
    aggregates: null
  };

  inputControl;

  @observable delayedSearch;

  get searchTypesArray () {
    const result = [];
    for (let key in SearchGroupTypes) {
      if (SearchGroupTypes.hasOwnProperty(key)) {
        result.push({...SearchGroupTypes[key], key});
      }
    }
    return result;
  }

  initializeInputControl = (input) => {
    if (input && input.input) {
      this.inputControl = input.input;
    }
  };

  becomeVisible = () => {
    if (this.inputControl && this.inputControl.focus) {
      setTimeout(() => { this.inputControl.focus(); }, 125);
    }
  };

  onSearchChanged = (event) => {
    this.setState({
      searchString: event.target.value
    }, event.target.value ? this.performSearchDelayed : this.performSearch);
  };

  onPerformSearch = (text) => {
    this.setState({
      searchString: text
    }, this.performSearch);
  };

  performSearchDelayed = () => {
    if (this.delayedSearch) {
      clearTimeout(this.delayedSearch);
    }
    this.delayedSearch = setTimeout(this.performSearch, INSTANT_SEARCH_DELAY);
  };

  navigate = (itemIndex) => async (e) => {
    e.preventDefault();
    e.stopPropagation();
    if (!this.props.router) {
      return false;
    }
    if (this.state.searchResults.length > itemIndex) {
      const item = this.state.searchResults[itemIndex];
      const url = await getItemUrl(item);
      if (url) {
        this.props.router.push(url);
        this.closeDialog();
      } else {
        message.error('Cannot navigate to item', 3);
      }
    }
    return false;
  };

  generateSearchTypes = () => {
    const result = [];
    for (let i = 0; i < this.state.selectedGroupTypes.length; i++) {
      const group = this.state.selectedGroupTypes[i];
      result.push(...SearchGroupTypes[group].types);
    }
    return result;
  };

  processAggregates = () => {
    const aggregates = {};
    if (this.props.searchEngine.loaded && this.props.searchEngine.value.aggregates) {
      const resultAggregates = this.props.searchEngine.value.aggregates;
      for (let key in resultAggregates) {
        if (resultAggregates.hasOwnProperty(key)) {
          for (let i = 0; i < this.searchTypesArray.length; i++) {
            if (this.searchTypesArray[i].types.indexOf(key) >= 0) {
              aggregates[this.searchTypesArray[i].key] =
                (aggregates[this.searchTypesArray[i].key] || 0) +
                resultAggregates[key];
            }
          }
        }
      }
    }
    return aggregates;
  };

  performSearch = (force = false) => {
    this.delayedSearch && clearTimeout(this.delayedSearch);
    if (!force &&
      this.state.searchString &&
      this.state.searchResultsFor &&
      this.state.searchResultsFor.toLowerCase() === this.state.searchString.toLowerCase()) {
      return;
    }
    if (!this.state.searchString) {
      this.setState({
        searchResults: [],
        searchResultsFor: this.state.searchString,
        hoveredIndex: null,
        previewAvailable: false,
        aggregates: null,
        selectedGroupTypes: [],
        hasMore: false
      });
      return;
    }
    const searchCriteria = this.state.searchString;
    this.setState({
      searching: true
    }, async () => {
      await this.props.searchEngine.send(
        searchCriteria,
        undefined,
        PAGE_SIZE,
        this.generateSearchTypes()
      );
      if (this.state.searchString === searchCriteria) {
        if (this.props.searchEngine.loaded) {
          const results = (this.props.searchEngine.value.documents || []).map(d => d);
          const aggregates = this.processAggregates();
          this.setState({
            searching: false,
            searchResultsFor: searchCriteria,
            searchResults: results,
            hoveredIndex: null,
            previewAvailable: false,
            aggregates,
            hasMore: results.length >= PAGE_SIZE
          });
        } else if (this.props.searchEngine.error) {
          message.error(this.props.searchEngine.error, 5);
          this.setState({
            searching: false,
            hasMore: false
          });
        }
      } else {
        this.setState({
          searching: false
        });
      }
    });
  };

  specifySearchGroups = () => {
    if (!this.state.searchString) {
      return;
    }
    const searchCriteria = this.state.searchString;
    this.setState({
      searching: true
    }, async () => {
      await this.props.searchEngine.send(
        searchCriteria,
        undefined,
        PAGE_SIZE,
        this.generateSearchTypes()
      );
      if (this.state.searchString === searchCriteria) {
        if (this.props.searchEngine.loaded) {
          const results = (this.props.searchEngine.value.documents || []).map(d => d);
          const aggregates = this.processAggregates();
          this.setState({
            aggregates,
            searching: false,
            searchResultsFor: searchCriteria,
            searchResults: results,
            hoveredIndex: null,
            previewAvailable: false,
            hasMore: results.length >= PAGE_SIZE
          });
        } else if (this.props.searchEngine.error) {
          message.error(this.props.searchEngine.error, 5);
          this.setState({
            searching: false,
            hasMore: false
          });
        }
      } else {
        this.setState({
          searching: false
        });
      }
    });
  };

  renderIcon = (resultItem) => {
    if (PreviewIcons[resultItem.type]) {
      return (
        <Icon
          className={styles.searchResultItemIcon}
          type={PreviewIcons[resultItem.type]} />
      );
    }
    return null;
  };

  previewAvailableDelay;
  previewAvailableTransition = false;

  makePreviewAvailable = () => {
    if (this.previewAvailableDelay) {
      clearTimeout(this.previewAvailableDelay);
    }
    if (this.state.previewAvailable) {
      return;
    }
    this.previewAvailableDelay = setTimeout(() => {
      this.previewAvailableDelay = null;
      this.previewAvailableTransition = true;
      if (this.state.hoveredIndex !== null) {
        this.setState({
          previewAvailable: true
        });
      }
    }, PREVIEW_AVAILABLE_DELAY);
  };

  makePreviewUnAvailable = () => {
    if (this.previewAvailableDelay) {
      clearTimeout(this.previewAvailableDelay);
    }
    if (!this.state.previewAvailable) {
      return;
    }
    this.previewAvailableDelay = setTimeout(() => {
      this.previewAvailableDelay = null;
      this.previewAvailableTransition = true;
      this.setState({
        previewAvailable: false
      });
    }, PREVIEW_AVAILABLE_DELAY);
  };

  blockMouseEvents;

  onHover = (index) => (e) => {
    if (e && this.blockMouseEvents) {
      return;
    } else if (!e) {
      if (this.blockMouseEvents) {
        clearTimeout(this.blockMouseEvents);
      }
      this.blockMouseEvents = setTimeout(() => {
        this.blockMouseEvents = null;
      }, 500);
    }
    this.previewAvailableTransition = false;
    this.setState({
      hoveredIndex: index
    }, this.makePreviewAvailable);
  };

  onUnHover = (index) => () => {
    if (this.previewAvailableTransition || this.state.previewAvailable) {
      return;
    }
    if (this.state.hoveredIndex === index) {
      this.setState({
        hoveredIndex: null
      }, this.makePreviewUnAvailable);
    }
  };

  renderSearchResultItem = (resultItem, index) => {
    let additionalStyle = getStyle({
      ie: {opacity: 0.75}
    });
    if (index === this.state.hoveredIndex) {
      additionalStyle = getStyle({
        ie: {opacity: 1}
      });
    }
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
        default: return resultItem.name;
      }
    };
    return (
      <div
        id={`search-result-item-${index}`}
        key={index}
        style={additionalStyle}
        className={
          classNames(
            styles.searchResultItem,
            'cp-fast-search-result-item',
            'cp-table-element',
            {
              'cp-table-element-hover': index === this.state.hoveredIndex
            }
          )
        }
        onMouseOver={this.onHover(index)}
        onMouseEnter={this.onHover(index)}
        onMouseLeave={this.onUnHover(index)}
        onClick={this.navigate(index)}>
        <Row
          type="flex"
          align="middle"
          onMouseEnter={this.onHover(index)}
        >
          <div style={{display: 'inline-block'}}>
            {this.renderIcon(resultItem)}
          </div>
          <span className={styles.title}>
            {renderName()}
          </span>
        </Row>
      </div>
    );
  };

  onKeyEnter = (event) => {
    if (event.keyCode === 27 && this.state.searchString) {
      event.preventDefault();
      event.stopPropagation();
      this.onPerformSearch('');
      return false;
    } else if (event.keyCode === 13) {

    }
  };

  renderPreview = () => {
    if (this.state.hoveredIndex !== null &&
      this.state.searchResults.length > this.state.hoveredIndex) {
      return (
        <Preview
          item={this.state.searchResults[this.state.hoveredIndex]} />
      );
    }
    return null;
  };

  enableDisableSearchGroup = (group, isDisabled) => (e) => {
    if (e) {
      e.stopPropagation();
      e.preventDefault();
    }
    if (isDisabled) {
      return;
    }
    const types = this.state.selectedGroupTypes;
    const index = types.indexOf(group);
    if (index >= 0) {
      types.splice(index, 1);
    } else {
      types.push(group);
    }
    if (this.inputControl && this.inputControl.focus) {
      this.inputControl.focus();
    }
    this.setState({
      hasMore: false,
      selectedGroupTypes: types
    }, this.specifySearchGroups);
  };

  static wait = (seconds) => new Promise(resolve => setTimeout(resolve, seconds * 1000));

  loadMore = (e) => {
    const obj = e.target;
    if (
      obj &&
      obj.scrollTop === (obj.scrollHeight - obj.offsetHeight) &&
      this.state.hasMore &&
      !this.state.searching
    ) {
      if (!this.state.searchString) {
        return;
      }
      const searchCriteria = this.state.searchString;
      this.setState({
        searching: true
      }, async () => {
        await SearchDialog.wait(1);
        const {searchResults = []} = this.state;
        const lastResult = searchResults.length > 0
          ? searchResults[searchResults.length - 1]
          : undefined;
        await this.props.searchEngine.send(
          searchCriteria,
          lastResult
            ? {docId: lastResult.elasticId, docScore: lastResult.score, scrollingBackward: false}
            : undefined,
          PAGE_SIZE,
          this.generateSearchTypes()
        );
        if (this.state.searchString === searchCriteria) {
          if (this.props.searchEngine.loaded) {
            const results = (this.props.searchEngine.value.documents || []).map(d => d);
            this.setState({
              searching: false,
              searchResults: [...this.state.searchResults, ...results],
              hasMore: results.length >= PAGE_SIZE
            });
          } else if (this.props.searchEngine.error) {
            message.error(this.props.searchEngine.error, 5);
            this.setState({
              searching: false,
              hasMore: false
            });
          }
        } else {
          this.setState({
            searching: false
          });
        }
      });
    }
  };

  renderHints = () => {
    return (
      <div className={styles.hints}>
        <Row
          style={{
            borderBottom: '1px solid rgba(255, 255, 255, 0.1)',
            paddingBottom: 10,
            marginBottom: 10
          }}
        >
          <span style={{fontSize: 'large'}}>
            The query string supports the following special characters:
          </span>
        </Row>
        <Row>
          <code>+</code> signifies AND operation
        </Row>
        <Row>
          <code>|</code> signifies OR operation
        </Row>
        <Row>
          <code>-</code> negates a single token
        </Row>
        <Row>
          <code>"</code> wraps a number of tokens to signify a phrase for searching
        </Row>
        <Row>
          <code>*</code> at the end of a term signifies a prefix query
        </Row>
        <Row>
          <code>(</code> and <code>)</code> signify precedence
        </Row>
        <Row>
          <code>~N</code> after a word signifies edit distance (fuzziness)
        </Row>
        <Row>
          <code>~N</code> after a phrase signifies slop amount
        </Row>
        <Row
          style={{
            borderTop: '1px solid rgba(255, 255, 255, 0.1)',
            paddingTop: 10,
            marginTop: 10
          }}>
          <span style={{fontSize: 'larger'}}>
            {/* eslint-disable-next-line */}
            In order to search for any of these special characters, they will need to be escaped with <code>\</code>
          </span>
        </Row>
      </div>
    );
  };

  navigateToAdvancedFilter = (e) => {
    e.stopPropagation();
    e.preventDefault();
    const {searchString, selectedGroupTypes} = this.state;
    const docTypes = (selectedGroupTypes || [])
      .map(group => SearchGroupTypes.hasOwnProperty(group) ? SearchGroupTypes[group].types : [])
      .reduce((r, c) => ([...r, ...c]), []);
    let queryString = facetedQueryString.build(
      searchString,
      {[DocumentTypeFilterName]: docTypes}
    );
    if (queryString) {
      queryString = `?${queryString}`;
    }
    this.props.router.push(`/search/advanced${queryString}`);
    this.closeDialog();
  };

  render () {
    const {preferences} = this.props;
    if (preferences.loaded && !preferences.searchEnabled) {
      return null;
    }
    let hintsTooltipPlacement;
    if (this.state.previewAvailable && this.state.searchResults.length) {
      hintsTooltipPlacement = 'bottomRight';
    } else if (this.state.searchResults.length) {
      hintsTooltipPlacement = 'bottom';
    }
    return (
      <div
        className={
          classNames(
            styles.searchContainer,
            {
              [styles.visible]: this.state.visible
            }
          )
        }
      >
        <div
          className={
            classNames(
              styles.searchBackground,
              {
                [styles.visible]: this.state.visible
              }
            )
          }
          style={
            this.state.visible
              ? getStyle({ie: {opacity: 0.75}})
              : {}
          }
          onClick={this.closeDialog}
        >
          {'\u00A0'}
        </div>
        <div
          className={classNames(
            styles.preview,
            'cp-search-preview',
            {
              [styles.notAvailable]: !this.state.previewAvailable
            }
          )}
          onClick={this.state.previewAvailable ? undefined : this.closeDialog}
        >
          {this.renderPreview()}
        </div>
        <div
          className={
            classNames(
              styles.hintContainer,
              {
                [styles.resultsAvailable]: this.state.searchResults.length,
                [styles.previewAvailable]: this.state.previewAvailable
              }
            )
          }
        >
          <Tooltip
            overlayClassName="search-hints-overlay"
            placement={hintsTooltipPlacement}
            title={this.renderHints()}>
            <div
              className={
                classNames(
                  styles.hintIconContainer,
                  'cp-search-type-button'
                )
              }
            >
              <Icon type="question" />
            </div>
          </Tooltip>
        </div>
        <div
          className={
            classNames(
              styles.typesForm,
              {
                [styles.resultsAvailable]: this.state.searchResults.length
              }
            )
          }
          onClick={this.closeDialog}
        >
          <div style={{display: 'flex', overflowX: 'auto'}}>
            {
              this.searchTypesArray.map((type, index) => {
                const disabled = this.state.aggregates && !this.state.aggregates[type.key];
                const selected = !disabled && this.state.selectedGroupTypes.indexOf(type.key) >= 0;
                return (
                  <div
                    className={
                      classNames(
                        styles.typeButton,
                        'cp-search-type-button',
                        {
                          'disabled': disabled,
                          'selected': selected
                        }
                      )
                    }
                    onClick={this.enableDisableSearchGroup(type.key, disabled)}
                    key={index}>
                    <Icon type={type.icon} />
                    <span className={styles.typeTitle}>
                      {
                        type.title(this.localizedString)(
                          this.state.aggregates && this.state.aggregates[type.key]
                        )
                      }
                    </span>
                  </div>
                );
              })
            }
          </div>
        </div>
        <Row
          type="flex"
          className={
            classNames(
              styles.searchForm,
              {
                [styles.resultsAvailable]: this.state.searchResults.length,
                [styles.previewAvailable]: this.state.previewAvailable
              }
            )
          }
          align="middle"
        >
          <Input.Search
            className={styles.searchInput}
            placeholder={this.props.preferences.loaded
              ? `${this.props.preferences.deploymentName} search`
              : 'Cloud Platform search'
            }
            ref={this.initializeInputControl}
            value={this.state.searchString}
            onChange={this.onSearchChanged}
            onSearch={this.onPerformSearch}
            onKeyDown={this.onKeyEnter}
            style={{width: '100%'}} />
          {
            !this.state.searching &&
            this.state.searchResultsFor &&
            !this.state.searchResults.length &&
            (
              <Row
                type="flex"
                className={
                  classNames(
                    styles.searchingInProgressContainer,
                    'cp-text-not-important'
                  )
                }
                align="middle"
                justify="center"
              >
                <span>Nothing found</span>
              </Row>
            )
          }
          <div
            onScroll={this.loadMore}
            id="search-results"
            className={
              classNames(
                styles.searchResults,
                {
                  [styles.resultsAvailable]: this.state.searchResults.length
                }
              )
            }
            onClick={this.closeDialog}>
            {
              this.state.searchResults.map(this.renderSearchResultItem)
            }
          </div>
          {
            this.state.searching &&
            (
              <Row
                type="flex"
                className={
                  classNames(
                    styles.searchingInProgressContainer,
                    'cp-text-not-important'
                  )
                }
                align="middle"
                justify="center"
              >
                <Icon type="loading" />
              </Row>
            )
          }
        </Row>
        <div
          className={
            classNames(
              styles.advanced,
              {
                [styles.resultsAvailable]: this.state.searchResults.length,
                [styles.previewAvailable]: this.state.previewAvailable
              },
              'cp-search-type-button'
            )
          }
          onClick={this.navigateToAdvancedFilter}
        >
          <Icon className={styles.icon} type="filter" />
          <span className={styles.buttonText}>Advanced search</span>
        </div>
      </div>
    );
  }

  openDialog = () => {
    const {preferences} = this.props;
    if (preferences.loaded && !preferences.searchEnabled) {
      return;
    }
    this.setState({
      visible: true
    }, () => {
      this.becomeVisible();
      this.props.onVisibilityChanged && this.props.onVisibilityChanged(this.state.visible);
    });
  };

  closeDialog = () => {
    this.setState({
      visible: false
    }, () => {
      this.props.onVisibilityChanged && this.props.onVisibilityChanged(this.state.visible);
    });
  };

  handleKeyPress = (e) => {
    if (this.props.blockInput || searchDialogBlocker.blocked) {
      return;
    }
    const modals = Array.from(document.getElementsByClassName('ant-modal-mask'));
    if (modals && modals.filter(m => m.className === 'ant-modal-mask').length) {
      return;
    }
    const {preferences} = this.props;
    if (preferences.loaded && !preferences.searchEnabled) {
      return;
    }
    if (e.keyCode === 114 || ((e.ctrlKey || e.metaKey) && e.keyCode === 70)) {
      e.preventDefault();
      if (!this.state.visible) {
        this.openDialog();
      }
      return;
    } else if (e.keyCode === 27) {
      if (this.state.visible) {
        this.closeDialog();
      }
      return;
    }
    let move = 0;
    let initial = 0;
    if (e.keyCode === 38) {
      // 'Up' key
      move = -1;
    } else if (e.keyCode === 40) {
      // 'Down' key
      move = 1;
      initial = -1;
    }
    if (move && this.state.searchResults && this.state.searchResults.length) {
      const currentIndex = (
        (this.state.hoveredIndex === null ? initial : this.state.hoveredIndex) +
        this.state.searchResults.length +
        move
      ) % (this.state.searchResults.length);
      this.onHover(currentIndex)();
      e.preventDefault();
      e.stopPropagation();
      const item = document.getElementById(`search-result-item-${currentIndex}`);
      if (item) {
        item.scrollIntoView({behavior: 'smooth'});
      }
      return false;
    }
  };

  componentDidMount () {
    const {preferences} = this.props;
    preferences
      .fetchIfNeededOrWait()
      .then(
        () => {
          window.addEventListener('keydown', this.handleKeyPress);
        }
      );
    this.props.onInitialized && this.props.onInitialized(this);
  }

  componentWillUnmount () {
    window.removeEventListener('keydown', this.handleKeyPress);
    this.props.onInitialized && this.props.onInitialized(null);
  }
}
