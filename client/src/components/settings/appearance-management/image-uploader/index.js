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
import {
  Button,
  Icon,
  Input,
  message,
  Upload,
  Tooltip, Checkbox
} from 'antd';
import classNames from 'classnames';
import displaySize from '../../../../utils/displaySize';
import styles from './image-uploader.css';

const CHECK_IMAGE_URL_DELAY_MS = 500;

const extractUrlValue = o => {
  const e = /url\(['"](.*)['"]\)/i.exec(o);
  if (e) {
    return e[1];
  }
  return undefined;
};

function getBase64 (file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.readAsDataURL(file);
    reader.onload = () => resolve(reader.result);
    reader.onerror = error => reject(error);
  });
}

class ImageUploader extends React.PureComponent {
  state = {
    imageURL: undefined,
    check: undefined,
    error: undefined
  }

  get isUnset () {
    const {value} = this.props;
    return !value || /^none$/i.test(value);
  }

  get isBase64encodedImage () {
    const {value} = this.props;
    return !this.isUnset && /^url\(['"]data:image\/.+base64/i.test(value);
  }

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.value !== this.props.value
    ) {
      this.updateFromProps();
    }
  }

  componentWillUnmount () {
  }

  updateFromProps = () => {
    const {
      value
    } = this.props;
    this.setState({
      imageURL: !this.isUnset && !this.isBase64encodedImage
        ? extractUrlValue(value)
        : undefined,
      check: undefined,
      error: undefined
    });
  }

  handleFileChange = (file) => {
    if (this.checkTimer) {
      clearInterval(this.checkTimer);
    }
    const size = file ? file.size : 0;
    if (this.props.maxSize && size > this.props.maxSize) {
      message.error(
        `File size exceeds ${displaySize(this.props.maxSize, false)}`,
        5
      );
    } else {
      const {
        variable,
        onChange
      } = this.props;
      getBase64(file)
        .then(base64 => {
          if (onChange) {
            onChange(variable, `url('${base64}')`);
          }
        })
        .catch(e => message.error(e.message, 5));
    }
    return false;
  }

  renderImageUploader = () => {
    const {
      value,
      disabled
    } = this.props;
    const {
      imageURL
    } = this.state;
    return (
      <Upload
        disabled={disabled}
        accept={'image/png, image/jpeg, image/jpg, image/svg, image/tiff'}
        multiple={false}
        showUploadList={false}
        beforeUpload={this.handleFileChange}
        className="transparent-upload"
      >
        <div
          className={
            classNames(
              styles.imageContainer,
              'image-uploader',
              {
                [styles.disabled]: disabled
              }
            )
          }
        >
          <div
            className={
              classNames(
                styles.thumbImagePreview
              )
            }
            style={
              this.isBase64encodedImage && !imageURL
                ? {backgroundImage: value}
                : {}
            }
          >
            {'\u00A0'}
          </div>
          <div
            className={
              classNames(
                styles.thumbImage,
                'uploader-thumb-image'
              )
            }
          >
            <Icon
              type="camera-o"
              className="cp-icon-large"
            />
          </div>
        </div>
      </Upload>
    );
  };

  checkImageURLDelayed = () => {
    if (this.checkTimer) {
      clearInterval(this.checkTimer);
    }
    const {imageURL} = this.state;
    if (!imageURL) {
      this.setState({
        check: undefined,
        error: undefined
      });
    } else {
      this.setState({
        check: imageURL,
        error: undefined
      }, () => {
        this.checkTimer = setTimeout(
          () => this.checkImageURL(),
          CHECK_IMAGE_URL_DELAY_MS
        );
      });
    }
  };

  checkImageURL = () => {
    const {
      imageURL,
      check
    } = this.state;
    const {
      variable,
      onChange
    } = this.props;
    fetch(imageURL)
      .then((response) => {
        if (this.state.check === check) {
          if (!response.ok) {
            throw new Error(`Error fetching image: ${response.statusText}`);
          }
          const contentType = response.headers.get('Content-Type');
          if (!/image\//i.test(contentType)) {
            throw new Error(`Not supported content-type: ${contentType}`);
          }
          this.setState({
            check: undefined,
            error: undefined
          }, () => {
            if (onChange) {
              onChange(variable, `url('${imageURL}')`);
            }
          });
        }
      })
      .catch(e => {
        if (this.state.check === check) {
          this.setState({
            error: e.message,
            check: undefined
          });
        }
      });
  };

