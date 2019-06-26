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
import {computed} from 'mobx';
import {Link} from 'react-router';
import {Alert, Checkbox, Icon, Input, message, Modal, Popover, Row, Table} from 'antd';
import UserAutoComplete from '../special/UserAutoComplete';
import StopPipeline from '../../models/pipelines/StopPipeline';
import PausePipeline from '../../models/pipelines/PausePipeline';
import ResumePipeline from '../../models/pipelines/ResumePipeline';
import {
  PipelineRunCommitCheck,
  PIPELINE_RUN_COMMIT_CHECK_FAILED
} from '../../models/pipelines/PipelineRunCommitCheck';
import {stopRun, canPauseRun, canStopRun, runPipelineActions, terminateRun} from './actions';
import StatusIcon from '../special/run-status-icon';
import AWSRegionTag from '../special/AWSRegionTag';
import UserName from '../special/UserName';
import styles from './RunTable.css';
import DayPicker from 'react-day-picker';
import 'react-day-picker/lib/style.css';
import moment from 'moment';
import displayDate from '../../utils/displayDate';
import evaluateRunDuration from '../../utils/evaluateRunDuration';
import roleModel from '../../utils/roleModel';
import localization from '../../utils/localization';
import registryName from '../tools/registryName';
import parseRunServiceUrl from '../../utils/parseRunServiceUrl';

const DATE_FORMAT = 'YYYY-MM-DD HH:mm:ss.SSS';

@inject('routing', 'pipelines', 'localization', 'dockerRegistries')
@runPipelineActions
@observer
export default class RunTable extends localization.LocalizedReactComponent {
  static propTypes = {
    onInitialized: PropTypes.func,
    dataSource: PropTypes.oneOfType([
      PropTypes.object,
      PropTypes.array
    ]),
    handleTableChange: PropTypes.func,
    pagination: PropTypes.object,
    loading: PropTypes.bool,
    className: PropTypes.string,
    reloadTable: PropTypes.func,
    launchPipeline: PropTypes.func,
    onSelect: PropTypes.func,
    useFilter: PropTypes.bool,
    versionsDisabled: PropTypes.bool,
    ownersDisabled: PropTypes.bool
  };

  state = {
    statuses: {
      visible: false,
      value: [],
      finalValue: [],
      filtered: false,
      isArray: true
    },
    parentRunIds: {
      visible: false,
      value: null,
      finalValue: null,
      filtered: false
    },
    pipelineIds: {
      visible: false,
      value: [],
      finalValue: [],
      filtered: false,
      isArray: true,
      searchString: null
    },
    dockerImages: {
      visible: false,
      value: [],
      finalValue: [],
      filtered: false,
      isArray: true,
      searchString: null
    },
    owners: {
      visible: false,
      value: null,
      finalValue: null,
      filtered: false
    },
    started: {
      visible: false,
      value: null,
      finalValue: null,
      filtered: false,
      isDate: true
    },
    completed: {
      visible: false,
      value: null,
      finalValue: null,
      filtered: false,
      isDate: true,
      isLastHour: true
    }
  };

  @computed
  get dockerImages () {
    if (this.props.dockerRegistries.loaded) {
      const images = [];
      const registries = this.props.dockerRegistries.value.registries || [];
      for (let i = 0; i < registries.length; i++) {
        const groups = registries[i].groups || [];
        for (let j = 0; j < groups.length; j++) {
          const tools = (groups[j].tools || []).map(t => ({
            registry: registryName(registries[i]),
            group: groups[j].name,
            image: t.image.split('/').pop(),
            value: `${registries[i].path}/${t.image}`
          }));
          images.push(...tools);
        }
      }
      return images;
    }
    return [];
  }

  clearState = () => {
    this.setState(
      {
        statuses: {
          visible: false,
          value: [],
          finalValue: [],
          filtered: false,
          isArray: true
        },
        parentRunIds: {
          visible: false,
          value: null,
          finalValue: null,
          filtered: false
        },
        pipelineIds: {
          visible: false,
          value: [],
          finalValue: [],
          filtered: false,
          isArray: true,
          searchString: null
        },
        versions: {
          visible: false,
          value: null,
          finalValue: null,
          filtered: false
        },
        owners: {
          visible: false,
          value: null,
          finalValue: null,
          filtered: false
        },
        started: {
          visible: false,
          value: null,
          finalValue: null,
          filtered: false,
          isDate: true
        },
        completed: {
          visible: false,
          value: null,
          finalValue: null,
          filtered: false,
          isDate: true,
          isLastHour: true
        }
      }
    );
  };

