import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import styles from './reservation-parameters.css';
import {InputNumber, Select, Slider} from 'antd';
import {getReservationParametersConfig} from './utilities';

class ReservationParameters extends React.PureComponent {
  state = {
    parameters: {},
    config: undefined,
    instanceType: undefined
  };

  componentDidMount () {
    this.updateConfig();
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    const {
      parameters: prev = {},
      instanceType: prevInstanceType = {}
    } = prevProps;
    const {
      parameters: curr = {},
      instanceType: currInstanceType = {}
    } = this.props;
    const {
      gpu: prevGpu,
      cpu: prevCpu,
      ram: prevRam
    } = prev;
    const {
      gpu: currGpu,
      cpu: currCpu,
      ram: currRam
    } = curr;
    if (
      prevGpu !== currGpu ||
      prevCpu !== currCpu ||
      prevRam !== currRam
    ) {
      this.updateFromProps();
    }
    const {
      name: prevInstanceTypeName
    } = prevInstanceType;
    const {
      name: currInstanceTypeName
    } = currInstanceType;
    if (
      prevInstanceTypeName !== currInstanceTypeName
    ) {
      this.updateConfig();
    }
  }

  componentWillUnmount () {
    this.token = undefined;
  }

  updateConfig = () => {
    const {
      instanceType: instanceTypeObj = {}
    } = this.props;
    const {
      name: instanceType
    } = instanceTypeObj;
    const token = this.token = {};
    (async () => {
      try {
        this.setState({
          instanceType: undefined,
          config: undefined
        });
        const config = await getReservationParametersConfig(instanceType);
        const {
          cpu_requests_enabled: cpuEnabled = false,
          gpu_requests_enabled: gpuEnabled = false,
          ram_requests_enabled: ramEnabled = false
        } = config || {};
        let {
          vcpu: cpu = 0,
          memory: ram = 0,
          gpu = 0
        } = instanceTypeObj;
        if (!cpuEnabled) {
          cpu = 0;
        }
        if (!gpuEnabled) {
          gpu = 0;
        }
        if (!ramEnabled) {
          ram = 0;
        }
        if (this.token === token) {
          const {
            parameters = {}
          } = this.state;
          let {
            cpu: pCpu = 1,
            ram: pRam = 1,
            gpu: pGpu = 1
          } = parameters;
          pCpu = Math.max(1, Math.min(pCpu, cpu));
          pGpu = Math.max(1, Math.min(pGpu, gpu));
          pRam = Math.max(1, Math.min(pRam, ram));
          this.setState({
            config,
            instanceType: {
              name: instanceType,
              ram,
              cpu,
              gpu
            },
            parameters: {
              cpu: pCpu,
              gpu: pGpu,
              ram: pRam
            }
          }, this.reportChange);
        }
      } catch (e) {
        console.error('error reading reservation parameters configuration', e);
      }
    })();
  };

  updateFromProps = () => {
    const {
      parameters = {}
    } = this.props;
    const {
      gpu = 1,
      cpu = 1,
      ram = 1
    } = parameters;
    this.setState({
      parameters: {
        gpu,
        cpu,
        ram
      }
    });
  };

  reportChange = () => {
    const {
      onChange
    } = this.props;
    const {
      parameters = {}
    } = this.state;
    if (onChange) {
      onChange(parameters);
    }
  };

  renderRequestsSelector = (key, options = {}) => {
    const {
      config,
      instanceType,
      parameters = {}
    } = this.state;
    if (!config || !instanceType) {
      return null;
    }
    const {
      enabledKey = `${key}_requests_enabled`,
      title = `${key.toUpperCase()} request`,
      slider = false
    } = options || {};
    const {
      [enabledKey]: enabled = false
    } = config;
    const {
      [key]: value = 1
    } = parameters;
    const onChange = (val) => {
      const n = Number(val);
      if (!Number.isNaN(n)) {
        this.setState({
          parameters: {
            ...parameters,
            [key]: n
          }
        }, this.reportChange);
      }
    };
    const {[key]: available} = instanceType;
    if (enabled && available > 0) {
      const component = (() => {
        if (slider) {
          const onKeyPress = (evt) => {
            if (/^enter$/i.test(evt.key)) {
              evt.preventDefault();
              evt.stopPropagation();
            }
          };
          return (
            <div
              className={styles.reservationParameterInput}
              style={{
                display: 'inline-flex',
                alignItems: 'center'
              }}
            >
              <Slider
                style={{flex: 1, marginRight: 5}}
                min={1}
                max={available}
                value={value}
                onChange={onChange}
                step={1}
              />
              <InputNumber
                style={{flexShrink: 0, width: 100, marginRight: 0}}
                value={value}
                min={1}
                max={available}
                onChange={onChange}
                onKeyDown={onKeyPress}
              />
            </div>
          );
        }
        const values = (new Array(available))
          .fill(1)
          .map((_, i) => i + 1);
        return (
          <Select
            className={styles.reservationParameterInput}
            value={String(value)}
            onChange={onChange}
          >
            {
              values.map((value) => (
                <Select.Option key={`${key}-${value}`} value={String(value)}>
                  {value > 0 ? String(value) : 'Not set'}
                </Select.Option>
              ))
            }
          </Select>
        );
      })();
      return (
        <div key={key} className={styles.reservationParameterRow}>
          <span className={styles.reservationParameterLabel}>
            {title}:
          </span>
          {component}
        </div>
      );
    }
    return null;
  };

  renderCpuRequests = () => this.renderRequestsSelector('cpu');

  renderRamRequests = () => this.renderRequestsSelector('ram', {slider: true, title: 'RAM request (GB)'});

  renderGpuRequests = () => this.renderRequestsSelector('gpu');

  render () {
    const {
      className,
      style
    } = this.props;
    const reqs = [
      this.renderCpuRequests(),
      this.renderRamRequests(),
      this.renderGpuRequests()
    ].filter(Boolean);
    if (reqs.length === 0) {
      return null;
    }
    return (
      <div
        className={classNames(className, styles.reservationParameters)}
        style={style}
      >
        {reqs}
      </div>
    );
  }
}

ReservationParameters.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  instanceType: PropTypes.object,
  parameters: PropTypes.object,
  onChange: PropTypes.func
};

export default ReservationParameters;
