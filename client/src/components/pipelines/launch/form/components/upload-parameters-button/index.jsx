import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Button, Dropdown, Menu, message} from 'antd';
import {readParametersFile} from './utilities';
import styles from './upload-parameters-button.css';
import {downloadParametersTemplate} from '../../utilities/parameter-utilities';

class UploadParametersButton extends React.PureComponent {
  get showDownload () {
    const {parametersToDownload = []} = this.props;
    return parametersToDownload.length > 0;
  }

  onClick = () => {
    const {input} = this;
    if (input) {
      input.value = '';
      input.click();
    }
  };

  onChange = () => {
    const {input} = this;
    if (input && input.files) {
      const newFiles = [];
      for (let i = 0; i < input.files.length; i++) {
        newFiles.push(input.files[i]);
      }
      void this.onSelect(newFiles);
    }
  }

  onSelect = async (files) => {
    const {onParametersUploaded} = this.props;
    const hide = message.loading(`Processing file${files.length === 1 ? '' : 's'}...`, -1);
    try {
      const params = await Promise.all(files.map(async (file) => {
        const parameters = await readParametersFile(file);
        return {
          file: file.name,
          parameters
        };
      }));
      if (onParametersUploaded) {
        onParametersUploaded(params);
      }
    } catch (error) {
      console.error('Error processing parameters file', error);
      message.error(
        <div>Error processing parameters file:<span>{error.message}</span></div>,
        5
      );
    } finally {
      hide();
    }
  }

  initializeInput = (input) => {
    this.input = input;
  };

  onMenuClick = ({key}) => {
    const {parametersToDownload = [], disabled} = this.props;
    if (disabled) {
      return;
    }
    if (key === 'download') {
      downloadParametersTemplate(parametersToDownload);
    }
  };

  renderLink = () => {
    const {parametersToDownload, disabled, children} = this.props;
    if (disabled) {
      return (
        <span className="cp-text-not-important">
          {children}
          {this.showDownload ? (
            <span style={{marginLeft: 8}}>Download template</span>
          ) : null}
        </span>
      );
    }
    return (
      <span>
        <a onClick={this.onClick}>{children}</a>
        <a
          onClick={() => downloadParametersTemplate(parametersToDownload)}
          style={{marginLeft: 8}}
        >
          Download template
        </a>
      </span>
    );
  };

  render () {
    const {
      className,
      style,
      asLink = true,
      children = 'Upload',
      multiple = true,
      accept,
      disabled
    } = this.props;
    if (asLink) {
      return (
        <div
          className={classNames(className, styles.uploadParametersButtonContainer)}
          style={style}
        >
          {this.renderLink()}
          <input
            style={{display: 'none'}}
            type="file"
            ref={this.initializeInput}
            multiple={multiple}
            accept={accept}
            onChange={this.onChange}
          />
        </div>
      );
    }
    return (
      <div
        className={classNames(className, styles.uploadParametersButtonContainer)}
        style={style}
      >
        {this.showDownload ? (
          <Dropdown.Button
            overlay={(
              <Menu
                onClick={this.onMenuClick}
                items={[{key: 'download', label: 'Download template'}]}
              />
            )}
            onClick={this.onClick}
            size="small"
            disabled={disabled}
            type="primary"
          >
            {children}
          </Dropdown.Button>
        ) : (
          <Button
            onClick={this.onClick}
            size="small"
            disabled={disabled}
            type="primary">
            {children}
          </Button>
        )}
        <input
          style={{display: 'none'}}
          type="file"
          ref={this.initializeInput}
          multiple={multiple}
          accept={accept}
          onChange={this.onChange}
        />
      </div>
    );
  }
}

UploadParametersButton.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  disabled: PropTypes.bool,
  children: PropTypes.node,
  multiple: PropTypes.bool,
  accept: PropTypes.string,
  asLink: PropTypes.bool,
  onParametersUploaded: PropTypes.func,
  parametersToDownload: PropTypes.arrayOf(PropTypes.shape({}))
};

export default UploadParametersButton;