  onFilterDropdownVisibleChange = (filterParameterName, nullValue = null) => (visible) => {
    const filterParameter = this.state[filterParameterName];
    const state = this.state;
    state[filterParameterName] = undefined;
    filterParameter.visible = visible;
    if (!visible) {
      filterParameter.searchString = null;
      filterParameter.value = filterParameter.finalValue ? filterParameter.finalValue : nullValue;
      filterParameter.filtered = filterParameter.finalValue !== null &&
        filterParameter.finalValue !== '';
    }
    const newState = Object.assign(state, {[filterParameterName]: filterParameter});
    this.setState(newState);
  };

  onFilterChanged = (filterParameterName) => () => {
    const filterParameter = this.state[filterParameterName];
    const state = this.state;
    state[filterParameterName] = undefined;
    filterParameter.visible = false;
    if (filterParameter.isArray) {
      filterParameter.finalValue = filterParameter.value.map(p => p);
    } else {
      filterParameter.finalValue = filterParameter.value;
    }
    filterParameter.filtered = filterParameter.finalValue !== null &&
      filterParameter.finalValue !== '';
    const newState = Object.assign(state, {[filterParameterName]: filterParameter});
    if (this.table) {
      const nextFilters = this.table.state.filters;
      if (filterParameter.isDate) {
        nextFilters[filterParameterName] = filterParameter.filtered
          ? [this.getDateStr(filterParameter.finalValue)] : [];
      } else if (filterParameter.isArray) {
        nextFilters[filterParameterName] = filterParameter.filtered
          ? filterParameter.finalValue.map(p => p) : [];
      } else {
        nextFilters[filterParameterName] = filterParameter.filtered
          ? [filterParameter.finalValue] : [];
      }
      this.table.handleFilter(filterParameterName, nextFilters);
    }
    this.setState(newState);
  };

  onInputChange = (filterParameterName) => (e) => {
    this.onFilterValueChange(filterParameterName)(e.target.value);
  };

  onFilterValueChange = (filterParameterName) => (value) => {
    const filterParameter = this.state[filterParameterName];
    const state = this.state;
    state[filterParameterName] = undefined;
    filterParameter.value = value;
    const newState = Object.assign(state, {[filterParameterName]: filterParameter});
    this.setState(newState);
  };

  getStatusesFilter = () => {
    const parameter = 'statuses';
    const allStatuses = [
      'Running',
      'Paused',
      'Success',
      'Failure',
      'Stopped'
    ];
    const clear = () => {
      this.state[parameter].value = [];
      this.state[parameter].searchString = null;
      this.onFilterChanged(parameter)();
    };
    const onChange = (status) => (e) => {
      const statuses = this.state.statuses;
      const index = statuses.value.indexOf(status.toUpperCase());
      if (e.target.checked && index === -1) {
        statuses.value.push(status.toUpperCase());
      } else if (!e.target.checked && index >= 0) {
        statuses.value.splice(index, 1);
      }
      this.setState({statuses});
    };
    const filterDropdown = (
      <div className={styles.filterPopoverContainer} style={{width: 120}}>
        <Row>
          <div style={{maxHeight: 400, overflowY: 'auto'}}>
            {
              allStatuses
                .map(status => {
                  return (
                    <Row
                      style={{margin: 5}}
                      key={status}>
                      <Checkbox
                        onChange={onChange(status)}
                        checked={this.state.statuses.value.indexOf(status.toUpperCase()) >= 0}>
                        {status}
                      </Checkbox>
                    </Row>
                  );
                })
            }
          </div>
        </Row>
        <Row type="flex" justify="space-between" className={styles.filterActionsButtonsContainer}>
          <a onClick={this.onFilterChanged(parameter)}>OK</a>
          <a onClick={clear}>Clear</a>
        </Row>
      </div>
    );
    return {
      filterDropdown,
      filterDropdownVisible: this.state[parameter].visible,
      filtered: this.state[parameter].filtered,
      onFilterDropdownVisibleChange: this.onFilterDropdownVisibleChange(parameter, []),
      filteredValue: this.state[parameter].filtered
        ? this.state[parameter].finalValue : []
    };
  };

