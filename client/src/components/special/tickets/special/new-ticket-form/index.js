/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
import classNames from 'classnames';
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import {
  Input,
  Radio,
  Upload,
  Button,
  Icon,
  Select
} from 'antd';
import Markdown from '../../../markdown';
import blobFilesToBase64 from '../blobFilesToBase64';
import styles from './new-ticket-form.css';

const PREVIEW_MODES = {
  edit: 'edit',
  preview: 'preview'
};

@inject('preferences')
@observer
export default class NewTicketForm extends React.Component {
  static propTypes = {
    onSave: PropTypes.func
  };

  state = {
    description: '',
    title: '',
    mode: PREVIEW_MODES.edit,
    fileList: [],
    status: undefined
  }

  componentDidMount () {
    this.setInitialState();
  }

  componentDidUpdate (prevProps) {
    if (prevProps.preferences?.loaded !== this.props.preferences?.loaded) {
      this.setInitialState();
    }
  }

  @computed
  get statuses () {
    const {preferences} = this.props;
    if (preferences && preferences.loaded) {
      return preferences.gitlabIssueStatuses;
    }
    return [];
  }

  get submitDisabled () {
    const {description, title, status} = this.state;
    const {pending} = this.props;
    return pending || !title || !description || !status;
  }

  setInitialState = () => {
    const {preferences} = this.props;
    const {status} = this.state;
    if (!status && preferences.loaded) {
      const [status] = this.statuses || [];
      this.setState({status});
    }
  };

  onChangeMode = (event) => {
    this.setState({mode: event.target.value});
  };

  onChangeValue = (field, eventType, stopPropagation = false) => event => {
    event && stopPropagation && event.stopPropagation();
    let value;
    switch (eventType) {
      case 'input':
        value = event.target.value;
        break;
      case 'select':
        value = event;
        break;
      default:
        value = undefined;
    }
    if (value !== undefined) {
      this.setState({[field]: value});
    }
  };

  onRemoveFile = (file) => {
    this.setState(({fileList}) => {
      const index = fileList.indexOf(file);
      const newFileList = fileList.slice();
      newFileList.splice(index, 1);
      return {
        fileList: newFileList
      };
    });
  };

  beforeUpload = (file) => {
    this.setState(({fileList}) => ({
      fileList: [...fileList, file]
    }));
    return false;
  };

  onSubmitClick = async () => {
    const {
      title,
      description,
      status,
      fileList
    } = this.state;
    const {onSave} = this.props;
    const base64Files = fileList && fileList.length > 0
      ? await blobFilesToBase64(fileList)
      : {};
    const payload = {
      title,
      description,
      labels: [status],
      ...(Object.keys(base64Files).length > 0 && {attachments: base64Files})
    };
    onSave && onSave(payload, true);
  };

  renderPreview = () => {
    const {description} = this.state;
    if (!description) {
      return (
        <span className={styles.previewPlaceholder}>
          Nothing to preview.
        </span>
      );
    }
    return (
      <Markdown
        md={description}
        style={{
          margin: '10px 0',
          minHeight: '32px',
          padding: '0 10px'
        }}
      />
    );
  };

  renderTicketEditor = () => {
    const {
      mode,
      description,
      title,
      fileList
    } = this.state;
    const {uploadEnabled} = this.props;
    return (
      <div
        className={classNames(
          'cp-bordered',
          styles.editorContainer
        )}
      >
        <Input
          value={title}
          onChange={this.onChangeValue('title', 'input')}
          placeholder="Title"
          style={{marginBottom: 10}}
        />
        <div>
          <Radio.Group value={mode} onChange={this.onChangeMode}>
            {Object.values(PREVIEW_MODES).map((key) => (
              <Radio.Button
                key={key}
                value={key}
                style={{
                  textTransform: 'capitalize',
                  borderRadius: '4px 4px 0 0'
                }}
              >
                {key}
              </Radio.Button>
            ))}
          </Radio.Group>
        </div>
        <div style={{border: mode === PREVIEW_MODES.edit
          ? 'unset'
          : '1px solid #dfdfdf'
        }}>
          {mode === PREVIEW_MODES.edit ? (
            <div className={styles.editorForm}>
              <div style={{position: 'relative'}}>
                <Input.TextArea
                  rows={8}
                  onChange={this.onChangeValue('description', 'input', true)}
                  value={description}
                  onClick={e => e.stopPropagation()}
                  placeholder="Leave a comment"
                  style={{
                    resize: 'none',
                    borderRadius: '0 4px 0 0'
                  }}
                />
                <Button
                  className={styles.submitButton}
                  disabled={this.submitDisabled}
                  onClick={this.onSubmitClick}
                >
                  Submit new ticket
                </Button>
              </div>
              {uploadEnabled ? (
                <Upload
                  style={{width: '100%'}}
                  fileList={fileList}
                  onRemove={this.onRemoveFile}
                  beforeUpload={this.beforeUpload}
                >
                  <Button className={styles.uploadButton}>
                    <Icon type="upload" /> Click to Upload
                  </Button>
                </Upload>
              ) : <div style={{height: '28px'}} />}
            </div>
          ) : (
            this.renderPreview()
          )}
        </div>
      </div>
    );
  };

  render () {
    const {status} = this.state;
    return (
      <div className={styles.container}>
        <div className={styles.content}>
          {this.renderTicketEditor()}
          <div style={{
            display: 'flex',
            minWidth: '300px',
            flexDirection: 'column',
            padding: '0 15px'
          }}>
            <div style={{display: 'flex', flexDirection: 'column', marginBottom: '5px'}}>
              <span>Status:</span>
              <Select
                onChange={this.onChangeValue('status', 'select')}
                value={status}
                style={{width: '100%'}}
              >
                {this.statuses.map(status => (
                  <Select.Option key={status}>
                    {`${status.charAt(0).toUpperCase()}${status.slice(1)}`}
                  </Select.Option>
                ))}
              </Select>
            </div>
          </div>
        </div>
      </div>
    );
  }
}
