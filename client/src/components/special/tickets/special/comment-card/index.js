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
import {getAuthor} from '../index.js';

export default class CommentCard extends React.Component {
  static propTypes = {
    comment: PropTypes.object,
    className: PropTypes.string,
    onSelectMenu: PropTypes.func,
    headerClassName: PropTypes.string,
    style: PropTypes.object
  };

  get commentInfo () {
    const {comment} = this.props;
    let author = '';
    let text = comment.description || comment.body;
    if (comment && comment.labels) {
      author = getAuthor(comment);
      text = comment.title;
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
            {this.commentInfo.author}
            <span
              className="cp-text-not-important"
              style={{fontSize: 'smaller', marginLeft: '5px'}}
            >
              commented {displayDate(comment.updated_at, 'D MMM YYYY, HH:mm')}
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
          md={this.commentInfo.text}
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