  getDateStr = (date) => {
    return moment(date).format(DATE_FORMAT);
  };

  getDateFilter = (parameter) => {
    const clear = () => {
      this.state[parameter].value = null;
      this.onFilterChanged(parameter)();
    };

    const onDateChanged = (date) => {
      if (this.state[parameter].isLastHour) {
        date = new Date(date.getFullYear(), date.getMonth(), date.getDate(), 23, 59, 59);
      } else {
        date = new Date(date.getFullYear(), date.getMonth(), date.getDate(), 0, 0);
      }
      this.onFilterValueChange(parameter)(date);
    };

    const filterDropdown = (
      <div className={styles.filterPopoverContainer}>
        <DayPicker
          className={styles.datePicker}
          selectedDays={this.state[parameter].value}
          onDayClick={onDateChanged} />
        <Row type="flex" justify="space-between" className={styles.filterActionsButtonsContainer}>
          <a onClick={this.onFilterChanged(parameter)}>OK</a>
          <a onClick={clear}>Clear</a>
        </Row>
      </div>
    );
    return {
      filterDropdown,
      filterDropdownVisible: this.state[parameter].visible,
      filtered: this.state[parameter].filtered,
      onFilterDropdownVisibleChange: this.onFilterDropdownVisibleChange(parameter),
      filteredValue: this.state[parameter].filtered
        ? [this.getDateStr(this.state[parameter].finalValue)] : []
    };
  };

  getPipelinesFilter = () => {
    const parameter = 'pipelineIds';
    if (this.props.pipelines && this.props.pipelines.length > 1) {
      const clear = () => {
        this.state[parameter].value = [];
        this.state[parameter].searchString = null;
        this.onFilterChanged(parameter)();
      };

      const onChange = (id) => (e) => {
        const pipelineIds = this.state.pipelineIds;
        const index = pipelineIds.value.indexOf(id);
        if (e.target.checked && index === -1) {
          pipelineIds.value.push(id);
        } else if (!e.target.checked && index >= 0) {
          pipelineIds.value.splice(index, 1);
        }
        this.setState({pipelineIds});
      };

      const onSearchChanged = (event) => {
        const value = event.target.value;
        const pipelineIds = this.state.pipelineIds;
        pipelineIds.searchString = value;
        this.setState({pipelineIds});
      };

      const pipelines = this.props.pipelines || [];
      pipelines.sort((a, b) => {
        if (a.name > b.name) {
          return 1;
        } else if (a.name < b.name) {
          return -1;
        }
        return 0;
      });

      const filterDropdown = (
        <div className={styles.filterPopoverContainer}>
          <Row>
            <Input.Search
              placeholder="Filter"
              onChange={onSearchChanged} />
          </Row>
          <Row>
            <div style={{maxHeight: 400, overflowY: 'auto'}}>
              {
                pipelines
                  .filter(pipeline =>
                    !this.state.pipelineIds.searchString ||
                    this.state.pipelineIds.searchString.length === 0 ||
                    pipeline.name.toLowerCase().indexOf(this.state.pipelineIds.searchString.toLowerCase()) === 0
                  )
                  .map(pipeline => {
                    return (
                      <Row
                        style={{margin: 5}}
                        key={pipeline.id}>
                        <Checkbox
                          onChange={onChange(pipeline.id)}
                          checked={this.state.pipelineIds.value.indexOf(pipeline.id) >= 0}>
                          {pipeline.name}
                        </Checkbox>
                      </Row>
                    );
                })
              }
            </div>
          </Row>
          <Row type="flex" justify="space-between" className={styles.filterActionsButtonsContainer}>
            <a onClick={this.onFilterChanged(parameter)}>OK</a>
            <a onClick={clear}>Clear</a>
          </Row>
        </div>
      );
      return {
        filterDropdown,
        filterDropdownVisible: this.state[parameter].visible,
        filtered: this.state[parameter].filtered,
        onFilterDropdownVisibleChange: this.onFilterDropdownVisibleChange(parameter, []),
        filteredValue: this.state[parameter].filtered
          ? this.state[parameter].finalValue : []
      };
    }
    return undefined;
  };

