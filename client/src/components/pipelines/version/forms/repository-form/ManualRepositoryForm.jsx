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
import {Form, Input} from 'antd';
import {RepositoryTypes} from '../../../../special/git-repository-control';

export default class ManualRepositoryForm extends React.Component {
  static propTypes = {
    formItemLayout: PropTypes.object,
    formItemStyle: PropTypes.object,
    onSubmit: PropTypes.func,
    pending: PropTypes.bool,
    pipeline: PropTypes.shape({
      branch: PropTypes.string,
      repository: PropTypes.string,
      repositoryToken: PropTypes.string,
      repositoryType: PropTypes.string,
    }),
    readOnly: PropTypes.bool,
    showToken: PropTypes.bool,
  };

  render() {
    const {
      formItemLayout,
      formItemStyle,
      onSubmit,
      pending,
      pipeline,
      readOnly,
      showToken = true,
    } = this.props;
    return (
      <div>
        <Form.Item
          key="repository"
          className="edit-pipeline-form-repository-container"
          {...formItemLayout}
          style={formItemStyle}
          label="Repository"
          name="repository"
          dependencies={['repositoryType']}
          rules={[
            ({getFieldValue}) => ({
              validator: async (_, value) => {
                let repoType = getFieldValue('repositoryType');
                if (!repoType && pipeline) {
                  repoType = pipeline.repositoryType;
                }
                if (repoType === RepositoryTypes.AzureDevOps && !value) {
                  throw new Error('Repository is required');
                }
              },
            }),
          ]}
        >
          <Input onPressEnter={onSubmit} disabled={!!pipeline || pending} />
        </Form.Item>
        <Form.Item
          key="branch"
          className="edit-pipeline-form-branch-container"
          {...formItemLayout}
          style={formItemStyle}
          label="Branch"
          name="branch"
        >
          <Input disabled={pending || readOnly} />
        </Form.Item>
        {showToken ? (
          <Form.Item
            key="token"
            className="edit-pipeline-form-repository-token-container"
            {...formItemLayout}
            style={formItemStyle}
            label="Token"
            name="token"
            dependencies={['repositoryType']}
            rules={[
              ({getFieldValue}) => ({
                validator: async (_, value) => {
                  let repoType = getFieldValue('repositoryType');
                  if (!repoType && pipeline) {
                    repoType = pipeline.repositoryType;
                  }
                  if (repoType === RepositoryTypes.AzureDevOps && !value) {
                    throw new Error('Token is required');
                  }
                },
              }),
            ]}
          >
            <Input
              onPressEnter={onSubmit}
              type="password"
              autoComplete="off"
              disabled={pending || readOnly}
            />
          </Form.Item>
        ) : null}
      </div>
    );
  }
}
