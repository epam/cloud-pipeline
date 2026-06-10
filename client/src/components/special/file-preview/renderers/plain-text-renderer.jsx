import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Alert} from 'antd';
import {ObjectStorage} from '../../../../utils/object-storage';
import LoadingView from '../../LoadingView.tsx';
import styles from './file-preview-renderers.module.css';
import CodeEditor from '../../CodeEditor';
import Markdown from '../../markdown';

class PlainTextRenderer extends React.PureComponent {
  state = {
    preview: undefined,
    error: undefined,
    content: undefined,
  };

  componentDidMount() {
    this.updateFromProps();
  }

  componentDidUpdate(prevProps, prevState, snapshot) {
    if (
      prevProps.filePath !== this.props.filePath ||
      prevProps.storage !== this.props.storage ||
      prevProps.fileData !== this.props.fileData
    ) {
      this.updateFromProps();
    }
  }

  componentWillUnmount() {
    this.token = {};
  }

  updateFromProps = () => {
    const token = (this.token = {});
    const commit = (fn) => {
      if (this.token === token) {
        fn();
      }
    };
    const {storage, filePath, fileData} = this.props;
    if (storage && filePath && !fileData) {
      (async () => {
        commit(() => {
          this.setState({
            pending: true,
            error: undefined,
            content: undefined,
          });
        });
        try {
          const obj = new ObjectStorage(storage);
          await obj.initialize();
          const content = await obj.getFileContent(filePath);
          commit(() => {
            this.setState({
              pending: false,
              error: undefined,
              content,
            });
          });
        } catch (error) {
          commit(() => {
            this.setState({
              pending: false,
              error: error.message,
              content: undefined,
            });
          });
        }
      })();
    } else {
      this.setState({
        pending: false,
        error: undefined,
        content: fileData,
      });
    }
  };

  render() {
    const {className, style, filePath} = this.props;
    const {pending, error, content} = this.state;
    if (pending) {
      return (
        <div className={classNames(className, styles.filePreviewRenderer)} style={style}>
          <LoadingView />
        </div>
      );
    }
    if (error) {
      return (
        <div className={classNames(className, styles.filePreviewRenderer)} style={style}>
          <Alert title={error} type="error" showIcon />
        </div>
      );
    }
    if (!content) {
      return (
        <div className={classNames(className, styles.filePreviewRenderer)} style={style}>
          <span className="cp-text-not-important">No data</span>
        </div>
      );
    }
    const isMarkdown = filePath && /\.md$/i.test(filePath);
    if (isMarkdown) {
      return (
        <Markdown
          className={classNames(className, styles.filePreviewRenderer)}
          style={style}
          md={content}
        />
      );
    }
    return (
      <CodeEditor
        className={classNames(className, styles.filePreviewRenderer)}
        style={style}
        code={content}
        readOnly
      />
    );
  }
}

PlainTextRenderer.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  storage: PropTypes.object,
  filePath: PropTypes.string,
  fileData: PropTypes.string,
};

export default PlainTextRenderer;
