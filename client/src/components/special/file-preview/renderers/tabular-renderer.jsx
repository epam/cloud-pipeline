import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Alert} from 'antd';
import Papa from 'papaparse';
import {ObjectStorage} from '../../../../utils/object-storage';
import LoadingView from '../../LoadingView';
import CodeEditor from '../../CodeEditor';
import HotTable from 'react-handsontable';
import styles from './file-preview-renderers.css';

class TabularDataRenderer extends React.PureComponent {
  static testExtension = (ext) => /^(tsv|csv)$/i.test(ext);
  state = {
    pending: false,
    error: undefined,
    content: undefined,
    parseError: undefined,
    plainText: undefined
  };

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.filePath !== this.props.filePath ||
      prevProps.storage !== this.props.storage ||
      prevProps.fileData !== this.props.fileData
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
      filePath,
      fileData
    } = this.props;
    const parseData = (data) => {
      if (!data) {
        commit(() => {
          this.setState({
            pending: false,
            error: undefined,
            content: undefined,
            parseError: undefined,
            plainText: undefined
          });
        });
        return;
      }
      try {
        const parseRes = Papa.parse(data);

        if (parseRes.errors.length) {
          const firstErr = parseRes.errors.shift();
          throw new Error(`${firstErr.code}: ${firstErr.message}. at row ${firstErr.row + 1}`);
        }
        commit(() => {
          this.setState({
            pending: false,
            error: undefined,
            content: parseRes.data,
            parseError: undefined,
            plainText: data
          });
        });
      } catch (error) {
        commit(() => {
          this.setState({
            pending: false,
            error: undefined,
            content: undefined,
            parseError: error.message,
            plainText: data
          });
        });
      }
    };
    if (storage && filePath && !fileData) {
      (async () => {
        commit(() => {
          this.setState({
            pending: true,
            error: undefined,
            content: undefined,
            parseError: undefined,
            plainText: undefined
          });
        });
        try {
          const obj = new ObjectStorage(storage);
          await obj.initialize();
          const content = await obj.getFileContent(filePath);
          parseData(content);
        } catch (error) {
          commit(() => {
            this.setState({
              pending: false,
              error: error.message,
              content: undefined,
              parseError: undefined,
              plainText: undefined
            });
          });
        }
      })();
    } else {
      parseData(fileData);
    }
  };

  render () {
    const {
      className,
      style
    } = this.props;
    const {
      pending,
      error,
      content,
      plainText
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
    if (!plainText) {
      return (
        <div
          className={classNames(className, styles.filePreviewRenderer)}
          style={style}
        >
          <span className="cp-text-not-important">No data</span>
        </div>
      );
    }
    if (content) {
      return (
        <HotTable
          className={
            classNames(className, styles.filePreviewRenderer, styles.filePreviewTabularRenderer)
          }
          style={style}
          root="hot"
          data={content}
          colHeaders
          rowHeaders
          readOnly
          readOnlyCellClassName={classNames('readonly-cell', 'cp-table-cell')}
          manualColumnResize
          manualRowResize
          contextMenu={['copy']}
        />
      );
    }
    return (
      <CodeEditor
        className={classNames(className, styles.filePreviewRenderer)}
        style={style}
        code={plainText}
        readOnly
      />
    );
  }
}

TabularDataRenderer.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  storage: PropTypes.object,
  filePath: PropTypes.string,
  fileData: PropTypes.string
};

export default TabularDataRenderer;
