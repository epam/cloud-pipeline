import React from 'react';
import PropTypes from 'prop-types';
import PipelineRunEstimatedPrice from '../../../models/pipelines/PipelineRunEstimatedPrice';
import {Alert} from 'antd';
import {LoadingOutlined} from '@ant-design/icons';
import JobEstimatedPriceInfo from '../../special/job-estimated-price-info';

class RunPayloadEstimatedPriceAlert extends React.PureComponent {
  state = {
    pending: false,
    error: false,
    pricePerHour: undefined
  };
  _token = {};

  invalidateToken = () => {
    this._token = {};
    return this._token;
  };

  componentDidMount () {
    this.recalculate();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.pipelineId !== this.props.pipelineId ||
      prevProps.pipelineVersion !== this.props.pipelineVersion ||
      prevProps.pipelineConfiguration !== this.props.pipelineConfiguration ||
      prevProps.instanceType !== this.props.instanceType ||
      prevProps.instanceDisk !== this.props.instanceDisk ||
      prevProps.spot !== this.props.spot ||
      prevProps.regionId !== this.props.regionId
    ) {
      this.recalculate();
    }
  }

  componentWillUnmount () {
    this.invalidateToken();
  }

  recalculate = () => {
    const token = this.invalidateToken();
    const {
      pipelineId,
      pipelineVersion,
      pipelineConfiguration,
      instanceType,
      instanceDisk,
      spot,
      regionId
    } = this.props;
    const commitState = (state, cb) => {
      if (token === this._token) {
        this.setState(state, cb);
      }
    };
    (async () => {
      commitState({pending: true, error: undefined});
      try {
        const request = new PipelineRunEstimatedPrice(
          pipelineId,
          pipelineVersion,
          pipelineConfiguration
        );
        await request.send({
          instanceType,
          instanceDisk,
          spot,
          regionId
        });
        if (request.error) {
          throw new Error(request.error);
        }
        const {
          pricePerHour = 0
        } = request.value;
        commitState({pricePerHour});
      } catch (error) {
        commitState({error: error.message, pricePerHour: undefined});
      } finally {
        commitState({pending: false});
      }
    })();
  };

  get nodeCount () {
    const {nodeCount} = this.props;
    if (nodeCount && !Number.isNaN(Number(nodeCount))) {
      return Number(nodeCount);
    }
    return 0;
  }

  render () {
    const {
      className,
      style,
      showIcon
    } = this.props;
    const {
      pending,
      error,
      pricePerHour = 0
    } = this.state;
    const pricePerHourValue = (
      Math.ceil(pricePerHour * 100.0) / 100.0 * (this.nodeCount + 1)
    ).toFixed(2);
    return (
      <Alert
        className={className}
        style={style}
        showIcon={showIcon}
        type={error ? 'warning' : 'success'}
        message={(
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 5
            }}
          >
            <span>
              {
                error ? 'Error calculating estimated price:' : 'Estimated price:'
              }
            </span>
            {
              pending && <LoadingOutlined />
            }
            {
              !pending && error && <span>{error}</span>
            }
            {
              !pending && !error && (
                <JobEstimatedPriceInfo>
                  <span><b>{pricePerHourValue}{'$'}</b> per hour.</span>
                </JobEstimatedPriceInfo>
              )
            }
          </div>
        )}
      />
    );
  }
}

RunPayloadEstimatedPriceAlert.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  showIcon: PropTypes.bool,
  pipelineId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  pipelineVersion: PropTypes.string,
  pipelineConfiguration: PropTypes.string,
  instanceDisk: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  instanceType: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  spot: PropTypes.oneOfType([PropTypes.string, PropTypes.bool]),
  regionId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  nodeCount: PropTypes.oneOfType([PropTypes.string, PropTypes.number])
};

export default RunPayloadEstimatedPriceAlert;
