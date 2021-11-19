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
import ReactDOM from 'react-dom';
import PropTypes from 'prop-types';
import {Button, Checkbox, Icon, Modal, message, Row} from 'antd';
import LoadTool from '../../../models/tools/LoadTool';
import dockerRegistries from '../../../models/tools/DockerRegistriesTree';
import PipelineRunSingleFilter from '../../../models/pipelines/PipelineRunSingleFilter';
import registryName from '../registryName';

class ToolDeletionWarning extends React.Component {
  constructor (props) {
    super(props);
    this.state = {
      showGroupAlert: false,
      groupAlertConfirmed: true,
      error: undefined,
      visible: false,
      resolve: undefined,
      runs: []
    };
  }

  componentDidMount () {
    const {onInitialized} = this.props;
    onInitialized && onInitialized(this);
  }

  display (title, runs, opts = {}) {
    if (this.displayPromise) {
      return this.displayPromise;
    }
    const {showGroupAlert, error, name, total, onNavigateToRuns} = opts;
    this.displayPromise = new Promise((resolve) => {
      const resolveFn = async (...opts) => {
        this.displayPromise = null;
        await resolve(...opts);
        this.setState({
          showGroupAlert: false,
          groupAlertConfirmed: true,
          visible: false,
          runs: [],
          total: 0,
          error: undefined,
          resolve: undefined,
          onNavigateToRuns: undefined
        });
      };
      this.setState({
        showGroupAlert,
        groupAlertConfirmed: !showGroupAlert,
        visible: true,
        title,
        total,
        name,
        runs,
        error,
        resolve: resolveFn,
        onNavigateToRuns
      });
    });
    return this.displayPromise;
  }

  renderGroupAlert = () => {
    const {
      name,
      groupAlertConfirmed
    } = this.state;
    const onConfirmChanged = (e) => {
      this.setState({groupAlertConfirmed: e.target.checked});
    };
    return (
      <Row>
        <Row>
          Group <b>{name || ''}</b> has child tools, do you want to delete
          them?
        </Row>
        <Row style={{
          fontSize: 12,
          color: 'rgba(0,0,0,.65)',
          marginTop: 8,
          marginBottom: 8
        }}>
          <Checkbox
            checked={groupAlertConfirmed}
            onChange={onConfirmChanged}>Delete child tools</Checkbox>
        </Row>
      </Row>
    );
  };

