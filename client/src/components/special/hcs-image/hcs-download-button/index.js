/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
import {inject, observer} from 'mobx-react';
import {Button, Dropdown, Icon, message} from 'antd';
import Menu, {MenuItem} from 'rc-menu';
import FileSaver from 'file-saver';
import {
  downloadAvailable as downloadCurrentTiffAvailable, downloadCurrentTiff
} from '../utilities/download-current-tiff';

@inject('hcsVideoSource', 'hcsMergedImageSource')
@observer
class HCSDownloadButton extends React.Component {
  handleDownloadScreenshot = () => {
    const {
      viewer: hcsImageViewer,
      wellView,
      wellId
    } = this.props;
    downloadCurrentTiff(
      hcsImageViewer,
      {
        wellView,
        wellId
      }
    );
  };

  handleHighResolutionScreenshotDownload = async (format) => {
    const hide = message.loading('Generating image...', 0);
    const {
      hcsMergedImageSource
    } = this.props;
    let generatedUrl, generatedFileName, generatedAccessCallback;
    try {
      await hcsMergedImageSource.initialize();
      if (hcsMergedImageSource.screenshotEndpointAPIError) {
        throw new Error(hcsMergedImageSource.screenshotEndpointAPIError);
      }
      const {
        url,
        name,
        accessCallback
      } = await hcsMergedImageSource.generateUrl(false, format);
      generatedUrl = url;
      generatedFileName = name;
      generatedAccessCallback = accessCallback;
    } catch (error) {
      message.error(error.message, 5);
    } finally {
      hide();
    }
    if (generatedUrl && generatedFileName) {
      const hideDownload = message.loading('Downloading image...', 0);
      if (typeof generatedAccessCallback === 'function') {
        generatedAccessCallback();
      }
      fetch(generatedUrl)
        .then(res => res.blob())
        .then(blob =>
          FileSaver.saveAs(blob, generatedFileName)
        )
        .catch((error) => {
          message.error(error.message, 5);
        })
        .finally(() => hideDownload());
    }
  };

  handleDownloadVideo = () => {
    const {
      hcsVideoSource
    } = this.props;
    const {
      videoUrl,
      videoAccessCallback = () => {}
    } = hcsVideoSource;
    videoAccessCallback();
    fetch(videoUrl)
      .then(res => res.blob())
      .then(blob => FileSaver.saveAs(blob, hcsVideoSource.getVideoFileName(videoUrl)));
  };

  renderScreenshotMenu = () => {
    const handle = ({key}) => {
      switch (key) {
        case 'tiff':
        case 'png':
          (this.handleHighResolutionScreenshotDownload)(key);
          break;
        case 'default':
        default:
          this.handleDownloadScreenshot();
          break;
      }
    };
    return (
      <Menu
        onClick={handle}
        selectedKeys={[]}
        style={{cursor: 'pointer'}}
      >
        <MenuItem key="default">
          Download preview image
        </MenuItem>
        <MenuItem key="tiff">
          Download original image (tiff)
        </MenuItem>
        <MenuItem key="png">
          Download original image (png)
        </MenuItem>
      </Menu>
    );
  };

  render () {
    const {
      className,
      style,
      size,
      viewer: hcsImageViewer,
      showTitle,
      hcsVideoSource,
      hcsMergedImageSource
    } = this.props;
    if (!hcsImageViewer) {
      return null;
    }
    const {
      videoMode,
      videoUrl,
      videoPending
    } = hcsVideoSource || {};
    if (videoMode) {
      return (
        <Button
          className={className}
          style={style}
          disabled={!videoUrl || videoPending}
          onClick={this.handleDownloadVideo}
          size={size}
        >
          {
            showTitle
              ? ('Download current video')
              : (<Icon type="download" className="cp-larger" />)
          }
        </Button>
      );
    }
    if (!hcsMergedImageSource.initialized || !hcsMergedImageSource.available) {
      return (
        <Button
          className={className}
          style={style}
          disabled={!downloadCurrentTiffAvailable(hcsImageViewer)}
          onClick={this.handleDownloadScreenshot}
          size={size}
        >
          {
            showTitle
              ? ('Download current image')
              : (<Icon type="camera" className="cp-larger" />)
          }
        </Button>
      );
    }
    if (showTitle) {
      return (
        <div
          className={className}
          style={style}
        >
          <div>
            <Button
              disabled={!downloadCurrentTiffAvailable(hcsImageViewer)}
              onClick={this.handleDownloadScreenshot}
              size={size}
              style={{width: '100%', marginBottom: 3}}
            >
              Download preview image
            </Button>
          </div>
          <div>
            <Button
              onClick={() => this.handleHighResolutionScreenshotDownload('tiff')}
              size={size}
              style={{width: '100%', marginBottom: 3}}
            >
              Download original image (tiff)
            </Button>
          </div>
          <div>
            <Button
              onClick={() => this.handleHighResolutionScreenshotDownload('png')}
              size={size}
              style={{width: '100%'}}
            >
              Download original image (png)
            </Button>
          </div>
        </div>
      );
    }
    const screenshotMenu = this.renderScreenshotMenu();
    return (
      <Dropdown.Button
        className={className}
        style={style}
        disabled={!downloadCurrentTiffAvailable(hcsImageViewer)}
        onClick={this.handleDownloadScreenshot}
        overlay={screenshotMenu}
        size={size}
      >
        <Icon type="camera" className="cp-larger" />
      </Dropdown.Button>
    );
  }
}

HCSDownloadButton.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  size: PropTypes.oneOf(['large', 'small', 'default']),
  viewer: PropTypes.object,
  showTitle: PropTypes.bool,
  wellView: PropTypes.bool,
  wellId: PropTypes.oneOfType([PropTypes.string, PropTypes.number])
};

export default HCSDownloadButton;
