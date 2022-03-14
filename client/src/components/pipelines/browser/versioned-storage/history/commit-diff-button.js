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
import {
  Button,
  Icon,
  message,
  Modal
} from 'antd';
import * as diff2html from 'diff2html';
import VsContentsDiff from '../../../../../models/versioned-storage/vs-contents-diff';
import styles from './history.css';
import 'highlight.js/styles/github.css';
import 'diff2html/bundles/css/diff2html.min.css';

class CommitDiffButton extends React.Component {
  state = {
    diff: undefined,
    diffError: undefined,
    diffPending: false,
    visible: false
  };

  showDiffModal = () => {
    this.fetchDiff()
      .then(() => {
        const {diffError} = this.state;
        if (diffError) {
          message.error(diffError, 5);
        } else {
          this.setState({
            visible: true
          });
        }
      });
  };

  hideDiffModal = () => {
    this.setState({
      diff: undefined,
      diffError: undefined,
      diffPending: false,
      visible: false
    });
  };

  fetchDiff = () => {
    const {
      commit,
      versionedStorageId,
      path
    } = this.props;
    if (versionedStorageId && commit) {
      return new Promise((resolve) => {
        const hide = message.loading('Fetching diff...', 0);
        this.setState({
          diffPending: true
        }, () => {
          const request = new VsContentsDiff(versionedStorageId, commit, path);
          request
            .fetch()
            .then(() => {
              if (request.error || !request.loaded) {
                throw new Error(request.error || 'Error fetching diffs');
              } else {
                const {
                  diff = undefined
                } = request.value;
                if (!diff) {
                  this.setState({
                    diff: undefined,
                    diffError: undefined,
                    diffPending: false
                  });
                } else {
                  const diffJson = diff2html.parse(diff);
                  const diffHtml = diff2html.html(
                    diffJson,
                    {
                      drawFileList: false,
                      outputFormat: 'line-by-line',
                      highlight: true
                    });
                  this.setState({
                    diff: diffHtml,
                    diffError: undefined,
                    diffPending: false
                  });
                }
              }
            })
            .catch(e => {
              this.setState({
                diff: undefined,
                diffError: e.message,
                diffPending: false
              });
            })
            .then(hide)
            .then(resolve);
        });
      });
    } else {
      return Promise.resolve();
    }
  };

  renderPresentation = () => {
    const {diff: rawHtml} = this.state;
    if (rawHtml) {
      return (
        <div
          key="presentation"
          dangerouslySetInnerHTML={{
            __html: rawHtml
          }}
        />
      );
    }
    return (
      <div className={classNames(styles.emptyContent, 'cp-text-not-important')}>
        No content
      </div>
    );
  };

  render () {
    const {
      className,
      commit,
      disabled,
      style,
      versionedStorageId
    } = this.props;
    const {
      diffPending,
      visible
    } = this.state;
    return (
      <div
        className={className}
        style={style}
      >
        <Button
          size="small"
          className={styles.button}
          disabled={
            disabled ||
            !versionedStorageId ||
            !commit ||
            diffPending
          }
          onClick={this.showDiffModal}
        >
          <Icon type="left" />
          <Icon type="right" />
        </Button>
        <Modal
          title={commit ? `Difference for ${commit}` : undefined}
          visible={visible}
          onCancel={this.hideDiffModal}
          footer={false}
          width="80%"
        >
          {this.renderPresentation()}
        </Modal>
      </div>
    );
  }
}

CommitDiffButton.propTypes = {
  className: PropTypes.string,
  commit: PropTypes.string,
  disabled: PropTypes.bool,
  path: PropTypes.string,
  versionedStorageId: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  style: PropTypes.object
};

export default CommitDiffButton;
