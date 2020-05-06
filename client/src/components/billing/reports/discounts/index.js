/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {observable} from 'mobx';
import {inject, observer, Provider as MobxProvider} from 'mobx-react';
import {Slider} from 'antd';
import * as discounts from './apply';
import styles from './discounts.css';

class DiscountsStore {
  @observable compute = 0;
  @observable storage = 0;
  @observable computeRaw = 0;
  @observable storageRaw = 0;

  constructor () {
    this.compute = 0;
    this.storage = 0;
    this.computeRaw = 0;
    this.storageRaw = 0;
  }

  apply (compute, storage) {
    if (compute !== this.compute) {
      this.compute = compute;
    }
    if (storage !== this.storage) {
      this.storage = storage;
    }
  }

  setCompute (value) {
    this.computeRaw = value;
    if (this.compute !== value) {
      if (this.setComputeDebounce) {
        clearTimeout(this.setComputeDebounce);
        this.setComputeDebounce = null;
      }
      this.setComputeDebounce = setTimeout(
        () => this.apply(value, this.storage),
        100
      );
    }
  }

  setStorage (value) {
    this.storageRaw = value;
    if (this.storage !== value) {
      if (this.setStorageDebounce) {
        clearTimeout(this.setStorageDebounce);
        this.setStorageDebounce = null;
      }
      this.setStorageDebounce = setTimeout(
        () => this.apply(this.compute, value),
        100
      );
    }
  }
}

function DiscountsSliderComponent ({compute, storage, discounts: store}) {
  const onChangeCompute = (value) => {
    store.setCompute(value);
  };
  const onChangeStorage = (value) => {
    store.setStorage(value);
  };
  return (
    <div className={styles.container}>
      {
        compute && (
          <div className={styles.subContainer}>
            <span className={styles.label}>
              Compute discounts:
            </span>
            <Slider
              min={0}
              max={100}
              onChange={onChangeCompute}
              value={store.computeRaw}
              step={0.01}
              style={{flex: 1}}
            />
          </div>
        )
      }
      {
        storage && (
          <div className={styles.subContainer}>
            <span className={styles.label}>
              Storage discounts:
            </span>
            <Slider
              min={0}
              max={100}
              onChange={onChangeStorage}
              value={store.storageRaw}
              step={0.01}
              style={{flex: 1}}
            />
          </div>
        )
      }
    </div>
  );
}

function DiscountsConsumerComponent ({children, discounts: store}) {
  return children(
    discounts.simpleDiscount(100.0 - store.compute),
    discounts.simpleDiscount(100.0 - store.storage)
  );
}

const DiscountsSlider = inject('discounts')(observer(DiscountsSliderComponent));
const DiscountsConsumer = inject('discounts')(observer(DiscountsConsumerComponent));

class Discounts extends React.Component {
  static Slider = DiscountsSlider;
  static Consumer = DiscountsConsumer;
  store = new DiscountsStore();

  render () {
    const {children} = this.props;
    return (
      <MobxProvider discounts={this.store}>
        {children}
      </MobxProvider>
    );
  }
}

Discounts.propTypes = {
  children: PropTypes.node
};

export {discounts};
export default Discounts;
