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
import {Icon} from 'antd';
import CommitDiffButton from './commit-diff-button';
import UserName from '../../../../special/UserName';
import displayDate from '../../../../../utils/displayDate';
import styles from './history.css';

function CommitCard (
  {
    className,
    commit,
    disabled = false,
    path,
    style,
    versionedStorageId
  }
) {
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
      <CommitDiffButton
        disabled={disabled}
        commit={commit?.commit}
        path={path}
        versionedStorageId={versionedStorageId}
      />
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
    </div>
  );
}

CommitCard.propTypes = {
  className: PropTypes.string,
  commit: PropTypes.object,
  disabled: PropTypes.bool,
  path: PropTypes.string,
  style: PropTypes.object,
  versionedStorageId: PropTypes.oneOfType([PropTypes.string, PropTypes.number])
};

export default CommitCard;
