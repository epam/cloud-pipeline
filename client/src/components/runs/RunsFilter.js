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
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import LoadingView from '../special/LoadingView';
import {Alert, Button, Card, Dropdown, Icon, Menu, Modal, Row, Table} from 'antd';
import RunTable from './RunTable';
import AdaptedLink from '../special/AdaptedLink';
import SessionStorageWrapper from '../special/SessionStorageWrapper';
import styles from './AllRuns.css';
import Filter from '../../utils/filter/filter';
import Composer from '../../utils/filter/composer';
import PipelineRunSearch from '../../models/pipelines/PipelineRunSearch';
import PipelineRunSearchKeywords from '../../models/pipelines/PipelineRunSearchKeywords';
import pipelines from '../../models/pipelines/Pipelines';
import FilterInput from '../../utils/filter/inputControl/FilterInput';
import SaveFilterForm from './SaveFilterForm';

const pageSize = 20;

@inject(() => ({
  keywords: PipelineRunSearchKeywords,
  pipelines,
  runFilter: new PipelineRunSearch()
}))
@observer
export default class RunsFilter extends React.Component {

  filter = new Filter();
  composer = new Composer();

  state = {
    error: null,
    filter: null,
    displayError: false,
    autocomplete: null,
    saveFilter: false,
    appliedFilter: null,
    currentPage: 1,
    savedFiltersDropDownVisible: false
  };

  _filter = (page, filter) => {
    this.setState({loading: true}, async () => {
      await this.props.runFilter.send({
        filterExpression: filter,
        page,
        pageSize: pageSize,
        timezoneOffsetInMinutes: -(new Date()).getTimezoneOffset()
      });
      if (this.props.runFilter.error) {
        this.setState({
          runs: [],
          error: this.props.runFilter.error,
          displayError: true,
          loading: false,
          appliedFilter: null,
          currentPage: 1
        });
      } else {
        this.setState({
          error: null,
          displayError: false,
          loading: false,
          runs: this.props.runFilter.value.elements,
          total: this.props.runFilter.value.totalCount,
          appliedFilter: this.state.filter.toStringExpression(),
          currentPage: page
        });
      }
    });
  };

  onFilter = () => {
    if (this.state.filter && this.state.filter.toStringExpression() !== this.state.appliedFilter) {
      this._filter(1, this.state.filter);
    } else if (this.state.error) {
      this.setState({
        displayError: true,
        loading: false
      });
    }
  };

  renderError = () => {
    if (this.state.error && this.state.displayError) {
      const lines = this.state.error.split('\n');
      return (
        <Alert
          style={{marginBottom: 10}}
          type="error"
          message={lines.map((line, index) => <pre key={index}>{line}</pre>)} />
      );
    }
    return undefined;
  };

  onEdit = (text, position, runFilter) => {
    const autocomplete = this.autocomplete(text, position);
    const parseResult = this.filter.parse(text);
    if (parseResult.error) {
      this.setState({text, error: parseResult.error, filter: null, displayError: false, autocomplete});
    } else {
      this.setState({text, error: null, filter: parseResult, displayError: false, autocomplete}, () => {
        if (runFilter) {
          this.onFilter();
        }
      });
    }
  };

  autocomplete = (text, position) => {
    const composerResult = this.composer.parse(text);
    if (!composerResult.error && this.keywords.length > 0) {
      const element = composerResult.findElementAtPosition(position);
      if (element && element.isProperty && !this.props.keywords.pending) {
        const filter = this.keywords.filter(k => !k.regex && k.fieldName.toLowerCase().startsWith(element.text.toLowerCase()));
        return {
          element,
          filter,
          hovered: null
        };
      }
    }
    return null;
  };

  onEnter = (text) => {
    if (this.state.autocomplete && this.state.autocomplete.hovered !== null) {
      const key = this.state.autocomplete.hovered;
      const text = this.state.text;
      const replaced = text.substring(0, this.state.autocomplete.element.starts) +
        this.state.autocomplete.filter[key].fieldName + text.substring(this.state.autocomplete.element.ends);
      if (this.filterInput) {
        this.filterInput.updateEditor(replaced, this.state.autocomplete.element.starts + this.state.autocomplete.filter[key].fieldName.length);
      }
      this.onEdit(replaced, undefined);
    } else {
      const parseResult = this.filter.parse(text);
      if (parseResult.error) {
        this.setState({text, error: parseResult.error, filter: null, displayError: true, autocomplete: null});
      } else {
        this.setState({text, error: null, filter: parseResult, displayError: false, autocomplete: null});
        this.onFilter();
      }
    }
  };

