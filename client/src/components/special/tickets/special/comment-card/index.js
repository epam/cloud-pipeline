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
  Icon,
  Dropdown,
  Menu
} from 'antd';
import Markdown from '../../../markdown';
import displayDate from '../../../../../utils/displayDate';
import getAuthor from '../utilities/get-author';
import UserName from '../../../UserName';
import parseAttachment from '../utilities/parse-attachment';

class CommentCard extends React.Component {
  static propTypes = {
    comment: PropTypes.object,
    className: PropTypes.string,
    onSelectMenu: PropTypes.func,
    style: PropTypes.object
  };

  getCommentInfo () {
    const {comment} = this.props;
    if (!comment) {
      return {
        author: undefined,
        text: ''
      };
    }
    const {
      description,
      body
    } = comment;
    let text = description || body;
    const author = getAuthor(comment);
    const authorLabel = text
      .split('\n')
      .find(part => part.toLowerCase().includes('on behalf of'));
    if (authorLabel) {
      text = text.replace(authorLabel, '');
    }
    return {
      author,
      text
    };
  }

  onSelectMenu = (key, comment) => {
    const {onSelectMenu} = this.props;
    onSelectMenu && onSelectMenu(key, comment);
  };

  render () {
    const {
      comment,
      className,
      onSelectMenu,
      style,
      isIssue
    } = this.props;
    if (!comment) {
      return null;
    }
    const {
      attachments: rawAttachments = [],
      updated_at: updatedAt,
      created_at: createdAt
    } = comment;
    let date = updatedAt || createdAt;
    let dateDescription = 'commented';
    if (isIssue) {
      date = createdAt || updatedAt;
      dateDescription = 'created ticket';
    }
    const {
      author,
      text
    } = this.getCommentInfo();
    const attachments = rawAttachments
      .map(parseAttachment)
      .filter(Boolean)
      .filter((attachment) => attachment.link);
    const messageMenu = (
      <Menu
        onClick={({key}) => this.onSelectMenu(key, comment)}
        selectedKeys={[]}
        style={{cursor: 'pointer'}}
      >
        <Menu.Item key="edit">
          Edit
        </Menu.Item>
        <Menu.Item key="delete">
          Delete
        </Menu.Item>
      </Menu>
    );
    return (
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          borderRadius: '4px',
          style
        }}
        className={
          classNames(
            'cp-panel-card',
            className
          )
        }
        key={comment.id}
      >
        <div
          className={
            classNames(
              {
                'cp-divider': !!text && text.length,
                'bottom': !!text && text.length
              }
            )
          }
          style={{
            padding: '5px 10px',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center'
          }}
        >
          <div>
            <UserName
              userName={author}
              showIcon={!!author}
            />
            <span
              className="cp-text-not-important"
              style={{marginLeft: '5px'}}
            >
              {dateDescription} {displayDate(date, 'D MMM YYYY, HH:mm')}
            </span>
          </div>
          {
            !!onSelectMenu && (
              <Dropdown
                overlay={messageMenu}
                trigger={['click']}
              >
                <Icon
                  type="ellipsis"
                  style={{
                    cursor: 'pointer',
                    marginRight: 10,
                    fontSize: 'large',
                    fontWeight: 'bold'
                  }}
                />
              </Dropdown>
            )
          }
        </div>
        <Markdown
          md={text}
          style={{
            margin: '10px 0',
            minHeight: '32px',
            padding: '5px 10px'
          }}
        />
        {
          attachments.length > 0 && (
            <div
              className={
                classNames(
                  'cp-divider',
                  'top'
                )
              }
              style={{
                padding: '5px 10px',
                display: 'flex',
                alignItems: 'center',
                flexWrap: 'wrap'
              }}
            >
              {
                attachments.map((attachment) => (
                  <a
                    key={attachment.name}
                    style={{marginRight: 5}}
                    href={attachment.link}
                    target="_blank"
                  >
                    <Icon type="paper-clip" style={{marginRight: 5}} />
                    {attachment.name}
                  </a>
                ))
              }
            </div>
          )
        }
      </div>
    );
  }
}

CommentCard.propTypes = {
  isIssue: PropTypes.bool,
  comment: PropTypes.object,
  className: PropTypes.string,
  onSelectMenu: PropTypes.func,
  style: PropTypes.object
};

export default CommentCard;
