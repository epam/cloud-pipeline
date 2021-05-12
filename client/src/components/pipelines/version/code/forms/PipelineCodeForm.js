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
import {inject, observer} from 'mobx-react';
import {observable} from 'mobx';
import classNames from 'classnames';
import PropTypes from 'prop-types';
import {Switch, Alert, Button, Row, Col, Modal, Spin} from 'antd';
import CodeFileCommitForm from './CodeFileCommitForm';
import CodeEditor from '../../../../special/CodeEditor';
import HotTable from 'react-handsontable';
import Papa from 'papaparse';
import downloadPipelineFile from '../../utilities/download-pipeline-file';
import parsePipelineFile from '../../utilities/parse-pipeline-file';
import styles from './PipelineCodeForm.css';

@inject(({routing}) => ({
  routing
}))
@observer
class PipelineCodeForm extends React.PureComponent {
  static propTypes = {
    path: PropTypes.string,
    version: PropTypes.string,
    cancel: PropTypes.func,
    save: PropTypes.func,
    pipelineId: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
    editable: PropTypes.bool,
    download: PropTypes.bool,
    showRevision: PropTypes.bool,
    visible: PropTypes.bool,
    byteLimit: PropTypes.number
  };
  static defaultProps = {
    editable: false,
    download: false,
    showRevision: false
  };

  editor;
  hotEditor;
  _parseOpts = {};

  state = {
    pending: false,
    error: undefined,
    editMode: false,
    commitMessageForm: false,
    editTabularAsText: false
  };

  _modifiedCode = null;
  @observable _originalCode = null;