  renderCheckIndicator = () => {
    const {
      check,
      error
    } = this.state;
    if (check) {
      return (
        <div className={styles.inputInfo}>
          <Icon
            type="loading"
          />
        </div>
      );
    }
    if (error) {
      return (
        <div className={styles.inputInfo}>
          <Tooltip title={error}>
            <Icon
              className="cp-error"
              type="exclamation-circle-o"
            />
          </Tooltip>
        </div>
      );
    }
    return null;
  };

  renderImageUrlInput = () => {
    const {
      disabled
    } = this.props;
    const {
      imageURL,
      check,
      error
    } = this.state;
    const onChangeInput = (e) => {
      if (!e.target.value) {
        this.onClear();
      } else {
        this.setState({
          imageURL: e.target.value
        }, this.checkImageURLDelayed);
      }
    };
    return (
      <div className={styles.inputContainer}>
        <Input
          className={
            classNames(
              styles.input,
              {
                [styles.withIcon]: check || error
              }
            )
          }
          disabled={disabled}
          value={imageURL}
          onChange={onChangeInput}
          placeholder="Enter image URL"
        />
        {this.renderCheckIndicator()}
      </div>
    );
  };

  onClear = () => {
    const {
      onChange,
      variable
    } = this.props;
    this.setState({
      imageURL: undefined
    }, this.checkImageURLDelayed);
    if (onChange) {
      onChange(variable, 'none');
    }
  };

  onReset = () => {
    const {
      onChange,
      variable
    } = this.props;
    this.setState({
      imageURL: undefined
    }, this.checkImageURLDelayed);
    if (onChange) {
      onChange(variable, undefined);
    }
  };

  renderClearButton = () => {
    const {
      disabled
    } = this.props;
    return (
      <Button
        disabled={disabled || this.isUnset}
        className={
          classNames(
            styles.button,
            styles.small
          )
        }
        onClick={this.onClear}
      >
        <Icon type="delete" />
      </Button>
    );
  };

  renderRevertButton = () => {
    const {
      disabled,
      initialValue,
      modifiedValue,
      onChange,
      variable
    } = this.props;
    if (initialValue !== modifiedValue) {
      const onRevert = () => {
        if (onChange) {
          onChange(variable, initialValue);
        }
      };
      return (
        <Button
          disabled={disabled}
          className={
            classNames(
              styles.button,
              styles.small
            )
          }
          onClick={onRevert}
        >
          <Icon type="rollback" />
        </Button>
      );
    }
    return null;
  };

  renderResetButton = () => {
    const {
      disabled,
      extended
    } = this.props;
    return (
      <Checkbox
        disabled={!extended || disabled}
        checked={!extended}
        className={styles.button}
        onChange={e => e.target.checked ? this.onReset() : undefined}
      >
        Inherited
      </Checkbox>
    );
  };

  render () {
    const {
      className
    } = this.props;
    return (
      <div
        className={
          classNames(
            className,
            styles.uploaderContainer
          )
        }
      >
        {this.renderImageUploader()}
        {this.renderImageUrlInput()}
        {this.renderClearButton()}
        {this.renderRevertButton()}
        {this.renderResetButton()}
      </div>
    );
  }
}

ImageUploader.PropTypes = {
  className: PropTypes.string,
  disabled: PropTypes.bool,
  maxSize: PropTypes.number,
  variable: PropTypes.string,
  modifiedValue: PropTypes.string,
  initialValue: PropTypes.string,
  extended: PropTypes.bool,
  onChange: PropTypes.func,
  value: PropTypes.string
};

ImageUploader.defaultProps = {
  maxSize: undefined
};

export default ImageUploader;
