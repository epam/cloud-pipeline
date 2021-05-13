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
  Row,
  Button,
  Icon,
  Spin,
  message,
  Input
} from 'antd';
import VersionFile from '../../../../../models/pipelines/VersionFile';
import localization from '../../../../../utils/localization';
import {SplitPanel} from '../../../../special/splitPanel/SplitPanel';
import styles from './info-panel.css';

const MAX_SIZE_TO_PREVIEW = 1000000;
const CONTENT_INFO = [{
  key: 'preview',
  closable: false,
  containerStyle: {
    display: 'flex',
    flexDirection: 'column'
  },
  size: {
    priority: 0,
    percentDefault: 50,
    pxMinimum: 200
  }
}, {
  key: 'history',
  title: 'Revision history',
  closable: false,
  containerStyle: {
    display: 'flex',
    flexDirection: 'column'
  },
  size: {
    keepPreviousSize: true,
    priority: 2,
    percentDefault: 51,
    pxMinimum: 200
  }
}];
const PREVIEW_TYPES = [
  '.txt',
  '.csv'
];

function checkFileSize (file) {
  if (!file || file.size === undefined) {
    return false;
  }
  return file.size > MAX_SIZE_TO_PREVIEW;
}

function checkFileValidity (file) {
  if (!file || file.size === undefined) {
    return false;
  }
  return PREVIEW_TYPES.some(type => file.name.endsWith(type));
}

@localization.localizedComponent
@observer
class InfoPanel extends localization.LocalizedReactComponent {
  state = {
    inProgress: false,
    fileContent: null,
    editFile: false,
    fileEditable: true,
    fileSizeExceeded: false
  }

  componentDidMount () {
    const {file} = this.props;
    if (file) {
      this.checkFileAndLoad(file);
    }
  };

  componentWillReceiveProps (nextProps) {
    const {file} = this.props;
    if (nextProps.file !== file) {
      this.checkFileAndLoad(nextProps.file);
    }
  };

  get previewAvailable () {
    const {fileSizeExceeded, fileEditable} = this.state;
    return !fileSizeExceeded && fileEditable;
  };

  checkFileAndLoad = (file) => {
    if (!file) {
      return null;
    }
    this.setState({
      fileEditable: checkFileValidity(file),
      fileSizeExceeded: checkFileSize(file)
    }, () => {
      this.getFileContent(file);
    });
  };

  handleFileEdit = () => {
    const {onFileEdit} = this.props;
    onFileEdit && onFileEdit();
  };

  getFileContent = (file) => {
    const {
      pipelineId,
      lastCommitId
    } = this.props;
    if (!pipelineId || !file || !file.id || !lastCommitId) {
      return null;
    }
    if (this.previewAvailable) {
      const request = new VersionFile(
        pipelineId,
        file.path,
        lastCommitId
      );
      this.setState({
        inProgress: true
      }, () => {
        const reject = error => {
          this.setState({inProgress: false});
          message.error(error, 5);
        };
        const resolve = result => {
          this.setState({
            fileContent: atob(result),
            inProgress: false
          });
        };
        request
          .fetch()
          .then(() => {
            if (request.error) {
              reject(request.error || `Error fetching ${file.id} content`);
            } else {
              resolve(request.response);
            }
          })
          .catch(e => reject(e.message));
      });
    } else {
      this.setState({
        fileContent: ''
      });
    }
  };

  renderDownloadLink = () => {
    const {file, onFileDownload} = this.props;
    return (
      <span>
        <span
          className={styles.downloadBtn}
          onClick={() => onFileDownload(file)}
        >
          Download file
        </span>
        to view full contents
      </span>
    );
  };

  renderPreviewHeader = () => {
    const {file} = this.props;
    const {fileEditable} = this.state;
    if (!fileEditable) {
      return (
        <Row
          type="flex"
          style={{color: '#777', marginTop: 5, marginBottom: 5}}
        >
          <span style={{marginRight: '5px'}}>
            File preview is not available.
          </span>
          {this.renderDownloadLink()}
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
          <b>{file.name}</b>
          <Button
            size="small"
            className={styles.previewHeaderBtn}
            disabled
          >
            <Icon type="eye" />
          </Button>
        </Row>
        <Row
          type="flex"
          justify="space-between"
          align="middle"
          className={styles.previewHeaderRow}
        >
          <b>File preview</b>
          <Button
            size="small"
            onClick={this.handleFileEdit}
            disabled={!fileEditable}
            className={styles.previewHeaderBtn}
          >
            <Icon type="arrows-alt" />
          </Button>
        </Row>
      </div>
    );
  };

  renderFilePreview = () => {
    const {fileContent} = this.state;
    if (typeof fileContent !== 'string' || !this.previewAvailable) {
      return null;
    }
    return (
      <Input
        spellCheck="false"
        autoComplete="off"
        autoCorrect="off"
        autoCapitalize="off"
        style={{height: '100%'}}
        type="textarea"
        className={styles.filePreviewInput}
        value={fileContent}
        readOnly
      />
    );
  };

  render () {
    const {pending} = this.props;
    const {inProgress} = this.state;
    if (pending || inProgress) {
      return (
        <Row type="flex" justify="center">
          <Spin />
        </Row>
      );
    }
    return (
      <div
        style={{flex: 1, flexDirection: 'column', overflow: 'auto'}}
      >
        <SplitPanel
          style={{flex: 1, overflow: 'auto', height: 'calc(100vh - 150px)'}}
          contentContainerStyle={{overflow: 'inherit', flex: 1}}
          contentInfo={CONTENT_INFO}
          orientation="vertical"
        >
          <div
            key="preview"
            className={styles.previewContainer}
          >
            {this.renderPreviewHeader()}
            {this.renderFilePreview()}
          </div>
          <div
            key="history"
          >
            History
          </div>
        </SplitPanel>
      </div>
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
  onFileEdit: PropTypes.func,
  onFileDownload: PropTypes.func
};

export default InfoPanel;
