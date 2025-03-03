import React, {Component} from 'react';
import PropTypes from 'prop-types';
import {Spin, Row} from 'antd';
import ResultTable from './components/result-table';
import PipelineRunResults from '../../../../../models/pipelines/PipelineRunResults';

export class Reports extends Component {
  static propTypes = {
    runId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]).isRequired
  };

  state = {
    results: [],
    pending: false
  }

  componentDidMount () {
    this.fetchRunResults();
  }

  fetchRunResults = async () => {
    const {runId} = this.props;

    const pipelineResults = new PipelineRunResults(runId);
    try {
      this.setState({pending: true});
      await pipelineResults.fetch();

      this.setState({results: pipelineResults.value});
    } catch (error) {
      // No handler
    }

    this.setState({pending: false});
  }

  render () {
    if (this.state.pending) {
      return (
        <Row type={'flex'} style={{marginTop: 40}} justify="center" >
          <Spin spinning={this.state.pending} />
        </Row>
      );
    }

    return (
      <ResultTable resultItems={this.state.results} />
    );
  }
}

export default Reports;
