import React from 'react';
import PropTypes from 'prop-types';
import Select from 'antd/es/select';
import 'antd/es/select/style/css';
import {list} from '../api/instances';

class InstanceInput extends React.Component {
  static propTypes = {
    disabled: PropTypes.bool,
    onChange: PropTypes.func,
    value: PropTypes.string
  };

  state = {
    value: null,
    list: []
  };

  componentDidMount() {
    this.setState({
      value: this.props.value
    });
    list().then(result => {
      const type = 'cluster.allowed.instance.types.docker';
      if (!result.error && result[type]) {
        this.setState({
          list: result[type]
        });
      }
    });
  }

  componentDidUpdate(prevProps, prevState, snapshot) {
    if (prevProps.value !== this.props.value) {
      this.setState({
        value: this.props.value
      });
    }
  }

  onChange = (e) => {
    const {onChange} = this.props;
    onChange(e);
  };

  render () {
    const {
      disabled
    } = this.props;
    const {
      value,
      list
    } = this.state;
    return (
      <Select
        value={value}
        disabled={disabled}
        onChange={this.onChange}
        showSearch
        filterOption={
          (input, option) =>
            option.props.value.toLowerCase().indexOf(input.toLowerCase()) >= 0}
      >
        {
          list.map(instance => (
            <Select.Option key={instance.name} value={instance.name}>
              {instance.name} ({instance.vcpu} CPU, {instance.memory} {instance.memoryUnit})
            </Select.Option>
          ))
        }
      </Select>
    );
  }
}

export default InstanceInput;
