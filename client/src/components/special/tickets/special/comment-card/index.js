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

export default class CommentCard extends React.Component {
  static propTypes = {
    comment: PropTypes.object,
    className: PropTypes.string,
    onSelectMenu: PropTypes.func
  };

  onSelectMenu = (key, comment) => {
    const {onSelectMenu} = this.props;
    onSelectMenu && onSelectMenu(key, comment);
  };

  render () {
    const {comment, className, onSelectMenu} = this.props;
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
          className
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
            'bottom'
          )}
          style={{
            padding: '5px 10px',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center'
          }}
        >
          <div>
            {comment.author.name}
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
          md={comment.description || comment.body}
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