  renderActiveRunsWarning = () => {
    const {runs, total, onNavigateToRuns, resolve} = this.state;
    if (runs && runs.length > 0) {
      const onClick = () => {
        if (onNavigateToRuns && resolve) {
          resolve(false);
          onNavigateToRuns();
        }
      };
      return (
        <Row style={{marginTop: 5, marginBottom: 5}}>
          <Row>
            <b>You are going to delete a tool/version, which is currently in use</b>
            <div style={{display: 'inline-block', marginLeft: 5}}>
              (
              {
                runs
                  .map(({id}, index) =>
                    (<span key={id} style={{marginLeft: index > 0 ? 5 : 0}}>#{id}</span>)
                  )
              }
              {
                total > runs.length
                  ? (
                    <span
                      style={{marginLeft: 5}}
                    >
                      <a onClick={onClick}>and {total - runs.length} more</a>
                    </span>
                  )
                  : undefined
              }
              ).
            </div>
          </Row>
          <Row>
            While the operation will succeed - this may cause unexpected behavior for the users running the jobs.
          </Row>
        </Row>
      );
    }
    return null;
  };

  render () {
    const {visible, title, resolve, showGroupAlert, groupAlertConfirmed} = this.state;
    return (
      <Modal
        visible={visible}
        closable={false}
        title={false}
        bodyStyle={{wordWrap: 'break-word'}}
        zIndex={1001}
        footer={(
          <Row type="flex" align="center" justify="end">
            <Button
              id="remove-button-cancel"
              onClick={resolve ? () => resolve(false) : undefined}
            >
              Cancel
            </Button>
            <Button
              disabled={!groupAlertConfirmed}
              id="remove-button-delete"
              type="danger"
              onClick={resolve ? () => resolve(true) : undefined}
              style={{marginLeft: 5}}
            >
              Delete
            </Button>
          </Row>
        )}
      >
        <h2 style={{margin: 20, color: '#666'}}>
          <Icon type="exclamation-circle" style={{color: 'rgb(255, 191, 51)', marginRight: 10}} />
          {title}
        </h2>
        {showGroupAlert && this.renderGroupAlert()}
        {this.renderActiveRunsWarning()}
        <Row>
          This operation cannot be undone.
        </Row>
      </Modal>
    );
  }
}

ToolDeletionWarning.propTypes = {
  onInitialized: PropTypes.func
};

ToolDeletionWarning.defaultProps = {
  onInitialized: undefined
};

let toolDeletionWarningDialog;

const initializeToolDeletionWarningDialog = (dialog) => {
  toolDeletionWarningDialog = dialog;
};

const toolDeletionWarningDialogContainer = document.createElement('div');
document.body.appendChild(toolDeletionWarningDialogContainer);

ReactDOM.render(
  <ToolDeletionWarning onInitialized={initializeToolDeletionWarningDialog} />,
  toolDeletionWarningDialogContainer
);

const PAGE_SIZE = 5;

async function fetchActiveRuns (dockerImages) {
  const request = new PipelineRunSingleFilter({
    page: 1,
    pageSize: PAGE_SIZE,
    userModified: false,
    statuses: ['RUNNING', 'PAUSING', 'PAUSED', 'RESUMING'],
    dockerImages
  }, false, false);
  await request.filter();
  if (request.loaded) {
    return {
      runs: (request.value || []).map((r) => ({id: r.id, dockerImage: r.dockerImage})),
      total: request.total
    };
  } else {
    return {error: (request.error ? request.error : 'Error fetching active runs')};
  }
}

const STATUSES_SEARCH_STRING =
  'run.status=RUNNING or run.status=PAUSING or run.status=PAUSED or run.status=RESUMING';

function deleteToolConfirm ({registry, group, tool: toolId, version, link}, router) {
  return new Promise(async (resolve) => {
    const dockerImages = [];
    const hide = message.loading('Fetching active jobs info...', 0);
    let showGroupAlert = false;
    let advancedSearchString;
    if (toolId) {
      const toolInfo = new LoadTool(toolId);
      await toolInfo.fetch();
      if (toolInfo.loaded) {
        const {registry: toolRegistry, image} = toolInfo.value;
        dockerImages.push(`${toolRegistry}/${image}${version ? `:${version}` : ''}`);
        advancedSearchString = `docker.image="${toolRegistry}/${image}${version ? `:${version}` : '*'}"`;
      }
    } else if (group) {
      const {id, name: groupName} = group;
      await dockerRegistries.fetchIfNeededOrWait();
      if (dockerRegistries.loaded) {
        const registries = dockerRegistries.value.registries || [];
        for (let r = 0; r < registries.length; r++) {
          const reg = registries[r];
          const groups = reg.groups || [];
          for (let g = 0; g < groups.length; g++) {
            const groupInfo = groups[g];
            if (+groupInfo.id === +id) {
              showGroupAlert = (groupInfo.tools || []).length > 0;
              advancedSearchString = `docker.image="${reg.path}/${groupName}/*"`;
              const tools = groupInfo.tools || [];
              for (let t = 0; t < tools.length; t++) {
                const tool = tools[t];
                const {image} = tool;
                dockerImages.push(`${reg.path}/${image}`);
              }
              break;
            }
          }
        }
      }
    } else if (registry) {
      const {groups, path} = registry;
      advancedSearchString = `docker.image="${path}/*"`;
      for (let g = 0; g < (groups || []).length; g++) {
        const groupInfo = groups[g];
        const tools = groupInfo.tools || [];
        for (let t = 0; t < tools.length; t++) {
          const tool = tools[t];
          const {image} = tool;
          dockerImages.push(`${path}/${image}`);
        }
      }
    }
    if (advancedSearchString) {
      advancedSearchString = `${advancedSearchString} and (${STATUSES_SEARCH_STRING})`;
    } else {
      advancedSearchString = STATUSES_SEARCH_STRING;
    }
    const {error, runs, total} = await fetchActiveRuns(dockerImages);
    hide();
    let title = `Are you sure you want to delete the tool${link ? ' link' : ''}?`;
    let name;
    if (version) {
      title = `Are you sure you want to delete version '${version}'?`;
      name = version;
    } else if (group) {
      title = `Are you sure you want to delete tool group '${group.name}'?`;
      name = group.name;
    } else if (registry) {
      name = registryName(registry);
      title = `Are you sure you want to delete tool registry '${registryName(registry)}'?`;
    }
    const navigate = () => {
      router.push(`/runs/filter?search=${advancedSearchString}`);
    };
    const success = await toolDeletionWarningDialog.display(
      title,
      runs,
      {error, showGroupAlert, name, total, onNavigateToRuns: navigate}
    );
    resolve(success);
  });
}

export default deleteToolConfirm;
