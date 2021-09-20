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
import {Button, message} from 'antd';
import ToolJobLink from '../tool-job-link';
import styles from './open-tool-info.css';

const STORAGE_PREFIX = 'cloud-data';
const DEFAULT_TEMPLATE = `
  Copy file path: {FILE_PATH}
  Open {APP_NAME}: {APP_LINK}
  Open copied file path in the {APP_NAME}
`;

class OpenToolInfo extends React.Component {
  pathElement;

  get template () {
    const {template} = this.props;
    return (template || DEFAULT_TEMPLATE)
      .replaceAll(/\\n/g, '\n')
      .split(/\r?\n/)
      .map(item => item.trim())
      .filter(Boolean);
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
    const isNFS = (storage.type || '').toUpperCase() === 'NFS';
    if (isWindowsPlatform) {
      fullPath = `C:\\${STORAGE_PREFIX}\\${storage.name}\\${file.replace(/\//g, '\\')}`;
    } else if (isNFS) {
      const storagePath = storage.path.replace(':', '');
      let mountPoint = storage.mountPoint;
      if (mountPoint && mountPoint.endsWith('/')) {
        mountPoint = mountPoint.slice(0, -1);
      }
      if (mountPoint && !mountPoint.startsWith('/')) {
        mountPoint = '/'.concat(mountPoint);
      }
      fullPath = mountPoint && mountPoint.length > 0
        ? `${mountPoint}/${file}`
        : `${STORAGE_PREFIX}/${storagePath}/${file}`;
    } else {
      const storagePath = isNFS
        ? storage.path.replace(new RegExp(/:\//g), '/')
        : `/${STORAGE_PREFIX}/${storage.path}`;
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
          <CopyOutlined />
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
          activeJobsFetching && (<LoadingOutlined />)
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
      .split(new RegExp(/(\{.*?\})/))
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
