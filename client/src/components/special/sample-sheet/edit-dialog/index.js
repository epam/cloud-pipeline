/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import {
  Button,
  Modal,
  message,
  Switch,
  Icon,
  Upload
} from 'antd';
import SampleSheet from '../edit-form';
import CodeEditor from '../../CodeEditor';
import {isSampleSheetContent} from '../utilities';
import styles from './sample-sheet-edit-dialog.css';
import readBlobContents from '../../../../utils/read-blob-contents';

class SampleSheetEditDialog extends React.Component {
  state = {
    textMode: false,
    content: undefined
  };

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.visible !== this.props.visible && this.props.visible) {
      this.updateFromProps();
    }
  }

  updateFromProps = () => {
    const {content} = this.props;
    this.setState({content, textMode: false}, () => {
      if (this.editor) {
        this.editor.clear();
      }
    });
  }

  initializeEditor = (editor) => {
    this.editor = editor;
  };

  toggleTextMode = (textMode) => {
    this.setState({textMode});
  };

  onChange = (content) => {
    this.setState({
      content
    });
  }

  onSaveClicked = () => {
    const {content} = this.state;
    if (isSampleSheetContent(content)) {
      const {onSave} = this.props;
      if (onSave) {
        onSave(content);
      }
    } else {
      message.error('Not a valid SampleSheet', 5);
    }
  };

  onUpload = (file) => {
    readBlobContents(file)
      .then((content) => {
        if (isSampleSheetContent(content)) {
          this.setState({content});
        } else {
          throw new Error(`${file.name} is not a valid SampleSheet`);
        }
      })
      .catch(e => message.error(e.message, 5));
    return false;
  };

  render () {
    const {
      visible,
      onClose,
      title,
      removable,
      onRemove
    } = this.props;
    const {
      content,
      textMode
    } = this.state;
    return (
      <Modal
        visible={visible}
        onCancel={onClose}
        closable={false}
        width="80%"
        style={{
          top: 50
        }}
        bodyStyle={{
          height: 'calc(100vh - 200px)',
          overflow: 'auto'
        }}
        title={(
          <div
            className={styles.title}
          >
            <b>{title || 'Edit SampleSheet'}</b>
            <div>
              <span style={{marginRight: 5}}>View as text</span>
              <Switch onChange={this.toggleTextMode} />
              <div style={{display: 'inline-flex', marginLeft: 5}}>
                <Upload
                  fileList={[]}
                  key="upload"
                  beforeUpload={this.onUpload}
                  multiple={false}
                >
                  <Button>
                    <Icon type="upload" /> Upload
                  </Button>
                </Upload>
              </div>
            </div>
          </div>
        )}
        footer={(
          <div className={styles.footer}>
            <div>
              {
                removable && (
                  <Button
                    type="danger"
                    onClick={onRemove}
                  >
                    REMOVE
                  </Button>
                )
              }
            </div>
            <div>
              <Button
                onClick={onClose}
              >
                CANCEL
              </Button>
              <Button
                type="primary"
                onClick={this.onSaveClicked}
              >
                SAVE
              </Button>
            </div>
          </div>
        )}
      >
        {
          textMode && visible && (
            <CodeEditor
              ref={this.initializeEditor}
              code={content}
              onChange={this.onChange}
              supportsFullScreen
              language="text"
              fileName="SampleSheet.csv"
              delayedUpdate
            />
          )
        }
        {
          !textMode && visible && (
            <SampleSheet
              editable
              content={content}
              onChange={this.onChange}
            />
          )
        }
      </Modal>
    );
  }
}

SampleSheetEditDialog.propTypes = {
  content: PropTypes.string,
  removable: PropTypes.bool,
  disabled: PropTypes.bool,
  onClose: PropTypes.func,
  onSave: PropTypes.func,
  onRemove: PropTypes.func,
  title: PropTypes.string,
  visible: PropTypes.bool
};

export default SampleSheetEditDialog;
