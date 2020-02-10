/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {computed, observable} from 'mobx';
import {Button, message} from 'antd';
import RunScheduleDialog from '../../../runs/run-scheduling/run-scheduling-dialog';
import RemoveSchedule from '../../../../models/configurationSchedule/RemoveConfigurationSchedule';
import UpdateSchedule from '../../../../models/configurationSchedule/UpdateConfigurationSchedule';
import CreateSchedule from '../../../../models/configurationSchedule/CreateConfigurationSchedule';

class Schedule extends React.Component {
  @observable schedule;

  @computed
  get rules () {
    if (this.schedule && this.schedule.loaded) {
      return (this.schedule.value || []).map(r => r);
    }
    return [];
  }

  constructor (props) {
    super(props);
    this.state = {
      opened: false,
      pending: false
    };
  }

  componentDidMount () {
    this.reload();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.configurationId !== this.props.configurationId) {
      this.reload();
    } else if (prevState.opened !== this.state.opened && prevState.opened) {
      this.invalidateCache();
    }
  }

  componentWillUnmount () {
    this.invalidateCache();
  }

  reload = () => {
    const {configurationId, configurationSchedules} = this.props;
    if (!this.schedule) {
      this.schedule = configurationSchedules.getSchedule(configurationId);
    } else {
      this.schedule.invalidateCache();
    }
    return this.schedule.fetchIfNeededOrWait();
  };

  invalidateCache = () => {
    const {configurationId, configurationSchedules} = this.props;
    configurationSchedules.invalidateScheduleCache(configurationId);
  };

  operationInProgress = (fn) => (...opts) => {
    this.setState({pending: true}, async () => {
      await fn(...opts);
      this.setState({pending: false});
    });
  };

  openScheduleModal = () => {
    this.setState({opened: true});
  };

  closeScheduleModal = () => {
    this.setState({opened: false});
  };

  saveRules = (rules) => {
    const hide = message.loading('Saving schedule rules...', 0);
    return new Promise((resolve) => {
      this.saveRunSchedule(rules)
        .then(() => {
          this.closeScheduleModal();
          this.reload();
          hide();
          resolve();
        })
        .catch(error => {
          hide();
          message.error(error.toString(), 5);
          resolve();
        });
    });
  };

  saveRunSchedule = (rules) => {
    const {configurationId} = this.props;
    const toRemove = [];
    const toUpdate = [];
    const toCreate = [];

    /**
     * Returns true if the rule has changes
     * */
    const ruleChanged = ({scheduleId, action, cronExpression, timeZone, removed}) => {
      if (!scheduleId || removed) {
        return true;
      }
      const [existed] = this.rules.filter(r => r.scheduleId === scheduleId);
      if (!existed) {
        return true;
      }

      return existed.action !== action ||
        existed.cronExpression !== cronExpression ||
        existed.timeZone !== timeZone;
    };

    rules.forEach(({scheduleId, action, cronExpression, timeZone, removed}) => {
      if (!ruleChanged({scheduleId, action, cronExpression, timeZone, removed})) {
        return;
      }
      const payload = {scheduleId, action, cronExpression, timeZone};
      if (scheduleId) {
        if (removed) {
          toRemove.push(payload);
        } else {
          toUpdate.push(payload);
        }
      } else if (!removed) {
        toCreate.push(payload);
      }
    });
    const wrapRequest = (Request, payload) => new Promise((resolve, reject) => {
      const request = new Request(configurationId);
      request
        .send(payload)
        .then(() => {
          if (request.error) {
            reject(new Error(request.error));
          } else {
            resolve();
          }
        })
        .catch(reject);
    });
    const requests = [];
    if (toRemove.length > 0) {
      requests.push(wrapRequest(RemoveSchedule, toRemove));
    }
    if (toUpdate.length > 0) {
      requests.push(wrapRequest(UpdateSchedule, toUpdate));
    }
    if (toCreate.length > 0) {
      requests.push(wrapRequest(CreateSchedule, toCreate));
    }
    return Promise.all(requests);
  };

  render () {
    const {opened, pending: operationInProgress} = this.state;
    const pending = operationInProgress ||
      !this.schedule || (this.schedule.pending && !this.schedule.loaded);
    return (
      <div style={{display: 'inline'}}>
        <Button
          disabled={pending}
          size="small"
          onClick={this.openScheduleModal}
        >
          <span
            style={{lineHeight: 'inherit', verticalAlign: 'middle'}}
          >
            Run schedule
            {
              this.rules.length > 0 && (
                <span>
                  : {this.rules.length} rule{this.rules.length > 1 ? 's' : ''}
                </span>
              )
            }
          </span>
        </Button>
        <RunScheduleDialog
          availableActions={[RunScheduleDialog.Actions.run]}
          title="Run schedule"
          rules={this.rules}
          disabled={pending}
          visible={opened}
          onClose={this.closeScheduleModal}
          onSubmit={this.operationInProgress(this.saveRules)}
        />
      </div>
    );
  }
}

Schedule.propTypes = {
  configurationId: PropTypes.oneOfType([PropTypes.number, PropTypes.string])
};

export default inject('configurationSchedules')(observer(Schedule));