  getDockerImageFilter = () => {
    const parameter = 'dockerImages';
    if (this.dockerImages && this.dockerImages.length > 1) {
      const clear = () => {
        this.state[parameter].value = [];
        this.state[parameter].searchString = null;
        this.onFilterChanged(parameter)();
      };

      const onChange = (image) => (e) => {
        const dockerImages = this.state.dockerImages;
        const index = dockerImages.value.indexOf(image);
        if (e.target.checked && index === -1) {
          dockerImages.value.push(image);
        } else if (!e.target.checked && index >= 0) {
          dockerImages.value.splice(index, 1);
        }
        this.setState({dockerImages});
      };

      const onSearchChanged = (event) => {
        const value = event.target.value;
        const dockerImages = this.state.dockerImages;
        dockerImages.searchString = value;
        this.setState({dockerImages});
      };

      const images = this.dockerImages;
      images.sort((a, b) => {
        if (a.value > b.value) {
          return 1;
        } else if (a.value < b.value) {
          return -1;
        }
        return 0;
      });
      const searchString = (this.state.dockerImages.searchString || '').toLowerCase();
      const filterImages = (image) => {
        return !searchString ||
          searchString.length === 0 ||
          image.value.toLowerCase().indexOf(searchString) >= 0 ||
          (image.registry || '').toLowerCase().indexOf(searchString) >= 0 ||
          (image.group || '').toLowerCase().indexOf(searchString) >= 0;
      };
      const filterDropdown = (
        <div className={styles.filterPopoverContainer}>
          <Row>
            <Input.Search
              value={this.state[parameter].searchString}
              placeholder="Filter"
              onChange={onSearchChanged} />
          </Row>
          <Row>
            <div style={{maxHeight: 400, overflowY: 'auto'}}>
              {
                images
                  .filter(filterImages)
                  .map(image => {
                    return (
                      <Row
                        style={{margin: 5}}
                        key={image.value}>
                        <Checkbox
                          onChange={onChange(image.value)}
                          checked={this.state.dockerImages.value.indexOf(image.value) >= 0}>
                          <span>{image.registry}</span>
                          <Icon type="right" />
                          <span>{image.group}</span>
                          <Icon type="right" />
                          <span>{image.image}</span>
                        </Checkbox>
                      </Row>
                    );
                  })
              }
            </div>
          </Row>
          <Row type="flex" justify="space-between" className={styles.filterActionsButtonsContainer}>
            <a onClick={this.onFilterChanged(parameter)}>OK</a>
            <a onClick={clear}>Clear</a>
          </Row>
        </div>
      );
      return {
        filterDropdown,
        filterDropdownVisible: this.state[parameter].visible,
        filtered: this.state[parameter].filtered,
        onFilterDropdownVisibleChange: this.onFilterDropdownVisibleChange(parameter, []),
        filteredValue: this.state[parameter].filtered
          ? this.state[parameter].finalValue : []
      };
    }
    return undefined;
  };

  getOwnersFilter = () => {
    if (!this.props.ownersDisabled) {
      const parameter = 'owners';
      const clear = () => {
        this.state[parameter].value = null;
        this.onFilterChanged(parameter)();
      };
      const filterDropdown = (
        <div className={styles.filterPopoverContainer} style={{width: 300}}>
          <UserAutoComplete
            placeholder="Owners"
            value={this.state[parameter].value}
            onChange={(value) => this.onFilterValueChange(parameter)(value)}
            onPressEnter={this.onFilterChanged(parameter)}
          />
          <Row type="flex" justify="space-between" className={styles.filterActionsButtonsContainer}>
            <a onClick={this.onFilterChanged(parameter)}>OK</a>
            <a onClick={clear}>Clear</a>
          </Row>
        </div>
      );
      return {
        filterDropdown,
        filterDropdownVisible: this.state[parameter].visible,
        filtered: this.state[parameter].filtered,
        onFilterDropdownVisibleChange: this.onFilterDropdownVisibleChange(parameter),
        filteredValue: this.state[parameter].filtered ? [this.state[parameter].value] : []
      };
    }
    return undefined;
  };

