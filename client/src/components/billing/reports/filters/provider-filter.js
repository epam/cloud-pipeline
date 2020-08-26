/*
 *
 *  * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

import React from 'react';
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';
import {Select} from 'antd';
import AWSRegionTag from '../../../special/AWSRegionTag';
import styles from './provider-filter.css';

const RegionType = {
  region: 'region',
  provider: 'provider'
};

class ProviderFilter extends React.Component {
  state = {
    focused: false,
    value: []
  };

  get selectedProviders () {
    const {value} = this.state;
    return (value || []).map(this.mapRegion)
      .filter(r => r.type === RegionType.provider)
      .map(r => r.id);
  }

  componentDidMount () {
    this.updateValue(this.props.filter);
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (this.filterToString(prevProps.filter) !== this.filterToString(this.props.filter)) {
      this.updateValue(this.props.filter);
    } else {
      this.correct();
    }
  }

  filterToString = (filter) => {
    const str = (filter || []).map(f => Number(f));
    str.sort((a, b) => a - b);
    return str.join('|');
  };

  updateValue = (value) => {
    const newValue = (value || []).map(r => `${RegionType.region}-${r}`);
    this.correctValue(newValue)
      .then(corrected => {
        this.setState({
          value: corrected || newValue
        });
      });
  };

  correct = () => {
    this.correctValue(this.state.value)
      .then(corrected => {
        if (corrected) {
          this.setState({value: corrected});
        }
      });
  };

  correctValue = async (value) => {
    const {cloudRegionsInfo} = this.props;
    await cloudRegionsInfo.fetchIfNeededOrWait();
    if (cloudRegionsInfo.loaded) {
      const regions = (cloudRegionsInfo.value || []);
      const currentValue = (value || []).map(this.mapRegion);
      const newValue = [];
      let modified = false;
      [...(new Set((cloudRegionsInfo.value || []).map(o => o.provider)))]
        .forEach((provider) => {
          if (currentValue.some(r => r.type === RegionType.provider && r.id === provider)) {
            return;
          }
          const existingIds = new Set(
            regions.filter(r => r.provider === provider).map(r => +r.id)
          );
          const currentIds = new Set(
            (value || []).map(this.mapRegion)
              .filter(r => r.type === RegionType.region && existingIds.has(+r.id))
              .map(r => +r.id)
          );
          if (![...existingIds].some(id => !currentIds.has(id))) {
            modified = true;
            newValue.push(`${RegionType.provider}-${provider}`);
          } else {
            newValue.push(...([...currentIds]).map(c => `${RegionType.region}-${c}`));
          }
        });
      if (modified) {
        return newValue;
      }
    }
    return undefined;
  };

  onFocus = () => {
    this.setState({focused: true});
  };

  mapRegion = (region) => {
    const [type, ...rest] = region.split('-');
    const id = rest.join('-');
    return {
      type,
      id
    };
  };

  onBlur = () => {
    this.setState({focused: false}, async () => {
      const {value} = this.state;
      const {cloudRegionsInfo, onChange} = this.props;
      await cloudRegionsInfo.fetchIfNeededOrWait();
      if (cloudRegionsInfo.loaded) {
        const payload = value.map(this.mapRegion);
        const providers = payload.filter(p => p.type === RegionType.provider);
        providers.forEach(provider => {
          const index = payload.indexOf(provider);
          const regions = (cloudRegionsInfo.value || [])
            .filter(r => r.provider === provider.id)
            .map(r => ({type: RegionType.region, id: r.id}));
          payload.splice(index, 1, ...regions);
        });
        const correctedPayload = payload.map(o => o.id);
        if (
          onChange &&
          this.filterToString(correctedPayload) !== this.filterToString(this.props.filter)
        ) {
          onChange(correctedPayload);
        }
      }
    });
  };

  onChange = (opts) => {
    const {value} = this.state;
    const {cloudRegionsInfo} = this.props;
    if (value.length < opts.length && cloudRegionsInfo.loaded) {
      const old = new Set(value);
      const newSelection = opts.find(o => !old.has(o));
      if (newSelection) {
        const {type, id} = this.mapRegion(newSelection);
        if (type === RegionType.provider) {
          const providerRegions = new Set(
            (cloudRegionsInfo.value || [])
              .filter(r => (r.provider || '').toLowerCase() === id.toLowerCase())
              .map(r => `${r.id}`)
          );
          const filtered = opts
            .map(this.mapRegion)
            .filter(o => o.type === RegionType.provider || !providerRegions.has(`${o.id}`))
            .map(o => `${o.type}-${o.id}`);
          this.setState({
            value: filtered
          });
          return;
        }
      }
    }
    this.setState({
      value: opts
    });
  };

  render () {
    const {cloudRegionsInfo} = this.props;
    const {focused, value} = this.state;
    let regions = [];
    let providers = [];
    if (cloudRegionsInfo.loaded) {
      regions = (cloudRegionsInfo.value || []).map(o => o);
      providers = [...new Set((cloudRegionsInfo.value || []).map(o => o.provider))];
    }
    return (
      <Select
        style={{width: 185}}
        allowClear
        mode="multiple"
        placeholder="All regions / providers"
        showSearch
        dropdownMatchSelectWidth={false}
        className={styles.providerSelect}
        dropdownClassName={styles.dropdown}
        filterOption={
          (input, option) => option.props.filter.some(f => f.indexOf(input.toUpperCase()) >= 0)
        }
        notFoundContent="Not found"
        value={value}
        onBlur={this.onBlur}
        onFocus={this.onFocus}
        onChange={this.onChange}
        open={focused}
      >
        <Select.OptGroup label="Providers">
          {
            providers.map((provider) => (
              <Select.Option
                key={`${RegionType.provider}-${provider}`}
                value={`${RegionType.provider}-${provider}`}
                filter={[(provider || '').toUpperCase()]}
              >
                {provider}
              </Select.Option>
            ))
          }
        </Select.OptGroup>
        <Select.OptGroup label="Regions">
          {
            regions.map((region) => (
              <Select.Option
                disabled={this.selectedProviders.some(p => p === region.provider)}
                key={`${RegionType.region}-${region.id}`}
                value={`${RegionType.region}-${region.id}`}
                filter={[
                  (region.provider || '').toUpperCase(),
                  (region.name || '').toUpperCase(),
                  (region.regionId || '').toUpperCase()
                ]}
              >
                <AWSRegionTag
                  showProvider
                  regionUID={region.regionId}
                  style={{fontSize: 'larger'}}
                />
                <span>{region.name}</span>
              </Select.Option>
            ))
          }
        </Select.OptGroup>
      </Select>
    );
  }
}

ProviderFilter.propTypes = {
  filter: PropTypes.array,
  onChange: PropTypes.func
};

export default inject('cloudRegionsInfo')(observer(ProviderFilter));
