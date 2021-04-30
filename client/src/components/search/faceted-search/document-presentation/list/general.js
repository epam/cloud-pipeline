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
import getDocumentName from '../utilities/get-document-name';
import {Icon} from 'antd';
import classNames from 'classnames';
import styles from './document-list-presentation.css';
import UserName from '../../../../special/UserName';
import {PreviewIcons} from "../../../preview/previewIcons";

export default function GeneralPresentation (
  {
    className,
    children,
    document,
    showDescription = true
  }
) {
  const renderIcon = () => {
    if (PreviewIcons[document?.type]) {
      return (
        <Icon
          className={styles.icon}
          type={PreviewIcons[document?.type]} />
      );
    }
    return null;
  };
  return (
    <div
      className={
        classNames(
          className,
          styles.row
        )
      }
    >
      <div className={styles.main}>
        <div className={styles.name}>
          {renderIcon()}
          <span>
            {getDocumentName(document) || '\u00A0'}
          </span>
        </div>
        <div className={styles.sub}>
          {
            showDescription && document?.description && (
              <span className={classNames(styles.description, styles.ellipsis)}>
                {document.description}
              </span>
            )
          }
          {children}
        </div>
      </div>
      {
        document?.owner && (
          <div className={styles.author}>
            {
              document?.owner && (
                <Icon type="user" />
              )
            }
            <UserName userName={document?.owner} />
          </div>
        )
      }
    </div>
  );
}