  getInputFilter = (parameter, placeholder) => {
    const clear = () => {
      this.state[parameter].value = null;
      this.onFilterChanged(parameter)();
    };
    const filterDropdown = (
      <div className={styles.filterPopoverContainer}>
        <Input
          placeholder={placeholder}
          value={this.state[parameter].value}
          onChange={this.onInputChange(parameter)}
          onPressEnter={this.onFilterChanged(parameter)}
        />
        <Row type="flex" justify="space-between" className={styles.filterActionsButtonsContainer}>
          <a onClick={this.onFilterChanged(parameter)}>OK</a>
          <a onClick={clear}>Clear</a>
        </Row>
      </div>
    );
    return {
      filterDropdown,
      filterDropdownVisible: this.state[parameter].visible,
      filtered: this.state[parameter].filtered,
      onFilterDropdownVisibleChange: this.onFilterDropdownVisibleChange(parameter),
      filteredValue: this.state[parameter].filtered ? [this.state[parameter].value] : []
    };
  };

  pausePipeline = async (id, e) => {
    if (e) {
      e.stopPropagation();
    }
    const pausePipeline = new PausePipeline(id);
    await pausePipeline.send({});
    if (pausePipeline.error) {
      message.error(pausePipeline.error);
    }
    if (this.props.reloadTable) {
      this.props.reloadTable();
    }
  };

  resumePipeline = async (id, e) => {
    if (e) {
      e.stopPropagation();
    }
    const resumePipeline = new ResumePipeline(id);
    await resumePipeline.send({});
    if (resumePipeline.error) {
      message.error(resumePipeline.error);
    }
    if (this.props.reloadTable) {
      this.props.reloadTable();
    }
  };

  stopPipeline = async (runId) => {
    const stopPipeline = new StopPipeline(runId);
    await stopPipeline.send(
      {
        endDate: moment.utc().format('YYYY-MM-DD HH:mm:ss.SSS'),
        status: 'STOPPED'
      }
    );
    if (this.props.reloadTable) {
      this.props.reloadTable();
    }
  };

  showStopConfirmDialog = (event, run) => {
    event.stopPropagation();
    return stopRun(this, this.props.reloadTable)(run);
  };

  showTerminateConfirmDialog = (event, run) => {
    event.stopPropagation();
    return terminateRun(this, this.props.reloadTable)(run);
  };

  showPauseConfirmDialog = async (event, run) => {
    event.stopPropagation();
    const checkRequest = new PipelineRunCommitCheck(run.id);
    await checkRequest.fetch();
    let content;
    if (checkRequest.loaded && !checkRequest.value) {
      content = (
        <Alert
          type="error"
          message={PIPELINE_RUN_COMMIT_CHECK_FAILED} />
      );
    }
    Modal.confirm({
      title: (
        <Row>
          Do you want to pause {this.renderPipelineName(run, true, true) || this.localizedString('pipeline')}?
        </Row>
      ),
      content,
      style: {
        wordWrap: 'break-word'
      },
      onOk: () => this.pausePipeline(run.id),
      okText: 'PAUSE',
      cancelText: 'CANCEL',
      width: 450
    });
  };

  showResumeConfirmDialog = (event, run) => {
    event.stopPropagation();
    Modal.confirm({
      title: (
        <span>
          Do you want to resume {this.renderPipelineName(run, true, true) || this.localizedString('pipeline')}?
        </span>
      ),
      style: {
        wordWrap: 'break-word'
      },
      onOk: () => this.resumePipeline(run.id),
      okText: 'RESUME',
      cancelText: 'CANCEL'
    });
  };

  reRunPipeline = (event, run) => {
    event.stopPropagation();
    if (this.props.launchPipeline) {
      this.props.launchPipeline(run);
    }
  };

  renderPause = (record) => {
    if (roleModel.executeAllowed(record) && roleModel.isOwner(record) &&
      record.initialized && !(record.nodeCount > 0) &&
      !(record.parentRunId && record.parentRunId > 0) &&
      record.instance && record.instance.spot !== undefined && !record.instance.spot) {
      switch (record.status.toLowerCase()) {
        case 'pausing':
          return <span id={`run-${record.id}-pausing`}>PAUSING</span>;
        case 'resuming':
          return <span id={`run-${record.id}-resuming`}>RESUMING</span>;
        case 'running':
          if (canPauseRun(record)) {
            return <a
              id={`run-${record.id}-pause-button`}
              onClick={(e) => this.showPauseConfirmDialog(e, record)}>PAUSE</a>;
          }
          break;
        case 'paused':
          return <a
            id={`run-${record.id}-resume-button`}
            onClick={(e) => this.showResumeConfirmDialog(e, record)}>RESUME</a>;
      }
    }
    return <div />;
  };

