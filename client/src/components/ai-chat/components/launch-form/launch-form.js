import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Button, Form} from 'antd';
import {Environment, LaunchFormInfo, ParameterGroup} from './index';
import styles from './launch-form.css';
import {observer} from 'mobx-react';
import LaunchFormStore from './launch-form-store';

@observer
export default class LaunchForm extends React.Component {
  constructor (props) {
    super(props);
    this.formStore = new LaunchFormStore();
  }

  componentDidMount () {
    const {data} = this.props;
    this.formStore.initializeFields(data);
    this.formStore.initializeParameters(data.parameters);
  }

  render () {
    const {data} = this.props;

    return (
      <div className={classNames(styles.launchForm, 'cp-panel')}>
        <LaunchFormInfo
          name={data.configurationName}
          version={data.version}
          description={data.description}
        />
        <Environment data={data} formStore={this.formStore} />
        <ParameterGroup data={data} formStore={this.formStore} />
        <div className={styles.controlBtn}>
          <Button
            type="primary"
            onClick={() => {
              console.log('[LaunchForm]', this.formStore);
            }}
          >
            SUBMIT
          </Button>
        </div>
      </div>
    );
  }
}

LaunchForm.propTypes = {
  data: PropTypes.shape({
    dockerImage: PropTypes.string,
    disk: PropTypes.string,
    cmd: PropTypes.string,
    instanceType: PropTypes.string,
    is_spot: PropTypes.bool,
    selectedPriceType: PropTypes.string,
    parameters: PropTypes.objectOf(
      PropTypes.shape({
        type: PropTypes.string.isRequired,
        value: PropTypes.any.isRequired
      })
    )
  }).isRequired
};
