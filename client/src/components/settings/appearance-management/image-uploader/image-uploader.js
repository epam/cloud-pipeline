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
import {Alert, Icon, Input, Modal, Row, Upload} from 'antd';

import styles from './image-uploader.css';

function getBase64 (file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.readAsDataURL(file);
    reader.onload = () => resolve(reader.result);
    reader.onerror = error => reject(error);
  });
}

export class ImageUploader extends React.Component {
  state = {
    initialImg: {url: '', type: ''},
    imageURL: '',
    imageDataUrl: '',
    previewVisible: false,
    uploadError: '',
    modified: this.props.modified
  }

  static defaultProps = {
    maxSize: null
  }

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.img !== this.props.img ||
      prevProps.maxSize !== this.props.maxSize ||
      prevProps.modified !== this.props.modified
    ) { this.updateFromProps(); }
  }

  updateFromProps = () => {
    const {img, modified} = this.props;
    if (img && img !== 'none') {
      const urlFromString = img.match(/(url\()('|")(.+)('|")(\))/);
      const currentBackgroundURL = urlFromString && urlFromString.length >= 3 ? urlFromString[3] : '';
      const type = currentBackgroundURL.startsWith('data:image') ? 'base64' : 'link';
      this.setState({
        initialImg: {url: currentBackgroundURL, type: type},
        imageDataUrl: type === 'base64' ? currentBackgroundURL : '',
        imageURL: type === 'link' ? currentBackgroundURL : '',
        modified
      });
    } else {
      this.setState({
        imageURL: '',
        imageDataUrl: '',
        previewVisible: false,
        uploadError: ''
      });
    }
  }

  handleFileChange = async (file) => {
    const size = file ? file.size / 1024 / 1024 : 0;
    if (file && !file.url) {
      file.url = await getBase64(file);
    }
    if (this.props.maxSize && size > this.props.maxSize) {
      this.setState({
        uploadError: `File size exceeds ${this.props.maxSize} MiB`,
        imageURL: '',
        imageDataUrl: ''
      });
    } else {
      this.setState({
        imageDataUrl: file.url,
        imageURL: '',
        uploadError: ''
      }, () => this.props.onChange(`url('${file.url}')`));
    }
    return Promise.reject(new Error('stop uploading'));
  }

  handleImageURLChange = (e) => {
    const url = e.target.value;
    if (url && (/^https?:\/\//).test(url)) {
      this.setState({
        imageURL: url,
        imageDataUrl: ''
      }, () => this.props.onChange(`url('${url}')`));
    }
  }

  clearImageURL = () => {
    this.setState({
      imageURL: '',
      imageDataUrl: '',
      previewVisible: false,
      uploadError: ''
    });
  }

  handlePreviewCancel = () => {
    this.setState({previewVisible: false});
  }
  onResetToDefault = () => {
    this.props.onChange(undefined);
  }
  render () {
    let image;
    if (this.state.imageDataUrl) {
      image = (
        <img
          className={styles.bgImage}
          src={this.state.imageDataUrl} />
      );
    } else {
      image = (
        <Row type="flex" align="middle" justify="center" className={styles.noImageContainer}>
          <Icon
            className={styles.noImage}
            type="camera-o" />
        </Row>
      );
    }
    return (
      <div className={styles.uploaderContainer}>
        <div className={styles.thumbContainer}>
          <div className={styles.imageContainer}>
            {image}
            <Upload
              accept={'image/png, image/jpeg, image/jpg, image/svg, image/tiff'}
              multiple={false}
              showUploadList={false}
              beforeUpload={this.handleFileChange}
            >
              <Row type="flex" align="middle" justify="center" className={styles.thumbImage}>
                <Icon type="upload" style={{fontSize: 'large', textShadow: '1px 1px black'}} />
              </Row>
            </Upload>
          </div>
          <Input
            style={{marginLeft: 5, flex: '1 1 auto', width: '250px'}}
            id="image_url"
            type="text"
            value={this.state.imageURL}
            suffix={this.state.imageURL ? <Icon type="close-circle" onClick={this.clearImageURL} /> : null}
            onChange={this.handleImageURLChange}
            placeholder="Enter the image URL"
          />
          {
            this.state.modified && (
              <a
                className="cp-link"
                onClick={this.onResetToDefault}
                style={{marginLeft: 5}}
              >
                Reset to default
              </a>
            )
          }
        </div>

        {this.state.uploadError && (
          <div style={{width: '100%', marginTop: '15px'}}>
            <Alert showIcon type="error" message={this.state.uploadError} />
          </div>)
        }
        <Modal
          visible={this.state.previewVisible}
          footer={null}
          onCancel={this.handlePreviewCancel}
        >
          <img
            alt="background_image"
            style={{width: '100%'}}
            src={this.state.imageDataUrl || this.state.imageURL}
          />
        </Modal>
      </div>
    );
  }
}

ImageUploader.PropTypes = {
  maxSize: PropTypes.number,
  onChange: PropTypes.func,
  img: PropTypes.string,
  modified: PropTypes.bool
};
