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
import * as diff2html from 'diff2html';
import {Collapse, Icon} from 'antd';
import classNames from 'classnames';
import rawTemplates from './raw-templates';
import styles from './diff.css';
import 'highlight.js/styles/github.css';
import 'diff2html/bundles/css/diff2html.min.css';
import '../../../../../staticStyles/git-diff-presentation.css';

class FileDiffPresenter extends React.PureComponent {
  state = {
    rawHtml: undefined,
    opened: false,
    initialized: false
  };

  diff2HtmlNode;

  componentDidMount () {
    this.updatePresentation();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.file !== this.props.file ||
      (prevProps.visible !== this.props.visible && this.props.visible)
    ) {
      this.onFileChanged();
    } else if (prevProps.raw !== this.props.raw) {
      this.updatePresentation();
    }
  }

  onFileChanged = () => {
    this.setState({
      initialized: false,
      opened: false
    }, () => {
      this.updatePresentation();
    });
  };

  updatePresentation = () => {
    const {raw, collapsed} = this.props;
    if (raw) {
      const {initialized, opened} = this.state;
      const diffJson = diff2html.parse(raw);
      const diffHtml = diff2html.html(
        diffJson,
        {
          drawFileList: false,
          outputFormat: 'line-by-line',
          highlight: true,
          rawTemplates
        });
      this.setState({
        rawHtml: diffHtml,
        initialized: true,
        opened: initialized ? opened : !collapsed
      });
    } else {
      this.setState({
        rawHtml: undefined
      });
    }
  };

  renderPresentation = () => {
    const {rawHtml} = this.state;
    if (rawHtml) {
      return (
        <div
          key="presentation"
          dangerouslySetInnerHTML={{
            __html: rawHtml
          }}
        />
      );
    }
    return (
      <div className={styles.emptyContent}>
        No content
      </div>
    );
  };

  renderDescription = () => {
    const {
      file,
      type
    } = this.props;
    if (file && type) {
      return (
        <div key="description">
          <div className={styles.fileDiffHeader}>
            <Icon type="file-text" />
            <span>{file}</span>
            <span
              className={classNames(styles.type, styles[type.toLowerCase()])}
            >
              {type}
            </span>
          </div>
        </div>
      );
    }
    return file;
  };

  onOpenedChange = (keys) => {
    this.setState({
      opened: (keys || []).length > 0
    });
  }

  render () {
    const {
      className,
      file,
      style
    } = this.props;
    const {
      opened
    } = this.state;
    return (
      <div
        key={file}
        className={className}
        style={style}
      >
        <Collapse
          className="git-diff-collapse"
          activeKey={opened ? ['presentation'] : []}
          onChange={this.onOpenedChange}
        >
          <Collapse.Panel
            header={this.renderDescription()}
            key="presentation"
          >
            {this.renderPresentation()}
          </Collapse.Panel>
        </Collapse>
      </div>
    );
  }
}

FileDiffPresenter.propTypes = {
  binary: PropTypes.bool,
  className: PropTypes.string,
  file: PropTypes.string,
  raw: PropTypes.string,
  type: PropTypes.string,
  visible: PropTypes.bool,
  collapsed: PropTypes.bool,
  style: PropTypes.object
};

export default FileDiffPresenter;
