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
import classNames from 'classnames';
import {Icon, message, Button} from 'antd';
import CommitDiffButton from './commit-diff-button';
import UserName from '../../../../special/UserName';
import displayDate from '../../../../../utils/displayDate';
import PipelineCodeForm from '../../../version/code/forms/PipelineCodeForm';
import styles from './history.css';
import RevertCommitForm from '../forms/revert-commit-form';
import PipelineFileRevert from '../../../../../models/pipelines/PipelineFileRevert';

const MAX_SIZE_TO_PREVIEW = 1024 * 75; // 75kb

class CommitCard extends React.PureComponent {
  state = {
    file: undefined,
    filePreviewVisible: false,
    revertDialogVisible: false,
    pending: false
  };

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.path !== this.props.path) {
      this.updateFromProps();
    }
  }

  updateFromProps = () => {
    const {path} = this.props;
    if (path) {
      const fileName = (path || '').split(/[\\/]/).pop();
      this.setState({
        file: {
          name: fileName,
          path
        }
      });
    } else {
      this.setState({file: undefined});
    }
  };

  showFilePreview = () => {
    this.setState({filePreviewVisible: true});
  }

  hideFilePreview = () => {
    this.setState({filePreviewVisible: false});
  };

  openRevertDialog = () => {
    this.setState({revertDialogVisible: true});
  }

  closeRevertDialog = () => {
    this.setState({revertDialogVisible: false});
  }

  onRevert = (payload) => {
    const {comment} = payload;
    const hide = message.loading(`Reverting...`, 0);
    const {
      versionedStorageId,
      path,
      commit,
      onRefresh
    } = this.props;
    this.setState({
      pending: true
    }, async () => {
      const state = {
        pending: false
      };
      try {
        const payload = {
          path,
          commitToRevert: commit?.commit,
          comment
        };
        const request = new PipelineFileRevert(versionedStorageId);
        await request.send(payload);
        if (request.error) {
          throw new Error(request.error);
        }
        state.revertDialogVisible = false;
        onRefresh && onRefresh();
      } catch (e) {
        message.error(e.message, 5);
      } finally {
        hide();
        this.setState(state);
      }
    });
  };

  render () {
    const {
      className,
      commit,
      disabled = false,
      path,
      style,
      versionedStorageId,
      isFile = false,
      isLatestFileCommit = false,
      readOnly
    } = this.props;
    const {
      filePreviewVisible,
      pending,
      revertDialogVisible
    } = this.state;
    if (!commit) {
      return null;
    }
    return (
      <div
        className={
          classNames(
            styles.commit,
            'cp-table-element',
            'cp-even-odd-element',
            {
              [styles.disabled]: disabled,
              'cp-table-element-disabled': disabled
            },
            className
          )
        }
        style={style}
      >
        <div className={styles.commitControls}>
          {
            isFile && !isLatestFileCommit && !readOnly && (
              <Button
                size="small"
                style={{marginRight: '2px'}}
                onClick={this.openRevertDialog}
              >
                <Icon
                  type="retweet"
                />
              </Button>
            )
          }
          {
            isFile && (
              <Button
                onClick={this.showFilePreview}
                size="small"
                style={{marginRight: '2px'}}
              >
                <Icon
                  type="download"
                />
              </Button>
            )
          }
          <CommitDiffButton
            disabled={disabled}
            commit={commit?.commit}
            path={path}
            versionedStorageId={versionedStorageId}
          />
        </div>
        <div>
          {
            commit.commit_message && (
              <div
                className={
                  classNames(
                    styles.line,
                    styles.message,
                    'cp-accent'
                  )
                }
              >
                {commit.commit_message} <br />
              </div>
            )
          }
          <div
            className={styles.line}
          >
            {
              commit.commit && (
                <span
                  className={styles.sha}
                >
                  <Icon type="tag" />
                  {commit.commit.slice(0, 7)}
                </span>
              )
            }
            <UserName
              className={
                classNames(
                  styles.author,
                  'cp-accent'
                )
              }
              userName={commit.author}
            />
            {
              commit.committer_date && (
                <span
                  className={styles.date}
                >
                  committed on {displayDate(commit.committer_date, 'MMM D, YYYY, HH:mm')}
                </span>
              )
            }
          </div>
        </div>
        <PipelineCodeForm
          path={path}
          pipelineId={versionedStorageId}
          visible={isFile && filePreviewVisible}
          version={commit.commit}
          cancel={this.hideFilePreview}
          download
          vsStorage
          byteLimit={MAX_SIZE_TO_PREVIEW}
        />
        <RevertCommitForm
          path={path}
          visible={revertDialogVisible}
          onRevert={this.onRevert}
          pending={pending}
          commit={commit.commit}
          onCancel={this.closeRevertDialog}
        />
      </div>
    );
  }
}

CommitCard.propTypes = {
  className: PropTypes.string,
  commit: PropTypes.object,
  disabled: PropTypes.bool,
  path: PropTypes.string,
  style: PropTypes.object,
  readOnly: PropTypes.bool,
  versionedStorageId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  isFile: PropTypes.bool,
  isLatestFileCommit: PropTypes.bool,
  onRefresh: PropTypes.func
};

export default CommitCard;
