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
import ReactDOM from 'react-dom';
import PropTypes from 'prop-types';
import {Provider, inject} from 'mobx-react';
import classNames from 'classnames';
import {
  Button,
  Icon,
  message
} from 'antd';
import {getMarkdownRenderer, processLinks} from '../../../markdown';
import ToolJobLink from '../tool-job-link';
import styles from './open-tool-info.css';

const markdownRenderer = getMarkdownRenderer();

const STORAGE_PREFIX = 'cloud-data';
const DEFAULT_TEMPLATE = `Copy file path: {FILE_PATH}
Open {APP_NAME}: {APP_LINK}
Open copied file path in the {APP_NAME}
`;

@inject('multiZoneManager', 'preferences', 'awsRegions')
class OpenToolInfo extends React.Component {
  pathElement;

  get template () {
    const {template} = this.props;
    return (template || DEFAULT_TEMPLATE)
      .replace(/\\n/g, '\n')
      .split('\n')
      .join('\n\n');
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
          <Icon type="copy" />
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

  renderContent = () => {
    const renderers = {
      'FILE_PATH': this.renderFilePath,
      'APP_NAME': this.renderAppName,
      'APP_LINK': this.renderAppLink
    };
    const html = processLinks(markdownRenderer.render(this.template), '_blank');
    const {tool} = this.props;
    if (tool && tool.image) {
      html.replace(/\{app_name\}/g, tool.image);
    }
    let result = html;
    let r = /\{(.*?)\}/g.exec(result);
    while (r) {
      const [type, attribute = ''] = r[1].split(':');
      result = result.slice(0, r.index)
        .concat(`<span
 class="open-in-placeholder"
 data-type="${type}"
 data-attribute="${attribute}"></span>`)
        .concat(result.slice(r.index + r[0].length));
      r = /\{(.*?)\}/g.exec(result);
    }
    const initialized = (div) => {
      if (div) {
        const placeholders = document.getElementsByClassName('open-in-placeholder');
        for (let p = 0; p < placeholders.length; p += 1) {
          const placeholder = placeholders[p];
          if (placeholder.dataset && placeholder.dataset['type']) {
            const {attribute = '', type} = placeholder.dataset;
            if (renderers.hasOwnProperty(type)) {
              ReactDOM.render(
                (
                  <Provider {...this.props}>
                    {renderers[type](attribute)}
                  </Provider>
                ),
                placeholder
              );
            }
          }
        }
      }
    };
    return (
      <div
        className={styles.list}
        dangerouslySetInnerHTML={{__html: result}}
        ref={initialized}
      />
    );
  }

  render () {
    const {
      tool,
      file,
      storage
    } = this.props;
    if (!tool || !file || !storage) {
      return null;
    }
    return this.renderContent();
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
