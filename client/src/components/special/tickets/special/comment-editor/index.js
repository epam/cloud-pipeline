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
import {
  Input,
  Radio,
  Upload,
  Button,
  Icon
} from 'antd';
import Markdown from '../../../markdown';
import blobFilesToBase64 from '../utilities/blob-files-to-base64';

const MODES = {
  edit: 'edit',
  preview: 'preview'
};

const ModeNames = {
  [MODES.edit]: 'Write',
  [MODES.preview]: 'Preview'
};

export default class CommentEditor extends React.Component {
  static propTypes = {
    isNewComment: PropTypes.bool,
    comment: PropTypes.object,
    onCancel: PropTypes.func,
    onSave: PropTypes.func,
    className: PropTypes.string,
    disabled: PropTypes.bool
  };

  state={
    mode: MODES.edit,
    description: '',
    fileList: []
  }

  componentDidMount () {
    this.setInitialState();
  }

  componentDidUpdate (prevProps) {
    if (prevProps.comment?.id !== this.props.comment?.id) {
      this.setInitialState();
    }
  }

  get submitDisabled () {
    const {disabled} = this.props;
    const {description} = this.state;
    return disabled || !description;
  }

  setInitialState = () => {
    const {comment} = this.props;
    if (comment) {
      this.setState({
        description: comment.description || comment.body
      });
    }
  };

  clearState = () => {
    this.setState({
      description: '',
      fileList: []
    });
  }

  onModeChange = (event) => {
    this.setState({mode: event.target.value});
  };

  onChangeTextField = fieldName => event => {
    this.setState({[fieldName]: event.target.value});
  };

  onCancel = () => {
    const {onCancel} = this.props;
    onCancel && onCancel();
  };

  onSave = async () => {
    const {description, fileList} = this.state;
    const {onSave, isNewComment} = this.props;
    const base64Files = fileList && fileList.length > 0
      ? await blobFilesToBase64(fileList)
      : {};
    onSave && onSave({
      description,
      ...(Object.keys(base64Files).length > 0 && {attachments: base64Files})
    });
    isNewComment && this.clearState();
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

  render () {
    const {mode, description, fileList} = this.state;
    const {
      isNewComment,
      className,
      uploadEnabled,
      disabled
    } = this.props;
    return (
      <div
        style={{
          padding: '10px',
          borderRadius: '4px'
        }}
        className={classNames('cp-panel', className)}
      >
        <Radio.Group value={mode} onChange={this.onModeChange}>
          {Object.values(MODES).map((key) => (
            <Radio.Button
              key={key}
              value={key}
              style={{
                textTransform: 'capitalize',
                borderRadius: '4px 4px 0 0'
              }}
            >
              {ModeNames[key] || key}
            </Radio.Button>
          ))}
        </Radio.Group>
        <div style={{border: mode === MODES.edit
          ? 'unset'
          : '1px solid #dfdfdf'
        }}>
          {mode === MODES.edit ? (
            <div style={{display: 'flex', flexDirection: 'column'}}>
              <div style={{position: 'relative'}}>
                <Input.TextArea
                  rows={8}
                  onChange={this.onChangeTextField('description')}
                  value={description}
                  onClick={e => e.stopPropagation()}
                  placeholder="Leave a comment"
                  style={{
                    resize: 'none',
                    borderRadius: '0 4px 0 0'
                  }}
                />
                <div
                  style={{
                    position: 'absolute',
                    bottom: '-28px',
                    right: 0,
                    height: '28px'
                  }}
                >
                  {
                    !isNewComment && (
                      <Button
                        onClick={this.onCancel}
                        style={{marginRight: '5px', borderRadius: '0 0 4px 4px'}}
                      >
                        Cancel
                      </Button>
                    )
                  }
                  <Button
                    onClick={this.onSave}
                    type="primary"
                    style={{borderRadius: '0 0 4px 4px'}}
                    disabled={this.submitDisabled}
                  >
                    {isNewComment ? 'Comment' : 'Update comment'}
                  </Button>
                </div>
              </div>
              {uploadEnabled ? (
                <Upload
                  style={{width: '100%'}}
                  fileList={fileList}
                  onRemove={this.onRemoveFile}
                  beforeUpload={this.beforeUpload}
                  disabled={disabled}
                >
                  <Button style={{borderRadius: '0 0 4px 4px'}}>
                    <Icon type="upload" /> Click to Upload
                  </Button>
                </Upload>
              ) : (<span style={{height: 28}} />)}
            </div>
          ) : (
            <Markdown
              md={description || 'Nothing to preview.'}
              style={{
                margin: '10px 0',
                minHeight: '32px',
                padding: '5px 10px'
              }}
            />
          )}
        </div>
      </div>
    );
  }
}