  componentDidMount () {
    if (this.props.visible) {
      this.fetchDocumentContents();
    }
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.path !== this.props.path ||
      prevProps.pipelineId !== this.props.pipelineId ||
      prevProps.version !== this.props.version ||
      (prevProps.visible !== this.props.visible && this.props.visible)
    ) {
      this.fetchDocumentContents();
    }
  }

  fetchDocumentContents = () => {
    const {pipelineId, version, path, byteLimit} = this.props;
    if (pipelineId && version && path) {
      this.setState({
        pending: true,
        error: undefined
      }, async () => {
        const state = {
          pending: false,
          error: undefined
        };
        try {
          const {
            content,
            binary,
            error
          } = await parsePipelineFile(pipelineId, version, path, byteLimit);
          if (binary) {
            throw new Error('File preview is not available (binary content)');
          }
          if (error) {
            throw new Error(error);
          }
          this._originalCode = content;
        } catch (e) {
          this._originalCode = null;
          state.error = e.message;
        } finally {
          this._modifiedCode = null;
          this.setState(state);
        }
      });
    } else {
      this._originalCode = '';
      this._modifiedCode = null;
    }
  }

  onCodeChange = (newText) => {
    this._modifiedCode = newText;
  };

  onDownload = () => {
    const {path, version, pipelineId} = this.props;
    if (
      pipelineId &&
      version &&
      path
    ) {
      return downloadPipelineFile(pipelineId, version, path);
    }
  };

  onClose = () => {
    const close = () => {
      this.setState({
        editMode: false,
        editTabularAsText: false
      });
      this._modifiedCode = this._originalCode;
      if (this.editor) {
        this.editor.clear();
      }
      this.props.cancel();
    };
    if (this.hotEditor && this.state.editMode) {
      this._modifiedCode = this.stringTableData;
    }
    if (this._modifiedCode && this._originalCode !== this._modifiedCode) {
      Modal.confirm({
        title: 'All changes will be lost. Continue?',
        style: {
          wordWrap: 'break-word'
        },
        onOk () {
          close();
        }
      });
    } else {
      close();
    }
  };

  doCommit = (options) => {
    this.setState({
      editMode: false,
      editTabularAsText: false
    }, () => {
      this.closeCommitForm();
      this.props.save && this.props.save(this._modifiedCode, options.message);
    });
  };

  closeCommitForm = () => {
    this.setState({commitMessageForm: false});
  };

  toggleEditMode = async () => {
    if (this.hotEditor && this.state.editMode) {
      this._modifiedCode = this.stringTableData;
    }
    if (!this.state.editMode) {
      this._modifiedCode = null;
      this.setState({editMode: !this.state.editMode});
    } else if (this._modifiedCode !== null && this._modifiedCode !== this._originalCode) {
      this.setState({commitMessageForm: true});
    } else {
      this._modifiedCode = null;
      this.setState({editMode: !this.state.editMode});
    }
  };

  toggleEditTabularAsText = (editTabularAsText) => {
    if (this.hotEditor && this.state.editMode) {
      this._modifiedCode = this.stringTableData;
    }
    this.setState({editTabularAsText});
  };

  get isEditable () {
    const {editable, pipelineId, version} = this.props;
    return editable && pipelineId && version;
  }

  initializeEditor = (editor) => {
    this.editor = editor;
  };

  initializeTableEditor = (tableEditor) => {
    this.hotEditor = tableEditor;
  };

  get fileName () {
    const {path} = this.props;
    if (!path) {
      return undefined;
    }
    return (path || '').split(/[\\/]/).pop();
  }

  get fileType () {
    if (!this.fileName) {
      return null;
    }
    const extension = this.fileName.split('.').pop().toLowerCase();
    switch (extension) {
      case 'dsv': return 'dsv';
      case 'tsv': return 'tsv';
      case 'csv': return 'csv';
      case 'txt':
      default: return 'text';
    }
  }

  get isTabular () {
    return this.fileType === 'dsv' ||
      this.fileType === 'tsv' ||
      this.fileType === 'csv';
  }

  get structuredTableData () {
    const result = {};
    const csvString = this._modifiedCode !== null
      ? this._modifiedCode
      : (this._originalCode || '');
    const parseRes = Papa.parse(csvString);

    if (parseRes.errors.length) {
      const firstErr = parseRes.errors.shift();
      result.error = true;
      result.message = `${firstErr.code}: ${firstErr.message}. at row ${firstErr.row + 1}`;

      return result;
    }

    if (parseRes.meta && parseRes.meta.delimiter) {
      this._parseOpts.delimiter = parseRes.meta.delimiter;
    }
    if (parseRes.meta && parseRes.meta.linebreak) {
      this._parseOpts.linebreak = parseRes.meta.linebreak;
    }

    result.data = parseRes.data;

    return result;
  }

  get stringTableData () {
    const unparseConfig = {};
    if (this._parseOpts && this._parseOpts.delimiter) {
      unparseConfig.delimiter = this._parseOpts.delimiter;
    }
    if (this._parseOpts && this._parseOpts.linebreak) {
      unparseConfig.newline = this._parseOpts.linebreak;
    }
    return Papa.unparse(this.hotEditor.hotInstance.getData(), unparseConfig);
  }

  get fileEditor () {
    const {pending, error} = this.state;
    if (pending) {
      return null;
    }
    if (error) {
      return (
        <Alert
          message={error}
          type="error"
        />
      );
    }
    if (this.isTabular && !this.state.editTabularAsText) {
      this._tableData = this.structuredTableData;

      return !this._tableData.error
        ? (
          <HotTable
            root="hot"
            ref={this.initializeTableEditor}
            data={this._tableData.data}
            colHeaders
            rowHeaders
            readOnly={!this.state.editMode}
            readOnlyCellClassName={classNames('readonly-cell', 'cp-table-cell')}
            manualColumnResize
            manualRowResize
            contextMenu={this.state.editMode
              ? [
                'row_above',
                'row_below',
                '---------',
                'col_left',
                'col_right',
                '---------',
                'remove_row',
                'remove_col',
                '---------',
                'undo',
                'redo',
                '---------',
                'cut',
                'copy'
              ] : [
                'copy'
              ]}
          />
        )
        : (
          <Alert
            message={`Error parsing tabular file ${this.fileName || ''}:
              ${this._tableData.message}`}
            type="error" />
        );
    } else {
      return (
        <CodeEditor
          ref={this.initializeEditor}
          readOnly={!this.state.editMode}
          code={this._modifiedCode !== null ? this._modifiedCode : (this._originalCode || '')}
          onChange={this.onCodeChange}
          supportsFullScreen
          language={this.state.editTabularAsText ? 'text' : undefined}
          fileName={this.fileName}
          delayedUpdate
        />
      );
    }
  }

  render () {
    const {
      showRevision,
      download,
      path,
      name,
      visible
    } = this.props;
    const {
      pending
    } = this.state;
    const title = path
      ? (
        <Row type="flex" justify="space-between">
          {name ? (<Col>{name}</Col>) : null}
          <Col>
            {showRevision ? `At revision ${this.props.version}:` : ''}
          </Col>
          <Col>
            {
              this.isEditable && this.isTabular &&
              <span>{this.state.editMode ? 'Edit' : 'View'} as text: <Switch
                className={styles.button}
                onChange={(checked) => this.toggleEditTabularAsText(checked)}
              /></span>
            }
            {
              this.isEditable && this.state.editMode &&
              <Button type="primary" className={styles.button} onClick={this.toggleEditMode}>
                Save
              </Button>
            }
            {
              this.isEditable && !this.state.editMode &&
              <Button className={styles.button} onClick={this.toggleEditMode}>
                Edit
              </Button>
            }
            {
              download && !this.state.editMode &&
              (
                <Button
                  className={styles.button}
                  onClick={this.onDownload}
                >
                  Download
                </Button>
              )
            }
            <Button className={styles.button} onClick={this.onClose}>
              Close
            </Button>
          </Col>
        </Row>)
      : null;
    return (
      <Modal
        visible={visible && !!path}
        onCancel={this.onClose}
        width="80%"
        title={title}
        closable={false}
        maskClosable={false}
        footer={false}
        style={{top: 20}}
      >
        <div className={styles.spinContainer}>
          <Spin spinning={pending}>
            <div
              className={
                classNames(
                  styles.editorContainer,
                  styles.tableEditor,
                  {
                    'cp-pipeline-code-editor-readonly': !this.state.editMode
                  }
                )
              }
            >
              { this.fileEditor }
            </div>
          </Spin>
          <CodeFileCommitForm
            visible={this.state.commitMessageForm}
            pending={false} onSubmit={this.doCommit}
            onCancel={this.closeCommitForm}
          />
        </div>
      </Modal>
    );
  }
}

export default PipelineCodeForm;
