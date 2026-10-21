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
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {Form} from 'antd';
import {
  RepositoryTypes,
  normalizeRepositoryType
} from '../../../../special/git-repository-control';
import RepositoryTypeSelector from '../repository-type';
import GitHubRepositoryForm from './GitHubRepositoryForm';
import ManualRepositoryForm from './ManualRepositoryForm';

export default class RepositoryForm extends React.Component {
  static propTypes = {
    editRepositorySettings: PropTypes.bool,
    form: PropTypes.shape({
      getFieldDecorator: PropTypes.func,
      getFieldValue: PropTypes.func
    }),
    formItemLayout: PropTypes.object,
    formItemStyle: PropTypes.object,
    githubType: PropTypes.string,
    onGithubTypeChange: PropTypes.func,
    onRepositoryTypeChanged: PropTypes.func,
    onSubmit: PropTypes.func,
    pending: PropTypes.bool,
    pipeline: PropTypes.shape({
      branch: PropTypes.string,
      repository: PropTypes.string,
      repositoryToken: PropTypes.string,
      repositoryType: PropTypes.string
    }),
    readOnly: PropTypes.bool
  };

  render () {
    const {
      editRepositorySettings,
      form,
      formItemLayout,
      formItemStyle,
      githubType,
      onGithubTypeChange,
      onRepositoryTypeChanged,
      onSubmit,
      pending,
      pipeline,
      readOnly
    } = this.props;
    const {getFieldDecorator} = form;
    const formItemDisplayStyle = {
      ...formItemStyle,
      display: editRepositorySettings ? 'inherit' : 'none'
    };
    const repositoryType = normalizeRepositoryType(
      form.getFieldValue('repositoryType') || (
        pipeline && pipeline.repositoryType
      ) || RepositoryTypes.GitLab
    );
    const useGitHubForm = repositoryType === RepositoryTypes.GitHub;
    return (
      <div>
        <Form.Item
          key="repositoryType"
          className="edit-pipeline-form-repository-type-container"
          {...formItemLayout}
          style={formItemDisplayStyle}
          label="Repository Type"
        >
          {getFieldDecorator('repositoryType', {
            initialValue:
              pipeline && pipeline.repositoryType
                ? normalizeRepositoryType(pipeline.repositoryType)
                : RepositoryTypes.GitLab
          })(
            <RepositoryTypeSelector
              disabled={!!pipeline || pending}
              onRepositoryTypeChanged={onRepositoryTypeChanged}
            />
          )}
        </Form.Item>
        {
          useGitHubForm ? (
            <GitHubRepositoryForm
              form={form}
              formItemLayout={formItemLayout}
              formItemStyle={formItemDisplayStyle}
              githubType={githubType}
              onGithubTypeChange={onGithubTypeChange}
              onSubmit={onSubmit}
              pending={pending}
              pipeline={pipeline}
              readOnly={readOnly}
            />
          ) : (
            <ManualRepositoryForm
              form={form}
              formItemLayout={formItemLayout}
              formItemStyle={formItemDisplayStyle}
              onSubmit={onSubmit}
              pending={pending}
              pipeline={pipeline}
              readOnly={readOnly}
            />
          )
        }
      </div>
    );
  }
}
