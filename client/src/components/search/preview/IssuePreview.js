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
import PropTypes from 'prop-types';
import {computed} from 'mobx';
import {inject, observer} from 'mobx-react';
import {Icon, Row, Tooltip} from 'antd';
import renderHighlights from './renderHighlights';
import renderSeparator from './renderSeparator';
import {PreviewIcons} from './previewIcons';
import styles from './preview.css';
import IssueLoad from '../../../models/issues/IssueLoad';
import moment from 'moment';
import displayDate from '../../../utils/displayDate';
import roleModel from '../../../utils/roleModel';

@roleModel.authenticationInfo
@inject((stores, params) => {
  const {issuesRenderer} = stores;
  const issueInfo = new IssueLoad(params.item.id);

  issueInfo.fetch();

  return {
    issuesRenderer,
    issueInfo
  };
})
@observer
export default class IssuePreview extends React.Component {

  static propTypes = {
    item: PropTypes.shape({
      id: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      parentId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      name: PropTypes.string,
      description: PropTypes.string
    })
  };

  @computed
  get description () {
    if (!this.issue) {
      return null;
    }

    let description = null;
    if (this.issue.author && this.issue.createdDate) {
      description = <span>
        Opened {this.renderDate(this.issue.createdDate)} by <b>{this.renderAuthorName(this.issue.author)}</b>
      </span>;
    } else if (this.issue.author) {
      description = <span>Opened by {this.renderAuthorName(this.issue.author)}</span>;
    } else if (this.issue.createdDate) {
      description = <span>Opened {this.renderDate(this.issue.createdDate)}</span>;
    }

    return description || this.props.item.description;
  }

  @computed
  get issue () {
    if (this.props.issueInfo && this.props.issueInfo.loaded) {
      return {
        author: this.props.issueInfo.value.author,
        createdDate: this.props.issueInfo.value.createdDate,
        text: this.props.issueInfo.value.text
      };
    }
    return null;
  }

  @computed
  get comments () {
    if (this.props.issueInfo && this.props.issueInfo.loaded) {
      return (this.props.issueInfo.value.comments || []).map(c => c);
    }
    return [];
  }

  isMine = (author) => {
    if (this.props.authenticatedUserInfo && this.props.authenticatedUserInfo.loaded) {
      return this.props.authenticatedUserInfo.value.userName === author;
    }
    return false;
  };

  renderAuthorName = (author) => {
    return this.isMine(author) ? 'You' : author;
  };

  renderDate = (date) => {
    return <Tooltip overlay={displayDate(date)}>
      <b style={{cursor: 'pointer'}}>{moment.utc(date).fromNow()}</b>
    </Tooltip>;
  };

  renderLabels = () => {
    if (!this.props.issueInfo) {
      return null;
    }
    if (this.props.issueInfo.pending) {
      return (
        <Row className={styles.contentPreview} type="flex" justify="center">
          <Icon type="loading" />
        </Row>
      );
    }
    if (this.props.issueInfo.error) {
      return (
        <div className={styles.contentPreview}>
          <span style={{color: '#ff556b'}}>{this.props.folder.error}</span>
        </div>
      );
    }
    if (!this.props.issueInfo.value.labels || !this.props.issueInfo.value.labels.length) {
      return null;
    }

    const labels = this.props.issueInfo.value.labels;

    return <Row type="flex" className={styles.attributes}>
      {
        labels.map((label, key) => (
          <div key={key} className={styles.attribute}>
            <div className={styles.attributeValue}>{label}</div>
          </div>
        ))
      }
    </Row>;
  };

  commentTextPreview = (text, style = {}) => {
    return <div
      className={styles.mdPreview}
      style={style}
      dangerouslySetInnerHTML={{__html: this.props.issuesRenderer.render(text)}} />;
  };

  renderIssue = () => {
    if (!this.props.issueInfo) {
      return null;
    }
    if (this.props.issueInfo.pending) {
      return (
        <Row className={styles.contentPreview} type="flex" justify="center">
          <Icon type="loading" />
        </Row>
      );
    }
    if (this.props.issueInfo.error) {
      return (
        <div className={styles.contentPreview}>
          <span style={{color: '#ff556b'}}>{this.props.folder.error}</span>
        </div>
      );
    }
    if (!this.issue) {
      return null;
    }

    return <div className={styles.contentPreview}>{this.commentTextPreview(this.issue.text)}</div>;
  };

  renderComments = () => {
    if (!this.props.issueInfo) {
      return null;
    }
    if (this.props.issueInfo.pending) {
      return (
        <Row className={styles.contentPreview} type="flex" justify="center">
          <Icon type="loading" />
        </Row>
      );
    }
    if (this.props.issueInfo.error) {
      return (
        <div className={styles.contentPreview}>
          <span style={{color: '#ff556b'}}>{this.props.folder.error}</span>
        </div>
      );
    }
    if (!this.comments || !this.comments.length) {
      return null;
    }
    const firstRowStyle = {
      color: '#999'
    };

    return this.comments.map(comment => ([
      renderSeparator(`${comment.id}_separator`),
      <div key={`${comment.id}_issue_comment`} className={styles.contentPreview}>
        <table>
          <tbody>
            <tr style={firstRowStyle}>
              <td>
                {this.renderAuthorName(comment.author)} commented {this.renderDate(comment.createdDate)}:
              </td>
            </tr>
            <tr>
              <td>
                {
                  comment.text &&
                  this.commentTextPreview(comment.text)
                }
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    ])).reduce((res, v) => [...res, ...v]);
  };

  render () {
    if (!this.props.item) {
      return null;
    }

    const highlights = renderHighlights(this.props.item);
    const labels = this.renderLabels();
    const issue = this.renderIssue();
    const comments = this.renderComments();

    return (
      <div className={styles.container}>
        <div className={styles.header}>
          <Row className={styles.title} type="flex" align="middle">
            <Icon type={PreviewIcons[this.props.item.type]} />
            <span>{this.props.item.name}</span>
          </Row>
          {
            this.description &&
            <Row className={styles.description}>
              {this.description}
            </Row>
          }
        </div>
        <div className={styles.content}>
          {highlights && renderSeparator()}
          {highlights}
          {labels && renderSeparator()}
          {labels}
          {issue && renderSeparator()}
          {issue}
          {comments}
        </div>
      </div>
    );
  }

}
