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

export default class EmailPreview extends React.Component {

  static propTypes = {
    value: PropTypes.string,
    style: PropTypes.object,
    className: PropTypes.string,
    iFrameStyle: PropTypes.object
  };

  codeTemplateStyle = 'padding: 2px 4px;font-size: 90%;color: #c7254e;background-color: #f9f2f4;border-radius: 4px;box-shadow: inset 0 -1px 0 rgba(0, 0, 0, 0.25);';

  processHtml = (regexp, fieldRegex, html) => {
    const process = (str) => {
      const matchResult = str.match(regexp);
      if (matchResult && matchResult.length === 2) {
        const paths = [];
        const test = (part) => {
          const res = part.match(regexp);
          if (res && res.length === 2) {
            const partRes = res[1].match(fieldRegex);
            if (partRes && partRes.length === 2) {
              paths.push(partRes[1]);
            }
            return res[0].substring(0, res[0].length - res[1].length);
          }
          return null;
        };
        let s = test(str);
        while (s) {
          s = test(s);
        }
        return paths.reverse().join('.');
      }
    };
    let result = html.match(regexp);
    const isField = (str, field) => {
      const check = (checkString, openTag, closeTag) => {
        const openIndex = checkString.toLowerCase().indexOf(openTag.toLowerCase());
        if (openIndex >= 0) {
          const closeIndex = checkString.substring(openIndex + openTag.length).toLowerCase().indexOf(closeTag.toLowerCase());
          if (closeIndex >= 0) {
            return {
              pass: true,
              index: openIndex + closeIndex + openTag.length + closeTag.length
            };
          } else {
            return {
              pass: false
            };
          }
        } else {
          return {
            pass: true,
            index: checkString.length
          };
        }
      };
      const checkQuotes = (quotes) => {
        let checkString = str.toLowerCase();
        let checkResult = check(checkString, `${field}=${quotes}`, quotes);
        while (checkResult.pass) {
          checkString = checkString.substring(checkResult.index);
          if (checkString && checkString.length) {
            checkResult = check(checkString, `${field}=${quotes}`, quotes);
          } else {
            break;
          }
        }
        return checkResult.pass;
      };
      return !checkQuotes('\'') || !checkQuotes('"');
    };

    const isHREForSRCField = (testString) => {
      return isField(testString, 'href') || isField(testString, 'src');
    };

    while (result && result.length === 2) {
      const paths = process(result[0]);
      const isHREForSRC = isHREForSRCField(html.substring(0, result.index));
      if (isHREForSRC) {
        html = html.substring(0, result.index) +
          paths +
          html.substring(result.index + result[0].length);
        result = html.match(regexp);
      } else {
        html = html.substring(0, result.index) +
          `<span style="${this.codeTemplateStyle}">${paths}</span>` +
          html.substring(result.index + result[0].length);
        result = html.match(regexp);
      }
    }
    return html;
  };

  getValue = () => {
    let value = this.props.value;
    if (value) {
      value = this.processHtml(/\$templateParameters(\.get\("[^\W ]+"\))+/, /.get\("([^\W ]+)"\)/, value);
      value = this.processHtml(/\$templateParameters(\["[^\W ]+"\])+/, /\["([^\W ]+)"\]/, value);
    }
    return value;
  };

  render () {
    return (
      <div
        className={this.props.className}
        style={this.props.style}>
        <iframe
          style={this.props.iFrameStyle || {
            width: '100%',
            height: '100%',
            border: 'none'
          }}
          src={`data:text/html;charset=utf-8,${encodeURIComponent(this.getValue())}`} />
      </div>
    );
  }
}
