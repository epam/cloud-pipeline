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
import {
  Input,
  Radio,
  Upload,
  Button,
  Icon,
  Modal,
  message
} from 'antd';
import Markdown from '../../../markdown';
import blobFilesToBase64 from '../utilities/blob-files-to-base64';
import styles from './new-ticket-form.css';

const PREVIEW_MODES = {
  edit: 'edit',
  preview: 'preview'
};

const PreviewModeNames = {
  [PREVIEW_MODES.edit]: 'Write',
  [PREVIEW_MODES.preview]: 'Preview'
};

class NewTicketForm extends React.Component {
  state = {
    description: '',
    title: '',
    mode: PREVIEW_MODES.edit,
    fileList: []
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.modalVisible !== this.props.modalVisible &&
      this.props.renderAsModal
    ) {
      this.reset();
    }
  }

  reset = () => {
    this.setState({
      description: '',
      title: '',
      mode: PREVIEW_MODES.edit,
      fileList: []
    });
  };

  get submitDisabled () {
    const {description, title} = this.state;
    const {pending} = this.props;
    return pending || !title || !description;
  }

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
    const {fileList} = this.state;
    const nameDuplicate = fileList.find(({name}) => name === file.name);
    if (nameDuplicate) {
      message.error(`${file.name} - file name should be unique`, 5);
      return false;
    }
    this.setState(({fileList}) => ({
      fileList: [...fileList, file]
    }));
    return false;
  };

  onCancelClick = () => {
    const {onCancel} = this.props;
    onCancel && onCancel();
  };

  onSubmitClick = async () => {
    const {
      title,
      description,
      fileList,
      renderAsModal
    } = this.state;
    const {onSave} = this.props;
    const base64Files = fileList && fileList.length > 0
      ? await blobFilesToBase64(fileList)
      : {};
    const payload = {
      title,
      description,
      ...(base64Files.length > 0 && {attachments: base64Files})
    };
    onSave && onSave(payload, !renderAsModal);
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
          padding: '0 10px',
          overflowWrap: 'break-word'
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
    const {
      renderAsModal,
      pending
    } = this.props;
    return (
      <div
        className={styles.editorContainer}
      >
        <Input
          value={title}
          disabled={pending}
          onChange={this.onChangeValue('title', 'input')}
          placeholder="Title"
          style={{marginBottom: 10}}
        />
        <div>
          <Radio.Group
            value={mode}
            onChange={this.onChangeMode}
            disabled={pending}
          >
            {Object.values(PREVIEW_MODES).map((key) => (
              <Radio.Button
                key={key}
                value={key}
                style={{
                  textTransform: 'capitalize',
                  borderRadius: '4px 4px 0 0'
                }}
              >
                {PreviewModeNames[key] || key}
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
                  disabled={pending}
                  onChange={this.onChangeValue('description', 'input', true)}
                  value={description}
                  onClick={e => e.stopPropagation()}
                  placeholder="Leave a comment"
                  style={{
                    resize: 'none',
                    borderRadius: '0 4px 0 0'
                  }}
                />
                {!renderAsModal ? (
                  <Button
                    className={styles.submitButton}
                    disabled={this.submitDisabled}
                    onClick={this.onSubmitClick}
                  >
                    Submit new ticket
                  </Button>
                ) : null}
              </div>
              <Upload
                style={{width: '100%'}}
                fileList={fileList}
                onRemove={this.onRemoveFile}
                beforeUpload={this.beforeUpload}
              >
                <Button
                  className={styles.uploadButton}
                  disabled={pending}
                >
                  <Icon type="upload" /> Upload attachment
                </Button>
              </Upload>
            </div>
          ) : (
            this.renderPreview()
          )}
        </div>
      </div>
    );
  };

  render () {
    const {renderAsModal, modalVisible, title} = this.props;
    if (renderAsModal) {
      return (
        <Modal
          title={title}
          visible={modalVisible}
          onCancel={this.onCancelClick}
          width="50%"
          footer={(
            <div className={styles.modalFooter}>
              <Button
                onClick={this.onCancelClick}
              >
                Cancel
              </Button>
              <Button
                type="primary"
                disabled={this.submitDisabled}
                onClick={this.onSubmitClick}
              >
                Submit new ticket
              </Button>
            </div>
          )}
        >
          <div className={styles.container}>
            <div className={styles.content}>
              {this.renderTicketEditor()}
            </div>
          </div>
        </Modal>
      );
    }
    return (
      <div className={styles.container}>
        <div className={styles.content}>
          {this.renderTicketEditor()}
        </div>
      </div>
    );
  }
}

NewTicketForm.propTypes = {
  onSave: PropTypes.func,
  onCancel: PropTypes.func,
  title: PropTypes.string,
  pending: PropTypes.bool,
  renderAsModal: PropTypes.bool,
  modalVisible: PropTypes.bool
};

export default NewTicketForm;
