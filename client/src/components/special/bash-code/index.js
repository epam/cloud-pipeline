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
import hljs from 'highlight.js';
import 'highlight.js/styles/github.css';
import styles from './bash-code.css';

function shScriptToHtml (script) {
  if (!script) {
    return '';
  }
  let command = hljs.highlight('bash', script).value;
  const r = /\[URL\](.+)\[\/URL\]/ig;
  let e = r.exec(command);
  while (e) {
    command = command.substring(0, e.index) +
      `<a href="${e[1]}" target="_blank">${e[1]}</a>` +
      command.substring(e.index + e[0].length);
    e = r.exec(command);
  }
  return command;
}

function BashCode (
  {
    className,
    code,
    id,
    loading,
    style,
    breakLines,
    nowrap
  }
) {
  let html = (shScriptToHtml(code) || '');
  if (breakLines) {
    html = html.replace(/\n/g, '<br />');
  }
  return (
    <div
      id={id}
      className={
        classNames(
          'code-highlight',
          styles.shCode,
          {
            [styles.nowrap]: nowrap
          },
          className
        )
      }
      style={style}
    >
      {
        loading && (<Icon type="loading" />)
      }
      {
        !loading && (
          <pre
            dangerouslySetInnerHTML={{__html: html}}
          />
        )
      }
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
  nowrap: PropTypes.bool
};

export default BashCode;
