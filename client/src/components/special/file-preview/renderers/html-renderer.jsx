import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import styles from './file-preview-renderers.css';
import {ObjectStorage} from '../../../../utils/object-storage';
import LoadingView from '../../LoadingView';
import {Alert} from 'antd';

class HtmlRenderer extends React.PureComponent {
  static testExtension = (ext) => /^html?$/i.test(ext);

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
    const {
      url
    } = this.state;
    this.token = {};
    if (url) {
      URL.revokeObjectURL(url);
    }
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
          const data = await obj.getFileContent(filePath);
          const blob = new Blob([data], {type: 'text/html'});
          const url = URL.createObjectURL(blob);
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
          <Alert message={error} type="error" showIcon />
        </div>
      );
    }
    if (!url) {
      return (
        <div
          className={classNames(className, styles.filePreviewRenderer)}
          style={style}
        >
          Preview not available
        </div>
      );
    }
    return (
      <div
        className={classNames(
          className,
          styles.filePreviewRenderer,
          styles.filePreviewHtmlRenderer
        )}
        style={style}
      >
        <iframe
          src={url}
          style={{width: '100%', height: '100%', overflow: 'auto', border: 'none'}}
        />
      </div>
    );
  }
}

HtmlRenderer.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  storage: PropTypes.object,
  filePath: PropTypes.string,
  fileData: PropTypes.string
};

export default HtmlRenderer;