  renderStatusAction = (record) => {
    if (roleModel.executeAllowed(record)) {
      switch (record.status.toLowerCase()) {
        case 'paused':
          if (roleModel.isOwner(record)) {
            return <a
              id={`run-${record.id}-terminate-button`}
              style={{color: 'red'}}
              onClick={(e) => this.showTerminateConfirmDialog(e, record)}>TERMINATE</a>;
          }
          break;
        case 'running':
        case 'pausing':
        case 'resuming':
          if (roleModel.isOwner(record) && canStopRun(record)) {
            return <a
              id={`run-${record.id}-stop-button`}
              style={{color: 'red'}}
              onClick={(e) => this.showStopConfirmDialog(e, record)}>STOP</a>;
          } else if (record.commitStatus && record.commitStatus.toLowerCase() === 'committing') {
            return <span style={{fontStyle: 'italic'}}>COMMITTING</span>;
          }
          break;
        case 'stopped':
        case 'failure':
        case 'success':
          return <a id={`run-${record.id}-rerun-button`} onClick={(e) => this.reRunPipeline(e, record)}>RERUN</a>;
      }
    }
    return <div />;
  };

  renderRunningTime = (item) => {
    const renderTime = () => {
      if (item.endDate && item.startDate && item.status !== 'RUNNING') {
        return (
          <span>
            {moment.utc(item.endDate)
              .diff(moment.utc(item.startDate), 'minutes', true).toFixed(2)} min
        </span>
        );
      } else {
        return (
          <span>
            Running for {moment.utc().diff(moment.utc(item.startDate), 'minutes', true).toFixed(2)} min
        </span>
        );
      }
    };
    const estimatedPrice = this.renderEstimatedPrice(item);
    const time = renderTime();
    if (estimatedPrice) {
      return (
        <div>
          <Row>
            {time}
          </Row>
          <Row>
            {estimatedPrice}
          </Row>
        </div>
      );
    } else {
      return time;
    }
  };

  renderEstimatedPrice = (item) => {
    if (!item.pricePerHour) {
      return null;
    }
    const diff = evaluateRunDuration(item) * item.pricePerHour;
    const price = Math.ceil(diff * 100.0) / 100.0;
    if (item.nodeCount) {
      return (
        <span>
          Cost: {(price * (+item.nodeCount + 1)).toFixed(2)}$ ({price.toFixed(2)}$)
        </span>
      );
    }
    return (
      <span>
        Cost: {price.toFixed(2)}$
      </span>
    );
  };

  renderPipelineName = (run, renderDockerImageName = false, inline = false) => {
    if (run.pipelineName && run.version) {
      if (inline) {
        return `${run.pipelineName} (${run.version})`;
      }
      return (
        <div>
          <Row>
            {run.pipelineName}
          </Row>
          <Row>
            {run.version}
          </Row>
        </div>
      );
    } else if (run.pipelineName) {
      return run.pipelineName;
    } else if (renderDockerImageName && run.dockerImage) {
      const parts = run.dockerImage.split('/');
      return parts[parts.length - 1];
    }
    return undefined;
  };

  renderDockerImage = (dockerImage) => {
    if (dockerImage) {
      const parts = dockerImage.split('/');
      return parts[parts.length - 1];
    }
    return undefined;
  };

