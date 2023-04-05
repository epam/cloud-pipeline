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

export default class CommentCard extends React.Component {
  static propTypes = {
    comment: PropTypes.object,
    className: PropTypes.string,
    onSelectMenu: PropTypes.func,
    headerClassName: PropTypes.string,
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
      author: systemAuthor = {},
      description,
      body,
      type
    } = comment;
    let {
      author = ''
    } = systemAuthor.name;
    let text = description || body;
    if (/^issue$/i.test(type)) {
      author = getAuthor(comment);
    } else {
      const authorLabel = text
        .split('\n')
        .find(part => part.toLowerCase().includes('on behalf of'));
      if (authorLabel) {
        author = authorLabel.split('of').pop().trim();
        text = text.replace(authorLabel, '');
      }
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
      headerClassName,
      onSelectMenu,
      style
    } = this.props;
    if (!comment) {
      return null;
    }
    const {
      type,
      updated_at: updatedAt,
      created_at: createdAt
    } = comment;
    let date = updatedAt || createdAt;
    let dateDescription = 'commented';
    if (/^issue$/i.test(type)) {
      date = createdAt || updatedAt;
      dateDescription = 'created ticket';
    }
    const {
      author,
      text
    } = this.getCommentInfo();
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
        className={classNames(
          'cp-panel',
          className
        )}
        key={comment.id}
      >
        <div
          className={classNames(
            'cp-divider',
            'bottom',
            headerClassName
          )}
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
          {onSelectMenu ? (
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
          ) : null}
        </div>
        <Markdown
          md={text}
          style={{
            margin: '10px 0',
            minHeight: '32px',
            padding: '5px 10px'
          }}
        />
      </div>
    );
  }
}
