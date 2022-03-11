/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import {observer} from 'mobx-react';
import {
  Alert,
  Row,
  Button,
  Icon,
  Spin,
  Input,
  message
} from 'antd';
import localization from '../../../../../utils/localization';
import {SplitPanel} from '../../../../special/splitPanel';
import PipelineCodeForm from '../../../version/code/forms/PipelineCodeForm';
import VSHistory from '../history';
import downloadPipelineFile from '../../../version/utilities/download-pipeline-file';
import parsePipelineFile from '../../../version/utilities/parse-pipeline-file';
import PipelineFileUpdate from '../../../../../models/pipelines/PipelineFileUpdate';
import styles from './info-panel.css';

const MAX_SIZE_TO_PREVIEW = 1024 * 75; // 75kb
const CONTENT_INFO = [{
  key: 'preview',
  containerStyle: {
    display: 'flex',
    flexDirection: 'column'
  },
  size: {
    priority: 0,
    percentDefault: 33,
    pxMinimum: 100
  }
}, {
  key: 'history',
  containerStyle: {
    display: 'flex',
    flexDirection: 'column'
  },
  size: {
    priority: 2,
    percentDefault: 66,
    pxMinimum: 200
  }
}];

function fillEmptyCells (count, cb) {
  const result = [];
  for (let i = 0; i < count; i++) {
    result.push(cb(i));
  }
  return result;
}

@localization.localizedComponent
@observer
class InfoPanel extends localization.LocalizedReactComponent {
  state = {
    inProgress: false,
    fileIsFetching: false,
    fileFetchingError: undefined,
    binaryFile: false,
    tabularFile: false,
    fileContent: null,
    fileEditable: true,
    fileSizeExceeded: false,
    filePreviewVisible: false
  };

  componentDidMount () {
    this.getFileContent();
  };

  componentDidUpdate (prevProps) {
    if (prevProps.file !== this.props.file) {
      this.getFileContent();
    }
  }

  get previewAvailable () {
    const {file} = this.props;
    if (file) {
      const {fileSizeExceeded, fileEditable} = this.state;
      return !fileSizeExceeded && fileEditable;
    }
    return false;
  };

  openFilePreview = () => {
    this.setState({
      filePreviewVisible: true
    });
  };

  closeFilePreview = () => {
    this.setState({
      filePreviewVisible: false
    });
  };

  handleGoBackClick = () => {
    const {onGoBack} = this.props;
    onGoBack && onGoBack();
  };

  getFileContent = () => {
    const {
      pipelineId,
      lastCommitId,
      file
    } = this.props;
    if (
      !pipelineId ||
      !file ||
      !file.path ||
      file.size > MAX_SIZE_TO_PREVIEW ||
      !lastCommitId
    ) {
      this.setState({
        fileIsFetching: false,
        fileFetchingError: undefined,
        fileContent: undefined,
        binaryFile: false,
        tabularFile: false,
        filePreviewVisible: false
      });
      return null;
    }
    this.setState({
      fileIsFetching: true
    }, () => {
      parsePipelineFile(pipelineId, lastCommitId, file.path, MAX_SIZE_TO_PREVIEW)
        .then(info => {
          const {
            content,
            error,
            binary,
            tabular
          } = info;
          this.setState({
            fileContent: content,
            binaryFile: binary,
            tabularFile: tabular,
            fileFetchingError: error,
            fileIsFetching: false,
            filePreviewVisible: false
          });
        });
    });
  };

  renderDownloadLink = (description) => {
    const {file, pipelineId, lastCommitId} = this.props;
    if (!file || !pipelineId || !lastCommitId) {
      return null;
    }
    const handleDownload = () => downloadPipelineFile(pipelineId, lastCommitId, file.path);
    return (
      <span>
        <span
          className={styles.downloadBtn}
          onClick={handleDownload}
        >
          Download file
        </span>
        {description}
      </span>
    );
  };

  renderPreviewHeader = () => {
    const {file} = this.props;
    if (!file) {
      return null;
    }
    const {
      fileFetchingError,
      fileIsFetching,
      binaryFile
    } = this.state;
    const {fileEditable} = this.state;
    let content;
    if (fileIsFetching) {
      content = (
        <Row type="flex" justify="center">
          <Spin />
        </Row>
      );
    } else if (fileFetchingError) {
      content = (
        <Row
          type="flex"
          style={{color: '#777', marginTop: 5, marginBottom: 5}}
        >
          <Alert
            type="error"
            message={(
              <Row>
                <span style={{marginRight: 5}}>
                  {fileFetchingError}
                </span>
                {this.renderDownloadLink()}
              </Row>
            )}
          />
        </Row>
      );
    } else if (file.size > MAX_SIZE_TO_PREVIEW || binaryFile) {
      content = (
        <Row
          type="flex"
          style={{color: '#777', marginTop: 5, marginBottom: 5}}
        >
          <span style={{marginRight: 5}}>
            File preview is not available.
          </span>
          {this.renderDownloadLink('to view full contents')}
        </Row>
      );
    } else {
      content = (
        <Row
          type="flex"
          justify="space-between"
          align="middle"
          className={styles.previewHeaderRow}
        >
          <b>File preview</b>
          <Button
            size="small"
            onClick={this.openFilePreview}
            disabled={!fileEditable}
            className={styles.previewHeaderBtn}
          >
            <Icon type="arrows-alt" />
          </Button>
        </Row>
      );
    }
    return (
      <div className={styles.previewHeaderContainer}>
        <Row
          type="flex"
          justify="space-between"
          align="middle"
          className={styles.previewHeaderRow}
        >
          <div style={{flex: 1}}>
            <Button
              size="small"
              className={styles.goBackHeaderBtn}
              onClick={this.handleGoBackClick}
            >
              <Icon type="left" />
            </Button>
            <b>{file.name}</b>
          </div>
          {/* button removed until blind/unblind api will be ready */}
          {/* <Button
            size="small"
            className={styles.previewHeaderBtn}
            disabled
          >
            <Icon type="eye" />
          </Button> */}
        </Row>
        {content}
      </div>
    );
  };

