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
import {CopyOutlined, LoadingOutlined} from '@ant-design/icons';
import {message} from 'antd';
import hljs from 'highlight.js';
import 'highlight.js/styles/github.css';
import copyTextToClipboard from '../copy-text-to-clipboard';
import styles from './bash-code.module.css';

function shScriptToHtml(script) {
  if (!script) {
    return '';
  }
  let command = hljs.highlight('bash', script).value;
  const r = /\[URL\](.+)\[\/URL\]/gi;
  let e = r.exec(command);
  while (e) {
    command =
      command.substring(0, e.index) +
      `<a href="${e[1]}" target="_blank">${e[1]}</a>` +
      command.substring(e.index + e[0].length);
    e = r.exec(command);
  }
  return command;
}

function handleCopy(event, code) {
  event.preventDefault();
  event.stopPropagation();
  copyTextToClipboard(code || '')
    .then(() => message.success('Copied to clipboard', 2))
    .catch((error) => message.error(error.message, 3));
}

function BashCode({className, code, id, loading, style, breakLines, nowrap, copyable = true}) {
  let html = shScriptToHtml(code) || '';
  if (breakLines) {
    html = html.replace(/\n/g, '<br />');
  }
  const showCopy = copyable && !loading && !!code;
  return (
    <div
      id={id}
      className={classNames(
        'code-highlight',
        styles.shCode,
        {
          [styles.nowrap]: nowrap,
        },
        className,
      )}
      style={style}
    >
      {loading && <LoadingOutlined />}
      {!loading && (
        <pre>
          {showCopy && (
            <span className={styles.copyIcon} onClick={(event) => handleCopy(event, code)}>
              <CopyOutlined />
            </span>
          )}
          <span dangerouslySetInnerHTML={{__html: html}} />
        </pre>
      )}
    </div>
  );
}

BashCode.propTypes = {
  id: PropTypes.string,
  className: PropTypes.string,
  code: PropTypes.string,
  loading: PropTypes.bool,
  style: PropTypes.object,
  breakLines: PropTypes.bool,
  nowrap: PropTypes.bool,
  copyable: PropTypes.bool,
};

export default BashCode;
