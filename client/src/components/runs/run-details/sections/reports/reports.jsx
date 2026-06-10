import React, {Component} from 'react';
import PropTypes from 'prop-types';
import {Spin} from 'antd';
import classNames from 'classnames';
import ResultTable from './components/result-table';
import PipelineRunResults from '../../../../../models/pipelines/PipelineRunResults';
import styles from './reports.module.css';

export class Reports extends Component {
  static propTypes = {
    runId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]).isRequired,
    className: PropTypes.string,
    style: PropTypes.object,
  };

  state = {
    results: [],
    pending: false,
  };

  componentDidMount() {
    this.fetchRunResults();
  }

  fetchRunResults = async () => {
    const {runId} = this.props;

    const pipelineResults = new PipelineRunResults(runId);
    try {
      this.setState({pending: true});
      await pipelineResults.fetch();

      this.setState({results: pipelineResults.value ?? []});
    } catch (error) {
      // No handler
    }

    this.setState({pending: false});
  };

  render() {
    const {className, style} = this.props;
    if (this.state.pending) {
      return (
        <div
          className={classNames(
            className,
            styles.runReportsContainer,
            styles.runReportsContainerLoading,
          )}
          style={style}
        >
          <Spin spinning={this.state.pending} />
        </div>
      );
    }

    return (
      <ResultTable
        className={classNames(className, styles.runReportsContainer)}
        style={style}
        resultItems={this.state.results}
      />
    );
  }
}

export default Reports;