  renderFilePreview = () => {
    const {file} = this.props;
    const {
      binaryFile,
      tabularFile,
      fileContent,
      fileFetchingError,
      fileIsFetching
    } = this.state;
    if (
      !file ||
      binaryFile ||
      fileFetchingError ||
      file.size > MAX_SIZE_TO_PREVIEW
    ) {
      return null;
    }
    if (tabularFile && tabularFile.length > 0) {
      const columnsLength = tabularFile
        .reduce((length, row) => Math.max(length, row.length), 0);
      return (
        <div className={styles.tabularFileContainer}>
          <table className={styles.tabular}>
            <thead>
              <tr>
                <th>
                  {'\u00A0'}
                </th>
                {tabularFile[0].map((cell, index) => (
                  <th key={`column-${index}`}>
                    {cell}
                  </th>
                ))}
                {
                  fillEmptyCells(
                    columnsLength - tabularFile[0].length,
                    index => (
                      <th key={`column-${index + tabularFile[0].length}`}>
                        {'\u00A0'}
                      </th>
                    )
                  )
                }
              </tr>
            </thead>
            <tbody>
              {
                tabularFile.slice(1).map((columns, row) => (
                  <tr key={`row-${row}`}>
                    <th key={`row-${row}`}>
                      {row + 1}
                    </th>
                    {columns.map((cell, column) => (
                      <td key={`cell-${row}-${column}`}>
                        {cell}
                      </td>
                    ))}
                    {
                      fillEmptyCells(
                        columnsLength - columns.length,
                        index => (
                          <td key={`cell-${row}-${index + columns.length}`}>
                            {'\u00A0'}
                          </td>
                        )
                      )
                    }
                  </tr>
                ))
              }
            </tbody>
          </table>
        </div>
      );
    } else {
      return (
        <Input.TextArea
          disabled={fileIsFetching}
          spellCheck="false"
          autoComplete="off"
          autoCorrect="off"
          autoCapitalize="off"
          autosize={false}
          className={styles.filePreviewInput}
          value={fileContent || (fileIsFetching && '') || 'empty'}
          style={
            fileContent
              ? {height: '100%'}
              : {color: '#aaa', height: '100%', fontStyle: 'italic'}
          }
          readOnly
        />
      );
    }
  };

  saveFile = async (contents, comment) => {
    const {
      pipelineId,
      onRefresh,
      file,
      lastCommitId
    } = this.props;
    if (!file) {
      return;
    }
    const request = new PipelineFileUpdate(pipelineId);
    try {
      const hide = message.loading('Committing file changes...');
      await request.send({
        contents,
        comment,
        path: file.path,
        lastCommitId
      });
      hide();
      if (request.error) {
        throw new Error(request.error);
      }
      this.closeFilePreview();
      if (onRefresh) {
        onRefresh();
      }
    } catch (e) {
      message.error(e.error, 5);
    }
  };

  render () {
    const {
      lastCommitId,
      pipelineId,
      path,
      file,
      readOnly,
      onRefresh
    } = this.props;
    const {
      filePreviewVisible
    } = this.state;
    return (
      <SplitPanel
        style={{overflow: 'auto'}}
        contentInfo={CONTENT_INFO}
        orientation="vertical"
      >
        {
          file && (
            <div
              key="preview"
              className={styles.previewContainer}
            >
              {this.renderPreviewHeader()}
              {this.renderFilePreview()}
            </div>
          )
        }
        <VSHistory
          key="history"
          path={file ? file.path : path}
          versionedStorageId={pipelineId}
          revision={lastCommitId}
          isFolder={!file}
          onRefresh={onRefresh}
          readOnly={readOnly}
          style={{
            flex: 1
          }}
        />
        <PipelineCodeForm
          visible={filePreviewVisible && !!file}
          path={file ? file.path : undefined}
          pipelineId={pipelineId}
          editable={!readOnly}
          download
          cancel={this.closeFilePreview}
          save={this.saveFile}
          version={lastCommitId}
        />
      </SplitPanel>
    );
  }
}

InfoPanel.propTypes = {
  file: PropTypes.object,
  path: PropTypes.string,
  pipelineId: PropTypes.oneOfType([
    PropTypes.string,
    PropTypes.number
  ]),
  lastCommitId: PropTypes.string,
  pending: PropTypes.bool,
  readOnly: PropTypes.bool,
  onRefresh: PropTypes.func,
  onGoBack: PropTypes.func
};

export default InfoPanel;