  onAutocompleteDown = () => {
    const autocomplete = this.state.autocomplete;
    if (autocomplete) {
      if (autocomplete.hovered !== null) {
        autocomplete.hovered = autocomplete.hovered + 1;
      } else {
        autocomplete.hovered = 0;
      }
      if (autocomplete.hovered >= this.state.autocomplete.filter.length) {
        autocomplete.hovered = 0;
      }
      this.setState({autocomplete});
    }
  };

  onAutocompleteUp = () => {
    const autocomplete = this.state.autocomplete;
    if (autocomplete) {
      if (autocomplete.hovered !== null) {
        autocomplete.hovered = autocomplete.hovered - 1;
      } else {
        autocomplete.hovered = this.state.autocomplete.filter.length - 1;
      }
      if (autocomplete.hovered < 0) {
        autocomplete.hovered = this.state.autocomplete.filter.length - 1;
      }
      this.setState({autocomplete});
    }
  };

  renderAutocomplete = () => {
    if (this.state.autocomplete && this.state.autocomplete.filter && this.state.autocomplete.filter.length > 0) {
      const onClick = ({key}) => {
        if (this.state.autocomplete && this.state.autocomplete.filter && this.state.autocomplete.filter.length > key) {
          const text = this.state.text;
          const replaced = text.substring(0, this.state.autocomplete.element.starts) +
            this.state.autocomplete.filter[key].fieldName + text.substring(this.state.autocomplete.element.ends);
          if (this.filterInput) {
            this.filterInput.updateEditor(replaced, this.state.autocomplete.element.starts + this.state.autocomplete.filter[key].fieldName.length);
          }
          this.onEdit(replaced, undefined);
        }
      };
      const onHover = (index) => {
        const autocomplete = this.state.autocomplete;
        autocomplete.hovered = index;
        this.setState({autocomplete});
      };
      return (
        <Menu
          onClick={onClick}
          className={styles.autocompleteMenu}>
          {this.state.autocomplete.filter.map((f, index) => {
            return (
              <Menu.Item
                className={
                  this.state.autocomplete.hovered === index
                    ? styles.autocompleteHoveredItem : styles.autocompleteItem
                }
                key={index}>
                <div onMouseOver={() => onHover(index)}>
                  {
                    f.fieldDescription
                      ? <span>{f.fieldName} - <i style={{fontSize: 'smaller', color: '#777'}}>{f.fieldDescription}</i></span>
                      : <span>{f.fieldName}</span>
                  }
                </div>
              </Menu.Item>
            );
          })}
        </Menu>
      );
    }
    return undefined;
  };

  launchPipeline = ({pipelineId, version, id, configName}) => {
    if (pipelineId && version && id) {
      this.props.router.push(`/launch/${pipelineId}/${version}/${configName || 'default'}/${id}`);
    } else if (pipelineId && version && configName) {
      this.props.router.push(`/launch/${pipelineId}/${version}/${configName}`);
    } else if (pipelineId && version) {
      this.props.router.push(`/launch/${pipelineId}/${version}/default`);
    } else if (id) {
      this.props.router.push(`/launch/${id}`);
    }
  };

  onSelectRun = ({id}) => {
    this.props.router.push(`/run/${id}`);
  };

  openSaveFilterForm = () => {
    this.setState({
      saveFilter: true
    });
  };

  closeSaveFilterForm = () => {
    this.setState({
      saveFilter: false
    });
  };

  saveFilter = ({name}) => {
    const filters = this.savedFilters;
    filters.push({
      name,
      filter: this.state.text
    });
    try {
      localStorage.setItem('filters', JSON.stringify(filters));
    } catch (___) {}
    this.closeSaveFilterForm();
  };

  removeFilter = (index) => {
    const filters = this.savedFilters;
    filters.splice(index, 1);
    try {
      localStorage.setItem('filters', JSON.stringify(filters));
    } catch (___) {}
  };

  @computed
  get savedFilters () {
    const savedFiltersStr = localStorage.getItem('filters');
    if (savedFiltersStr) {
      try {
        return JSON.parse(savedFiltersStr);
      } catch (___) {
        return [];
      }
    }
    return [];
  }