  getColumns = () => {
    const statusesFilter = this.props.useFilter ? this.getStatusesFilter() : {};
    const parentRunFilter = this.props.useFilter ? this.getInputFilter('parentRunIds', 'Parent run id') : {};
    const pipelineFilter = this.props.useFilter ? this.getPipelinesFilter() : {};
    const dockerImagesFilter = this.props.useFilter ? this.getDockerImageFilter() : {};
    const startDateFilter = this.props.useFilter ? this.getDateFilter('started') : {};
    const endDateFilter = this.props.useFilter ? this.getDateFilter('completed') : {};
    const ownersFilter = this.props.useFilter ? this.getOwnersFilter() : {};

    const runColumn = {
      title: this.containsNestedChildren() ? (
        <span style={{paddingLeft: 25}}>Run</span>
      ) : (
        <span>Run</span>
      ),
      dataIndex: 'podId',
      key: 'statuses',
      className: styles.runRowName,
      render: (text, run) => {
        let clusterIcon;
        if (run.nodeCount > 0) {
          clusterIcon = <Icon type="database" />;
        }
        if (run.serviceUrl && run.initialized) {
          const urls = parseRunServiceUrl(run.serviceUrl);
          return (
            <span>
              <StatusIcon run={run} small additionalStyle={{marginRight: 5}} />
              <Popover
                mouseEnterDelay={1}
                content={
                  <div>
                    <ul>
                      {
                        urls.map((url, index) =>
                          <li key={index} style={{margin: 4}}>
                            <a href={url.url} target="_blank">{url.name || url.url}</a>
                          </li>
                        )
                      }
                    </ul>
                  </div>
                }
                trigger="hover">
                {clusterIcon} <Icon type="export" /> {text}
              </Popover>
            </span>
          );
        } else {
          return (<span><StatusIcon run={run} small /> {clusterIcon} {text}</span>);
        }
      },
      ...statusesFilter
    };
    const regionColumn = {
      dataIndex: 'instance',
      key: 'instance',
      className: styles.runRowInstance,
      render: instance => instance && (
        <AWSRegionTag
          plainMode
          style={{fontSize: 'larger'}}
          provider={instance.cloudProvider}
          regionId={instance.cloudRegionId}
        />
      ),
    };
    const parentRunColumn = {
      title: 'Parent run',
      dataIndex: 'parentRunId',
      key: 'parentRunIds',
      className: styles.runRowParentRun,
      render: text => <span className={styles.mainInfo}>{text}</span>,
      ...parentRunFilter
    };
    const pipelineColumn = {
      title: this.localizedString('Pipeline'),
      dataIndex: 'pipelineName',
      key: 'pipelineIds',
      className: styles.runRowPipeline,
      render: (name, run) => this.renderPipelineName(run),
      ...pipelineFilter
    };
    const dockerImageColumn = {
      title: 'Docker image',
      dataIndex: 'dockerImage',
      key: 'dockerImages',
      className: styles.runRowDockerImage,
      render: this.renderDockerImage,
      ...dockerImagesFilter
    };
    const startedColumn = {
      title: <b>Started</b>,
      dataIndex: 'startDate',
      key: 'started',
      className: styles.runRowStarted,
      render: date => displayDate(date),
      ...startDateFilter
    };
    const completedColumn = {
      title: 'Completed',
      dataIndex: 'endDate',
      key: 'completed',
      className: styles.runRowCompleted,
      render: date => displayDate(date),
      ...endDateFilter
    };
    const elapsedTimeColumn = {
      title: 'Elapsed',
      key: 'runningTime',
      className: styles.runRowElapsedTime,
      render: item => this.renderRunningTime(item)
    };
    const ownerColumn = {
      title: 'Owner',
      dataIndex: 'owner',
      key: 'owners',
      className: styles.runRowOwner,
      render: text => <UserName userName={text} />,
      ...ownersFilter
    };

    const renderRunParameter = (runParameter) => {
      if (!runParameter || !runParameter.name) {
        return null;
      }
      const valueSelector = () => {
        return runParameter.resolvedValue || runParameter.value || '';
      };
      if (runParameter.dataStorageLinks) {
        const valueParts = valueSelector().split(/[,|]/);
        const urls = [];
        for (let i = 0; i < valueParts.length; i++) {
          const value = valueParts[i].trim();
          const [link] = runParameter.dataStorageLinks.filter(link => {
            return link.absolutePath && value.toLowerCase() === link.absolutePath.toLowerCase();
          });
          if (link) {
            let url = `/storage/${link.dataStorageId}`;
            if (link.path && link.path.length) {
              url = `/storage/${link.dataStorageId}?path=${link.path}`;
            }
            urls.push((
              <Link key={i} to={url}>{value}</Link>
            ));
          } else {
            urls.push(<span key={i}>{value}</span>);
          }
        }
        return (
          <tr key={runParameter.name}>
            <td style={{verticalAlign: 'top', paddingLeft: 5}}>
              <span>{runParameter.name}: </span>
            </td>
            <td>
              <ul>
                {urls.map((url, index) => <li key={index}>{url}</li>)}
              </ul>
            </td>
          </tr>
        );
      }
      const values = (valueSelector() || '').split(',').map(v => v.trim());
      if (values.length === 1) {
        return (
          <tr key={runParameter.name}>
            <td style={{verticalAlign: 'top', paddingLeft: 5}}>{runParameter.name}:</td>
            <td>{values[0]}</td>
          </tr>
        );
      } else {
        return (
          <tr key={runParameter.name}>
            <td style={{verticalAlign: 'top', paddingLeft: 5}}>
              <span>{runParameter.name}:</span>
            </td>
            <td>
              <ul>
                {values.map((value, index) => <li key={index}>{value}</li>)}
              </ul>
            </td>
          </tr>
        );
      }
    };

    const linksColumn = {
      key: 'links',
      className: styles.runRowLinks,
      render: run => {
        if (run.pipelineRunParameters) {
          const inputParameters = run.pipelineRunParameters
            .filter(p => ['input', 'common'].indexOf((p.type || '').toLowerCase()) >= 0);
          const outputParameters = run.pipelineRunParameters
            .filter(p => (p.type || '').toLowerCase() === 'output');
          if (inputParameters.length > 0 || outputParameters.length > 0) {
            const content = (
              <table>
                <tbody>
                  {
                    inputParameters.length > 0
                      ? <tr><td colSpan={2}><b>Input:</b></td></tr>
                      : undefined
                  }
                  {inputParameters.map(renderRunParameter)}
                  {
                    outputParameters.length > 0
                      ? <tr><td colSpan={2}><b>Output:</b></td></tr>
                      : undefined
                  }
                  {outputParameters.map(renderRunParameter)}
                </tbody>
              </table>
            );
            return (
              <Popover content={content}>
                <a onClick={() => {}}>LINKS</a>
              </Popover>
            );
          }
        }
        return null;
      }
    };
    const actionsPauseRunColumn = {
      key: 'actionPause',
      className: styles.runRow,
      render: (text, record) => this.renderPause(record)
    };
    const actionsRunColumn = {
      key: 'actionRerunStop',
      className: styles.runRow,
      render: (text, record) => this.renderStatusAction(record)
    };
    const actionsLogColumn = {
      key: 'actionLog',
      dataIndex: 'id',
      className: styles.runRow,
      render: (id, record) => (
        <Link id={`run-${id}-logs-button`} to={`/run/${id}`} className={styles.linkAction}>Log</Link>
      )
    };

    return [
      runColumn,
      regionColumn,
      parentRunColumn,
      pipelineColumn,
      dockerImageColumn,
      startedColumn,
      completedColumn,
      elapsedTimeColumn,
      ownerColumn,
      linksColumn,
      actionsPauseRunColumn,
      actionsRunColumn,
      actionsLogColumn
    ];
  };

