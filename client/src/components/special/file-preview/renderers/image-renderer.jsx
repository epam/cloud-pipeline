import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import styles from './file-preview-renderers.css';
import {ObjectStorage} from '../../../../utils/object-storage';
import LoadingView from '../../LoadingView';
import {Alert} from 'antd';

class ImageRenderer extends React.PureComponent {
  static testExtension = (ext) => /^(png|jpg|jpeg|bmp)$/i.test(ext);

  state = {
    pending: false,
    error: undefined,
    url: undefined
  };

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.filePath !== this.props.filePath ||
      prevProps.storage !== this.props.storage
    ) {
      this.updateFromProps();
    }
  }

  componentWillUnmount () {
    this.token = {};
  }

  updateFromProps = () => {
    const token = this.token = {};
    const commit = (fn) => {
      if (this.token === token) {
        fn();
      }
    };
    const {
      storage,
      filePath
    } = this.props;
    if (storage && filePath) {
      (async () => {
        commit(() => {
          this.setState({
            pending: true,
            error: undefined,
            url: undefined
          });
        });
        try {
          const obj = new ObjectStorage(storage);
          await obj.initialize();
          const url = await obj.generateFileUrl(filePath);
          commit(() => {
            this.setState({
              pending: false,
              error: undefined,
              url
            });
          });
        } catch (error) {
          commit(() => {
            this.setState({
              pending: false,
              error: error.message,
              url: undefined
            });
          });
        }
      })();
    } else {
      this.setState({
        pending: false,
        error: undefined,
        url: undefined
      });
    }
  };

  render () {
    const {
      className,
      style,
      storage,
      filePath
    } = this.props;
    if (!storage || !filePath) {
      return (
        <div
          className={classNames(className, styles.filePreviewRenderer)}
          style={style}
        >
          File preview not available
        </div>
      );
    }
    const {
      pending,
      error,
      url
    } = this.state;
    if (pending) {
      return (
        <div
          className={classNames(className, styles.filePreviewRenderer)}
          style={style}
        >
          <LoadingView />
        </div>
      );
    }
    if (error) {
      return (
        <div
          className={classNames(className, styles.filePreviewRenderer)}
          style={style}
        >
          <Alert title={error} type="error" showIcon />
        </div>
      );
    }
    return (
      <div
        className={classNames(className, styles.filePreviewRenderer)}
        style={style}
      >
        <img src={url} style={{width: '100%'}} alt={filePath} />
      </div>
    );
  }
}

ImageRenderer.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  storage: PropTypes.object,
  filePath: PropTypes.string,
  fileData: PropTypes.string
};

export default ImageRenderer;
