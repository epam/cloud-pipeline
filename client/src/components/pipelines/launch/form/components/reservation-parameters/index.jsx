import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import styles from './reservation-parameters.css';
import {InputNumber, Select, Slider} from 'antd';
import {
  getReservationParametersConfig,
  correctReservationParameters,
  DEFAULT_RAM_REQUESTS_STEP,
  DEFAULT_RAM_REQUESTS_UNIT,
  getInstanceResourcesRestrictions,
  parseRAMRequest,
  transformBytesToK8sRAMRequest
} from './utilities';

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
        if (this.token === token) {
          const {
            parameters = {}
          } = this.state;
          const {
            cpu: pCpu = 1,
            ram: pRam = 1,
            gpu: pGpu = 1
          } = correctReservationParameters(parameters, {
            config,
            instanceType
          });
          this.setState({
            config,
            instanceType: {
              name: instanceType,
              ...getInstanceResourcesRestrictions({
                config,
                instanceType: instanceTypeObj
              })
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
      step = 1,
      formatter = o => String(o),
      title = `${key.toUpperCase()} request`,
      slider: sliderRaw = false,
      inputValueConverter = o => o,
      inputValueFormatter = o => o
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
    const onChangeInput = (val) => {
      const n = Number(val);
      if (!Number.isNaN(n) && !/[.,]$/.test(val)) {
        this.setState({
          parameters: {
            ...parameters,
            [key]: inputValueFormatter(n)
          }
        }, this.reportChange);
      }
    };
    const {[key]: range} = instanceType;
    const [, max = 1] = range || [];
    const maxInput = parseFloat(inputValueConverter(max));
    let slider = sliderRaw;
    const values = [];
    if (!slider && step > 0) {
      let v = step;
      while (v <= max) {
        values.push(v);
        v += step;
      }
      if (values.length >= 100) {
        slider = true;
      }
    }
    if (enabled && max > 0) {
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
                min={step}
                max={Math.floor(max / step) * step}
                value={value}
                onChange={onChange}
                tipFormatter={formatter}
                step={step}
              />
              <InputNumber
                style={{flexShrink: 0, width: 100, marginRight: 0}}
                value={parseFloat(inputValueConverter(value))}
                min={1}
                max={maxInput}
                onChange={onChangeInput}
                onKeyDown={onKeyPress}
              />
            </div>
          );
        }
        return (
          <Select
            className={styles.reservationParameterInput}
            value={String(value)}
            onChange={onChange}
          >
            {
              values.map((value) => (
                <Select.Option key={`${key}-${value}`} value={String(value)}>
                  {value > 0 ? formatter(value) : 'Not set'}
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

  renderCpuRequests = () => this.renderRequestsSelector(
    'cpu',
    {
      reserved: 1
    }
  );

  renderRamRequests = () => {
    const {
      config,
      instanceType
    } = this.state;
    if (!config || !instanceType) {
      return null;
    }
    const {
      ram_requests_unit: ramRequestsUnit = DEFAULT_RAM_REQUESTS_UNIT,
      ram_requests_step: ramRequestsStep = DEFAULT_RAM_REQUESTS_STEP
    } = config;
    const step = parseRAMRequest(ramRequestsStep, ramRequestsUnit);
    const decimal = !ramRequestsUnit.includes('i');
    return this.renderRequestsSelector(
      'ram',
      {
        slider: true,
        title: `RAM request (${ramRequestsUnit})`,
        reserved: 1,
        step,
        inputValueConverter: o => transformBytesToK8sRAMRequest(
          o,
          {unit: ramRequestsUnit}
        ),
        inputValueFormatter: o => parseRAMRequest(o, ramRequestsUnit),
        formatter: o => transformBytesToK8sRAMRequest(
          o,
          {appendBytesLetter: true, decimal}
        )
      });
  }

  renderGpuRequests = () => this.renderRequestsSelector(
    'gpu',
    {
      reserved: 0
    }
  );

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
