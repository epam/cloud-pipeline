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
import {
  Button,
  Icon,
  message
} from 'antd';
import ToolJobLink from '../tool-job-link';
import styles from './open-tool-info.css';

const NON_NFS_LINUX_PREFIX = 'cloud-data';

class OpenToolInfo extends React.Component {
  pathElement;

  get template () {
    const {template} = this.props;
    if (template) {
      const listItems = template
        .replaceAll(/\\n/g, '\n')
        .split(/\r?\n/)
        .map(item => item.trim())
        .filter(Boolean);
      return listItems;
    }
    return [];
  }

  get appName () {
    const {tool} = this.props;
    return tool.image;
  }

  get filePath () {
    const {
      file,
      storage,
      tool
    } = this.props;
    const {platform = ''} = tool || {};
    const isWindowsPlatform = /^windows$/i.test(platform.toLowerCase());
    let fullPath;
    if (isWindowsPlatform) {
      fullPath = `Z:\\${storage.name}\\${file.replace(/\//g, '\\')}`;
    } else {
      const isNFS = (storage.type || '').toUpperCase() === 'NFS';
      const storagePath = isNFS
        ? storage.path.replace(new RegExp(/:\//g), '/')
        : `/${NON_NFS_LINUX_PREFIX}/${storage.path}`;
      fullPath = `${storagePath}/${file}`;
    }
    return fullPath;
  }

  onLaunchClick = (event) => {
    const {onLaunchClick} = this.props;
    event.stopPropagation();
    event.preventDefault();
    onLaunchClick && onLaunchClick();
  };

  renderFilePath = () => {
    const initializePathElement = element => {
      this.pathElement = element;
    };
    const copy = (e) => {
      e.stopPropagation();
      e.preventDefault();
      if (this.pathElement) {
        const range = document.createRange();
        range.setStart(this.pathElement, 0);
        range.setEnd(this.pathElement, 1);
        window.getSelection().removeAllRanges();
        window.getSelection().addRange(range);
        if (document.execCommand('copy')) {
          message.info('Copied to clipboard', 3);
          window.getSelection().removeAllRanges();
        }
      }
    };
    return (
      <div>
        <span>
          Copy file path:
        </span>
        <br />
        <div className={styles.code}>
          <div className={styles.part} style={{flex: 1}}>
            <pre ref={initializePathElement}>
              {this.filePath}
            </pre>
          </div>
          <div
            className={classNames(styles.part, styles.button)}
            onClick={copy}
          >
            <Icon type="copy" />
          </div>
        </div>
      </div>
    );
  };

  renderAppName = () => {
    const {tool} = this.props;
    return (
      <span>
        {tool.image}
      </span>
    );
  };

  renderAppLink = (linkText) => {
    const {
      activeJob,
      activeJobsFetching
    } = this.props;
    return (
      <span>
        {
          activeJobsFetching && (<Icon type="loading" />)
        }
        {
          !activeJobsFetching && !!activeJob && (
            <ToolJobLink
              job={activeJob}
              toolName={this.appName}
              linkText={linkText}
            />
          )
        }
        {
          !activeJobsFetching && !activeJob && (
            <Button
              size="small"
              type="primary"
              onClick={this.onLaunchClick}
              style={{marginLeft: 5}}
            >
              Launch
            </Button>
          )
        }
      </span>
    );
  };

  renderListItemContent = (rowTemplate) => {
    const renderers = {
      'FILE_PATH': this.renderFilePath,
      'APP_NAME': this.renderAppName,
      'APP_LINK': this.renderAppLink
    };
    const rowContent = rowTemplate
      .split(new RegExp(/((?!^)\{.*?\})/))
      .filter(Boolean);
    const getRenderFn = (chunk) => {
      const templateSignature = new RegExp(/^[{].*[}]$/);
      if (templateSignature.test(chunk)) {
        const [placeholderName, argumentString] = chunk
          .trim()
          .slice(1, -1)
          .split(':');
        const renderFn = renderers[placeholderName];
        return [renderFn, argumentString];
      }
      return [];
    };
    return (
      <div>
        {rowContent.map((chunk, index) => {
          const [renderFn, argument] = getRenderFn(chunk);
          return (
            <span key={index}>
              {
                renderFn
                  ? renderFn(argument)
                  : chunk
              }
            </span>
          );
        })}
      </div>
    );
  };

  renderCustomTemplate = () => {
    return (
      <ul className={styles.list}>
        {this.template.map((rowTemplate, index) => {
          return (
            <li key={index}>
              {this.renderListItemContent(rowTemplate)}
            </li>
          );
        })}
      </ul>
    );
  };

  renderDefaultTemplate = () => {
    const {
      activeJob,
      activeJobsFetching
    } = this.props;
    return (
      <ul className={styles.list}>
        <li>
          {this.renderFilePath()}
        </li>
        <li>
          {
            activeJobsFetching && (<Icon type="loading" />)
          }
          {
            !activeJobsFetching && !!activeJob && (
              <ToolJobLink
                job={activeJob}
              />
            )
          }
          {
            !activeJobsFetching && !activeJob && (
              <span>
                {`Run personal ${this.appName} instance:`}
                <Button
                  size="small"
                  type="primary"
                  onClick={this.onLaunchClick}
                  style={{marginLeft: 5}}
                >
                  Launch
                </Button>
              </span>
            )
          }
        </li>
        <li>
          {`Open copied file path in ${this.appName}`}
        </li>
      </ul>
    );
  };

  render () {
    const {
      tool,
      file,
      storage
    } = this.props;
    if (!tool || !file || !storage) {
      return null;
    }
    return (
      this.template.length > 0
        ? this.renderCustomTemplate()
        : this.renderDefaultTemplate()
    );
  }
}

OpenToolInfo.propTypes = {
  template: PropTypes.string,
  activeJob: PropTypes.object,
  activeJobsFetching: PropTypes.bool,
  file: PropTypes.string,
  storage: PropTypes.object,
  tool: PropTypes.object,
  onLaunchClick: PropTypes.func
};

export default OpenToolInfo;
