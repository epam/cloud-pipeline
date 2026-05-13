/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';
import {computed, observable} from 'mobx';
import {Checkbox, Form, Input, Select, Spin} from 'antd';
import {RepositoryTypes} from '../../../../special/git-repository-control';
import ManualRepositoryForm from './ManualRepositoryForm';
import PipelineGitNameSpaces from '../../../../../models/pipelines/PipelineGitNameSpaces';
import PipelineGitRepositories from '../../../../../models/pipelines/PipelineGitRepositories';

@observer
export default class GitHubRepositoryForm extends React.Component {
  static propTypes = {
    form: PropTypes.object,
    formItemLayout: PropTypes.object,
    formItemStyle: PropTypes.object,
    onSubmit: PropTypes.func,
    pending: PropTypes.bool,
    pipeline: PropTypes.object,
    readOnly: PropTypes.bool
  };

  state = {
    manualSettings: false
  };

  @observable _namespacesRequest = new PipelineGitNameSpaces();
  @observable _repositoriesRequest = null;


  @computed
  get namespaces () {
    const req = this._namespacesRequest;
    return req && req.loaded && req.value ? req.value : [];
  }

  @computed
  get githubRepositories () {
    const req = this._repositoriesRequest;
    return req && req.loaded && req.value ? req.value : [];
  }

  componentDidMount () {
    if (!this.state.manualSettings) {
      this.fetchNamespaces();
    }
  }

  componentDidUpdate (_, prevState) {
    if (prevState.manualSettings && !this.state.manualSettings) {
      this.fetchNamespaces();
    }
  }

  fetchNamespaces = () => {
    const req = this._namespacesRequest;
    if (req.loaded || req.pending) {
      return;
    }
    req.fetch();
  };

  onGithubOwnerChanged = (namespaceId) => {
    const {form} = this.props;
    if (!namespaceId) {
      this._repositoriesRequest = null;
      form.setFieldsValue({githubRepository: undefined});
      return;
    }
    const req = new PipelineGitRepositories(namespaceId);
    this._repositoriesRequest = req;
    req.fetch();
  };

  githubRepositoryOptionValue = (repo) => repo.httpUrl || '';

  onManualSettingsChange = (e) => this.setState({manualSettings: e.target.checked});

  renderGitHubSelectForm () {
    const {
      form,
      formItemLayout,
      formItemStyle,
      pending,
      pipeline,
      readOnly
    } = this.props;
    const {getFieldDecorator} = form;
    const nsReq = this._namespacesRequest;
    const nsPending = nsReq && nsReq.pending;
    const reposReq = this._repositoriesRequest;
    const reposPending = reposReq && reposReq.pending;
    const githubOwner = form.getFieldValue('githubOwner');
    const githubRepositorySelectDisabled =
      !!pipeline || pending || !githubOwner;
    return (
      <div>
        <Form.Item
          key="githubOwner"
          className="edit-pipeline-form-github-owner-container"
          {...formItemLayout}
          style={formItemStyle}
          label="Organization"
        >
          {getFieldDecorator('githubOwner', {
            initialValue: undefined,
            onChange: this.onGithubOwnerChanged
          })(
            <Select
              allowClear
              showSearch
              style={{width: '100%'}}
              placeholder="Select owner or organization"
              disabled={pending || readOnly}
              optionFilterProp="children"
              notFoundContent={
                nsPending ? <Spin size="small" /> : 'No organizations'
              }
            >
              {this.namespaces.map((ns) => (
                <Select.Option key={ns.id} value={ns.id}>
                  {ns.name || ns.id}
                </Select.Option>
              ))}
            </Select>
          )}
        </Form.Item>
        <Form.Item
          key="githubRepository"
          className="edit-pipeline-form-github-repository-container"
          {...formItemLayout}
          style={formItemStyle}
          label="GitHub repository"
        >
          {getFieldDecorator('githubRepository', {
            initialValue: `${pipeline && pipeline.repository ? pipeline.repository : ''}`
          })(
            <Select
              allowClear
              showSearch
              style={{width: '100%'}}
              placeholder="Select GitHub repository"
              disabled={githubRepositorySelectDisabled}
              optionFilterProp="children"
              notFoundContent={
                reposPending ? <Spin size="small" /> : 'No GitHub repositories'
              }
              filterOption={(input, option) =>
                (option.props.children || '')
                  .toString()
                  .toLowerCase()
                  .indexOf((input || '').toLowerCase()) >= 0
              }
            >
              {this.githubRepositories.map((repo) => {
                const value = this.githubRepositoryOptionValue(repo);
                if (!value) {
                  return null;
                }
                const optionValue = String(value);
                return (
                  <Select.Option key={optionValue} value={optionValue}>
                    {repo.name || optionValue}
                  </Select.Option>
                );
              })}
            </Select>
          )}
        </Form.Item>
        <Form.Item
          key="githubBranch"
          className="edit-pipeline-form-github-branch-container"
          {...formItemLayout}
          style={formItemStyle}
          label="GitHub branch"
        >
          {getFieldDecorator('githubBranch', {
            initialValue:
              pipeline && pipeline.branch
                ? pipeline.branch
                : undefined
          })(<Input disabled={pending || readOnly} />)}
        </Form.Item>
      </div>
    );
  }

  render () {
    const {
      form,
      formItemLayout,
      formItemStyle,
      onSubmit,
      pending,
      pipeline,
      readOnly
    } = this.props;
    const {manualSettings} = this.state;
    return (
      <div>
        <Form.Item
          key="manualSettings"
          {...formItemLayout}
          style={formItemStyle}
          label=" "
          colon={false}
        >
          <Checkbox
            checked={manualSettings}
            onChange={this.onManualSettingsChange}
            disabled={pending || readOnly}
          >
            Manual settings
          </Checkbox>
        </Form.Item>
        {
          manualSettings ? (
            <ManualRepositoryForm
              key="repository-fields-manual"
              form={form}
              formItemLayout={formItemLayout}
              formItemStyle={formItemStyle}
              onSubmit={onSubmit}
              pending={pending}
              pipeline={pipeline}
              readOnly={readOnly}
            />
          ) : (
            this.renderGitHubSelectForm()
          )
        }
      </div>
    );
  }
}
