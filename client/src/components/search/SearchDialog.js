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
import MetadataEntityLoad from '../../models/folderMetadata/MetadataEntityLoad';
import {
  getPipelineFileInfo,
  PipelineFileTypes
} from './utilities/getPipelineFileInfo';
import '../../staticStyles/Search.css';

const PAGE_SIZE = 50;
const INSTANT_SEARCH_DELAY = 1000;
const PREVIEW_AVAILABLE_DELAY = 500;

@localization.localizedComponent
@inject('searchEngine', 'preferences', 'pipelines')
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
    page: 0,
    hoveredIndex: null,
    previewAvailable: false,
    selectedGroupTypes: [],
    aggregates: null,
    totalPages: 0
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
      switch (item.type) {
        case SearchItemTypes.azFile:
        case SearchItemTypes.s3File:
        case SearchItemTypes.NFSFile:
          if (item.parentId) {
            const path = item.id;
            const parentFolder = path.split('/').slice(0, path.split('/').length - 1).join('/');
            if (parentFolder) {
              this.props.router.push(`/storage/${item.parentId}?path=${parentFolder}`);
            } else {
              this.props.router.push(`/storage/${item.parentId}`);
            }
            this.closeDialog();
          }
          break;
        case SearchItemTypes.azStorage:
        case SearchItemTypes.s3Bucket:
        case SearchItemTypes.NFSBucket:
          this.props.router.push(`/storage/${item.id}`);
          this.closeDialog();
          break;
        case SearchItemTypes.run:
          this.props.router.push(`/run/${item.id}`);
          this.closeDialog();
          break;
        case SearchItemTypes.pipeline:
          this.props.router.push(`/${item.id}`);
          this.closeDialog();
          break;
        case SearchItemTypes.tool:
          this.props.router.push(`/tool/${item.id}`);
          this.closeDialog();
          break;
        case SearchItemTypes.folder:
          this.props.router.push(`/folder/${item.id}`);
          this.closeDialog();
          break;
        case SearchItemTypes.configuration:
          const [id, configName] = item.id.split('-');
          this.props.router.push(`/configuration/${id}/${configName}`);
          this.closeDialog();
          break;
        case SearchItemTypes.metadataEntity:
          if (item.parentId) {
            const request = new MetadataEntityLoad(item.id);
            await request.fetch();
            if (request.loaded && request.value.classEntity && request.value.classEntity.name) {
              this.props.router.push(`/metadata/${item.parentId}/${request.value.classEntity.name}`);
              this.closeDialog();
            } else {
              this.props.router.push(`/metadataFolder/${item.parentId}/`);
              this.closeDialog();
            }
          }
          break;
        case SearchItemTypes.issue:
          if (item.entity) {
            const {entityClass, entityId} = item.entity;
            switch (entityClass.toLowerCase()) {
              case 'folder':
                this.props.router.push(`/folder/${entityId}/`);
                this.closeDialog();
                break;
              case 'pipeline':
                this.props.router.push(`/${entityId}/`);
                this.closeDialog();
                break;
              case 'tool':
                this.props.router.push(`/tool/${entityId}/`);
                this.closeDialog();
                break;
            }
          }
          break;
        case SearchItemTypes.pipelineCode:
          if (item.parentId && item.description && item.id) {
            const versions = this.props.pipelines.versionsForPipeline(item.parentId);
            await versions.fetch();
            let version = item.description;
            if (versions.loaded) {
              let [v] = (versions.value || []).filter(v => v.name === item.description);
              if (!v && versions.value.length > 0) {
                const [draft] = versions.value.filter(v => v.draft);
                if (draft) {
                  version = draft.name;
                } else {
                  version = versions.value[0].name;
                }
              }
            }
            const hide = message.loading('Navigating...', 0);
            const fileInfo = await getPipelineFileInfo(item.parentId, version, item.id);
            let url = `/${item.parentId}/${version}`;
            if (fileInfo) {
              switch (fileInfo.type) {
                case PipelineFileTypes.document:
                  url = `/${item.parentId}/${version}/documents`;
                  break;
                case PipelineFileTypes.source:
                  if (fileInfo.path) {
                    url = `/${item.parentId}/${version}/code&path=${fileInfo.path}`;
                  } else {
                    url = `/${item.parentId}/${version}/code`;
                  }
                  break;
              }
            }
            hide();
            this.props.router.push(url);
            this.closeDialog();
          } else if (item.parentId && item.description) {
            this.props.router.push(`/${item.parentId}/${item.description}`);
            this.closeDialog();
          } else if (item.parentId) {
            this.props.router.push(`/${item.parentId}`);
            this.closeDialog();
          } else {
            message.error('Cannot navigate to item', 3);
          }
          break;
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
              aggregates[this.searchTypesArray[i].key] = (aggregates[this.searchTypesArray[i].key] || 0) +
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
        totalPages: 0,
        page: 0
      });
      return;
    }
    const searchCriteria = this.state.searchString;
    this.setState({
      searching: true
    }, async () => {
      await this.props.searchEngine.send(searchCriteria, this.state.page, PAGE_SIZE, this.generateSearchTypes());
      if (this.state.searchString === searchCriteria) {
        if (this.props.searchEngine.loaded) {
          const totalPages = Math.ceil(this.props.searchEngine.value.totalHits / PAGE_SIZE);
          const results = (this.props.searchEngine.value.documents || []).map(d => d);
          const aggregates = this.processAggregates();
          this.setState({
            searching: false,
            searchResultsFor: searchCriteria,
            searchResults: results,
            hoveredIndex: null,
            previewAvailable: false,
            aggregates,
            totalPages
          });
        } else if (this.props.searchEngine.error) {
          message.error(this.props.searchEngine.error, 5);
          this.setState({
            searching: false
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
      await this.props.searchEngine.send(searchCriteria, this.state.page, PAGE_SIZE, this.generateSearchTypes());
      if (this.state.searchString === searchCriteria) {
        if (this.props.searchEngine.loaded) {
          const totalPages = Math.ceil(this.props.searchEngine.value.totalHits / PAGE_SIZE);
          const results = (this.props.searchEngine.value.documents || []).map(d => d);
          const aggregates = this.processAggregates();
          this.setState({
            aggregates,
            searching: false,
            searchResultsFor: searchCriteria,
            searchResults: results,
            hoveredIndex: null,
            previewAvailable: false,
            totalPages
          });
        } else if (this.props.searchEngine.error) {
          message.error(this.props.searchEngine.error, 5);
          this.setState({
            searching: false
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
    const classNames = [styles.searchResultItem];
    let additionalStyle = getStyle({
      ie: {backgroundColor: 'rgba(255, 255, 255, 0.75)'}
    });
    if (index === this.state.hoveredIndex) {
      classNames.push(styles.hovered);
      additionalStyle = getStyle({
        ie: {backgroundColor: 'white'}
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
          return resultItem.name;
        }
        default: return resultItem.name;
      }
    };
    return (
      <div
        id={`search-result-item-${index}`}
        key={index}
        style={additionalStyle}
        className={`${classNames.join(' ')}`}
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
      page: 0,
      selectedGroupTypes: types
    }, this.specifySearchGroups);
  };

  static wait = (seconds) => new Promise(resolve => setTimeout(resolve, seconds * 1000));

  loadMore = (e) => {
    const obj = e.target;
    if (obj && obj.scrollTop === (obj.scrollHeight - obj.offsetHeight) &&
      this.state.page < this.state.totalPages &&
      !this.state.searching) {
      this.setState({
        page: this.state.page + 1
      }, async () => {
        if (!this.state.searchString) {
          return;
        }
        const searchCriteria = this.state.searchString;
        this.setState({
          searching: true
        }, async () => {
          await SearchDialog.wait(1);
          await this.props.searchEngine.send(searchCriteria, this.state.page, PAGE_SIZE, this.generateSearchTypes());
          if (this.state.searchString === searchCriteria) {
            if (this.props.searchEngine.loaded) {
              const totalPages = Math.ceil(this.props.searchEngine.value.totalHits / PAGE_SIZE);
              const results = (this.props.searchEngine.value.documents || []).map(d => d);
              this.setState({
                searching: false,
                searchResults: [...this.state.searchResults, ...results],
                totalPages
              });
            } else if (this.props.searchEngine.error) {
              message.error(this.props.searchEngine.error, 5);
              this.setState({
                searching: false
              });
            }
          } else {
            this.setState({
              searching: false
            });
          }
        });
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
            In order to search for any of these special characters, they will need to be escaped with <code>\</code>
          </span>
        </Row>
      </div>
    );
  };

  render () {
    const {preferences} = this.props;
    if (preferences.loaded && !preferences.searchEnabled) {
      return null;
    }
    const searchFormClassNames = [styles.searchForm];
    if (this.state.searchResults.length) {
      searchFormClassNames.push(styles.resultsAvailable);
    }
    if (this.state.previewAvailable) {
      searchFormClassNames.push(styles.previewAvailable);
    }
    const typesFormClassNames = [styles.typesForm];
    if (this.state.searchResults.length) {
      typesFormClassNames.push(styles.resultsAvailable);
    }
    const hintContainerClassNames = [styles.hintContainer];
    if (this.state.searchResults.length) {
      hintContainerClassNames.push(styles.resultsAvailable);
    }
    if (this.state.previewAvailable) {
      hintContainerClassNames.push(styles.previewAvailable);
    }
    let hintsTooltipPlacement;
    if (this.state.previewAvailable && this.state.searchResults.length) {
      hintsTooltipPlacement = 'bottomRight';
    } else if (this.state.searchResults.length) {
      hintsTooltipPlacement = 'bottom';
    }
    const previewClassNames = [styles.preview];
    if (!this.state.previewAvailable) {
      previewClassNames.push(styles.notAvailable);
    }
    return (
      <div className={`${styles.searchContainer} ${this.state.visible ? styles.visible : ''}`}>
        <div
          className={`${styles.searchBackground} ${this.state.visible ? styles.visible : ''}`}
          style={
            this.state.visible
              ? getStyle({ie: {opacity: 0.75}})
              : {}
          }
          onClick={this.closeDialog}>
          {'\u00A0'}
        </div>
        <div
          className={`${previewClassNames.join(' ')}`}
          onClick={this.state.previewAvailable ? undefined : this.closeDialog}>
          {this.renderPreview()}
        </div>
        <div className={`${hintContainerClassNames.join(' ')}`}>
          <Tooltip
            overlayClassName="search-hints-overlay"
            placement={hintsTooltipPlacement}
            title={this.renderHints()}>
            <div className={styles.hintIconContainer}>
              <Icon type="question" />
            </div>
          </Tooltip>
        </div>
        <div className={`${typesFormClassNames.join(' ')}`} onClick={this.closeDialog}>
          <div style={{display: 'flex', overflowX: 'auto'}}>
            {
              this.searchTypesArray.map((type, index) => {
                const disabled = this.state.aggregates && !this.state.aggregates[type.key];
                const active = !disabled && this.state.selectedGroupTypes.indexOf(type.key) >= 0;
                return (
                  <div
                    className={`${styles.typeButton} ${disabled ? styles.disabled : ''} ${active ? styles.active : ''}`}
                    onClick={this.enableDisableSearchGroup(type.key, disabled)}
                    key={index}>
                    <Icon type={type.icon} />
                    <span className={styles.typeTitle}>
                      {type.title(this.localizedString)(this.state.aggregates && this.state.aggregates[type.key])}
                    </span>
                  </div>
                );
              })
            }
          </div>
        </div>
        <Row type="flex" className={`${searchFormClassNames.join(' ')}`} align="middle">
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
            !this.state.searching && this.state.searchResultsFor && !this.state.searchResults.length &&
            <Row type="flex" className={styles.searchingInProgressContainer} align="middle" justify="center">
              <span>Nothing found</span>
            </Row>
          }
          {
            this.state.searchResults.length &&
            <div
              onScroll={this.loadMore}
              id="search-results"
              className={styles.searchResults}
              onClick={this.closeDialog}>
              {
                this.state.searchResults.map(this.renderSearchResultItem)
              }
            </div>
          }
          {
            this.state.searching &&
            <Row type="flex" className={styles.searchingInProgressContainer} align="middle" justify="center">
              <Icon type="loading" />
            </Row>
          }
        </Row>
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
    if (this.props.blockInput) {
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
