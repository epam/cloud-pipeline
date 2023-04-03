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
import {Icon} from 'antd';
import classNames from 'classnames';
import Preview from '../index.js';
import styles from './preview-modal.css';

class PreviewModal extends React.Component {
  state = {
    maximized: false,
    maximizedAvailable: false,
    requireMaximumSpace: false
  };

  componentDidUpdate (prevProps) {
    if (prevProps.preview !== this.props.preview && !this.props.preview) {
      this.resetMaximized();
    }
  }

  resetMaximized () {
    this.setState({
      maximized: false,
      maximizedAvailable: false
    });
  }

  onPreviewLoaded = (options = {}) => {
    const {
      maximizedAvailable = false,
      requireMaximumSpace = false
    } = options;
    this.setState({
      maximizedAvailable,
      requireMaximumSpace
    });
  };

  handleMaximizePreview = (maximized = true) => {
    this.setState({maximized});
  };

  render () {
    const {
      preview,
      onClose,
      closable
    } = this.props;
    if (!preview) {
      return null;
    }
    const {
      maximized,
      maximizedAvailable,
      requireMaximumSpace
    } = this.state;
    const handleClosePreview = (event) => {
      if (event && event.target === event.currentTarget) {
        onClose && onClose();
        this.setState({
          maximized: false,
          maximizedAvailable: false,
          requireMaximumSpace: false
        });
      }
    };
    return (
      <div
        className={
          classNames(
            styles.previewWrapper,
            'cp-search-preview-wrapper',
            {
              [styles.closable]: closable
            }
          )
        }
        onClick={closable ? handleClosePreview : undefined}
      >
        <div
          className={
            classNames(
              styles.preview,
              {
                [styles.maximumSpace]: requireMaximumSpace
              },
              'cp-search-preview'
            )
          }
        >
          <Preview
            item={preview}
            onPreviewLoaded={this.onPreviewLoaded}
            fullscreen={maximized}
            onFullScreenChange={this.handleMaximizePreview}
            fullScreenAvailable={maximized && maximizedAvailable}
          />
          {
            maximizedAvailable && (
              <Icon
                type="arrows-alt"
                className={classNames(
                  styles.previewButton,
                  styles.maximize,
                  'cp-search-preview-button'
                )}
                onClick={() => this.handleMaximizePreview(true)}
              />
            )
          }
          <Icon
            type="close"
            className={classNames(
              styles.previewButton,
              styles.close,
              'cp-search-preview-button'
            )}
            onClick={handleClosePreview}
          />
        </div>
      </div>
    );
  }
}

PreviewModal.propTypes = {
  preview: PropTypes.object,
  onClose: PropTypes.func,
  closable: PropTypes.bool
};

export default PreviewModal;
