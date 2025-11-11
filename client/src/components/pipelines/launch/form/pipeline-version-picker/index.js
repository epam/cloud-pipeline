import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import GetPipelineVersions from '../../../../../models/pipelines/Version';
import styles from './pipeline-version-picker.css';
import {AutoComplete, Input, Modal} from 'antd';

function filterPipelineVersion (version, filter, exact = false) {
  if (!filter || filter.trim().length === 0) {
    return true;
  }
  if (typeof version === 'string') {
    if (exact) {
      return version.toLowerCase() === filter.trim().toLowerCase();
    }
    return version.toLowerCase().includes(filter.trim().toLowerCase());
  }
  if (typeof version === 'object') {
    const {
      name,
      commitId
    } = version;
    if (exact) {
      return name.toLowerCase() === filter.trim().toLowerCase();
    }
    return name.toLowerCase().includes(filter.trim().toLowerCase()) || commitId.toLowerCase().includes(filter.trim().toLowerCase());
  }
  return false;
}

function createAutoCompleteGroup (key, group, versions) {
  return (
    <AutoComplete.OptGroup key={key} label={group}>
      {
        versions.map((version) => (
          <AutoComplete.Option
            key={version.commitId}
            value={version.name}>
            {version.name}
          </AutoComplete.Option>
        ))
      }
    </AutoComplete.OptGroup>
  );
}

class PipelineVersionPicker extends React.PureComponent {
  state = {
    filter: undefined,
    pending: false,
    error: undefined,
    versions: []
  };

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.pipelineId !== this.props.pipelineId) {
      this.updateFromProps();
    }
  }

  componentWillUnmount () {
    this.invalidateFetchSession();
  }

  /**
   * Invalidates current fetch session and returns a `commit` callback
   * that should be called to safely apply async changes, e.g.:
   *
   * ```javascript
   * const commit = this.invalidateFetchSession();
   * const data = await fetchAsyncData();
   * commit(() => this.setState({data})); // will be called if current session is not cancelled
   * ```
   * @returns {(function(*): void)|*}
   */
  invalidateFetchSession = () => {
    const token = this.token = {};
    return (fn) => {
      if (token === this.token && typeof fn === 'function') {
        fn();
      }
    };
  };

  updateFromProps = () => {
    const {pipelineId} = this.props;
    const commit = this.invalidateFetchSession();
    if (pipelineId) {
      (async () => {
        commit(() => {
          this.setState({
            filter: undefined,
            pending: true,
            error: undefined,
            versions: []
          });
        });
        try {
          const request = new GetPipelineVersions(pipelineId);
          await request.fetch();
          if (request.error) {
            throw new Error(request.error);
          }
          const versions = request.value || [];
          const pipelineVersions = versions.map((v) => ({
            author: v.author,
            authorEmail: v.authorEmail,
            commitId: v.commitId,
            message: v.message,
            name: v.name
          }));
          commit(() => {
            this.setState({
              filter: undefined,
              pending: false,
              error: undefined,
              versions: pipelineVersions
            });
          });
        } catch (error) {
          commit(() => {
            this.setState({
              filter: undefined,
              pending: false,
              error: error instanceof Error ? error.message : String(error)
            });
          });
        }
      })();
    } else {
      commit(() => {
        this.setState({
          filter: undefined,
          pending: false,
          error: undefined,
          versions: []
        });
      });
    }
  };

  onChangeFilter = (filter) => {
    this.setState({
      filter
    });
  };

  onSelectVersion = (version) => {
    const {
      pipelineVersion,
      onPipelineVersionChange
    } = this.props;
    if (
      version &&
      version.trim().length > 0 &&
      pipelineVersion !== version &&
      typeof onPipelineVersionChange === 'function'
    ) {
      const {versions = []} = this.state;
      const existing = (versions || [])
        .find((v) => (v.name || '').toLowerCase() === version.trim().toLowerCase());
      const realVersion = existing || version.trim().toLowerCase().startsWith('draft-')
        ? version
        : `draft-${version}`;
      console.log(version, existing, realVersion);
      this.setState({filter: version});
      Modal.confirm({
        title: <span>Are you sure you want to change version to <b>{version}</b>?</span>,
        style: {
          wordWrap: 'break-word'
        },
        content: <div>Current parameters and values may be lost.</div>,
        onOk: () => {
          onPipelineVersionChange(realVersion);
          this.setState({filter: undefined});
        },
        onCancel: () => {
          this.setState({filter: undefined});
        },
        okText: 'CHANGE',
        cancelText: 'CANCEL'
      });
    }
  }

  onPressEnter = (event) => {
    event.stopPropagation();
    event.preventDefault();
  };

  onBlurInput = () => {
    this.setState({filter: undefined});
  };

  render () {
    const {
      className,
      style,
      disabled,
      pipelineVersion
    } = this.props;
    const {
      pending,
      versions,
      filter
    } = this.state;
    let value = filter === undefined ? pipelineVersion : filter;
    let filteredVersions = versions;
    if (filter && filter.length > 0) {
      filteredVersions = versions.filter((version) => filterPipelineVersion(version, filter, false));
    }
    const exactVersion = versions.find((version) => filterPipelineVersion(version, value, true));
    let newVersions = [];
    if (!exactVersion && value) {
      newVersions = [{
        name: value,
        commitId: value
      }];
    }
    const options = [];
    if (newVersions.length > 0) {
      options.push(createAutoCompleteGroup('new-versions', 'Commit', newVersions));
    }
    options.push(createAutoCompleteGroup('versions', 'Versions', filteredVersions));
    return (
      <div className={classNames(className, styles.pipelineVersionPicker)} style={style}>
        <AutoComplete
          disabled={pending || disabled}
          dataSource={options}
          value={value}
          onSelect={this.onSelectVersion}
          onChange={this.onChangeFilter}
        >
          <Input onBlur={this.onBlurInput} onPressEnter={this.onPressEnter} />
        </AutoComplete>
      </div>
    );
  }
}

PipelineVersionPicker.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  pipelineId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  pipelineVersion: PropTypes.string,
  onPipelineVersionChange: PropTypes.func,
  disabled: PropTypes.bool
};

export default PipelineVersionPicker;