  prepareSourceItem = (item) => {
    if (item.pipelineRunParameters) {
      const [parentId] = item.pipelineRunParameters.filter(p => p.name === 'parent-id');
      if (parentId && parseInt(parentId.value) !== 0) {
        item.parentRunId = parentId.value;
      }
    }
    if (item.childRuns) {
      item.children = item.childRuns.map(this.prepareSourceItem);
    }
    return item;
  };

  containsNestedChildren = () => {
    if (this.props.dataSource) {
      return this.props.dataSource.map(this.prepareSourceItem).filter(i => i.children && i.children.length).length > 0;
    }
    return false;
  };

  render () {
    const source = this.props.dataSource && this.props.dataSource.map(this.prepareSourceItem);
    const style = this.props.className ? {className: this.props.className} : {};
    return (
      <Table
        className={`${styles.table} runs-table`}
        {...style}
        ref={(ele) => { this.table = ele; }}
        columns={this.getColumns()}
        rowKey={record => record.id}
        rowClassName={(record) => `${
          record.serviceUrl &&
          (
            record.status === 'RUNNING' ||
            record.status === 'PAUSED' ||
            record.status === 'PAUSING' ||
            record.status === 'RESUMING'
          ) &&
          record.initialized
            ? styles.serviceUrlRun : styles.run
        } run-${record.id}`}
        dataSource={source}
        onChange={this.props.handleTableChange}
        onRowClick={this.props.onSelect}
        pagination={this.props.pagination}
        loading={this.props.loading}
        size="small"
        indentSize={10}
        locale={{emptyText: 'No Data', filterConfirm: 'OK', filterReset: 'Clear'}}
    />);
  }

  componentDidMount () {
    this.props.onInitialized && this.props.onInitialized(this);
  }
}