  renderSavedFilters = () => {
    const savedFilters = this.savedFilters.map((filter, index) => {
      return {
        key: index,
        ...filter
      };
    });
    if (savedFilters.length === 0) {
      return undefined;
    }
    const onSelect = ({filter}) => {
      this.setState({savedFiltersDropDownVisible: false}, () => {
        if (this.filterInput) {
          this.filterInput.updateEditor(filter, filter.length);
        }
        this.onEdit(filter, undefined, true);
      });
    };
    const deleteFilter = (key) => {
      this.removeFilter(key);
      this.setState({savedFiltersDropDownVisible: false});
    };
    const onDelete = (filter) => (event) => {
      event.stopPropagation();
      this.setState({savedFiltersDropDownVisible: false});
      Modal.confirm({
        title: `Are you sure you want to delete filter '${filter.name}'?`,
        style: {
          wordWrap: 'break-word'
        },
        onOk () {
          deleteFilter(filter.key);
        },
        cancelText: 'No',
        okText: 'Yes'
      });
    };
    const onDropDownVisibilityChanged = (visible) => {
      this.setState({savedFiltersDropDownVisible: visible});
    };
    const columns = [{
      dataIndex: 'name',
      key: 'name'
    }, {
      key: 'actions',
      className: styles.savedFiltersActionsRow,
      render: (filter) => (
        <Button
          type="danger"
          onClick={onDelete(filter)}
          size="small">
          <Icon type="delete" />
        </Button>
      )
    }];
    const menu = (
      <Row style={{minWidth: 200}}>
        <Table
          className={styles.table}
          style={{width: '100%'}}
          dataSource={savedFilters}
          pagination={false}
          showHeader={false}
          columns={columns}
          rowClassName={() => styles.savedFiltersRow}
          onRowClick={onSelect}
          size="small" />
      </Row>
    );
    return (
      <td style={{width: 50, textAlign: 'center', textTransform: 'uppercase'}}>
        <Dropdown
          placement="bottomRight"
          visible={this.state.savedFiltersDropDownVisible}
          onVisibleChange={onDropDownVisibilityChanged}
          overlay={menu}
          trigger={['click']}>
          <Button><Icon type="down" /></Button>
        </Dropdown>
      </td>
    );
  };

  @computed
  get keywords () {
    if (this.props.keywords.pending || this.props.keywords.error) {
      return [];
    }
    return (this.props.keywords.value || []).map(k => k);
  }

  handleTableChange (pagination) {
    const {current} = pagination;
    this.setState({
      currentPage: current
    });
  }

  componentDidUpdate (prevProps, prevState) {
    if (prevState.currentPage !== this.state.currentPage && !this.state.error && this.state.filter) {
      this._filter(this.state.currentPage, this.state.filter);
    }
  }

  render () {
    if (this.props.keywords.pending) {
      return <LoadingView />;
    }
    return (
      <Card className={styles.runsCard} bodyStyle={{padding: 15}}>
        <Row type="flex" align="middle">
          <table style={{width: '100%', marginBottom: 10}}>
            <tbody>
              <tr>
                <td style={{position: 'relative'}}>
                  <FilterInput
                    ref={input => this.filterInput = input}
                    isError={!!this.state.error}
                    keywords={this.keywords}
                    onEdit={this.onEdit}
                    onEnter={this.onEnter}
                    onUpKeyPressed={this.onAutocompleteUp}
                    onDownKeyPressed={this.onAutocompleteDown} />
                  {this.renderAutocomplete()}
                </td>
                {this.renderSavedFilters()}
                <td style={{width: 70, textAlign: 'center', textTransform: 'uppercase'}}>
                  <Button
                    onClick={this.openSaveFilterForm}
                    disabled={this.state.text === undefined || this.state.text === null || !this.state.text.length}
                    id="save-filter-button">
                    SAVE
                  </Button>
                </td>
                <td style={{width: 100, textAlign: 'center', textTransform: 'uppercase'}}>
                  <AdaptedLink
                    id="basic-runs-filter-button"
                    to={SessionStorageWrapper.getActiveRunsLink()}
                    location={location}>
                    Basic filter
                  </AdaptedLink>
                </td>
              </tr>
            </tbody>
          </table>
        </Row>
        <Row>
          {this.renderError()}
        </Row>
        <Row>
          <RunTable
            useFilter={false}
            loading={this.state.loading}
            dataSource={this.state.runs}
            handleTableChange={::this.handleTableChange}
            pipelines={this.props.pipelines.pending ? [] : (this.props.pipelines.value || []).map(p => p)}
            pagination={{total: this.state.total, pageSize, current: this.state.currentPage}}
            launchPipeline={this.launchPipeline}
            onSelect={this.onSelectRun}
          />
        </Row>
        <SaveFilterForm
          pending={false}
          onSubmit={this.saveFilter}
          onCancel={this.closeSaveFilterForm}
          visible={this.state.saveFilter} />
      </Card>
    );
  }
}

