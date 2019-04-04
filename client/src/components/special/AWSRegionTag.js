/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import PropTypes from 'prop-types';
import styles from './AWSRegionTag.css';
import './AWSRegionTagFlags.less';

@inject('awsRegions')
@observer
export default class AWSRegionTag extends React.Component {

  static propTypes = {
    regionId: PropTypes.number,
    regionUID: PropTypes.string,
    style: PropTypes.object,
    className: PropTypes.string,
    size: PropTypes.oneOf(['small', 'normal', 'large']),
    displayName: PropTypes.bool,
    displayFlag: PropTypes.bool,
    provider: PropTypes.string,
    flagBorder: PropTypes.oneOfType([
      PropTypes.bool,
      PropTypes.string,
      PropTypes.shape({
        size: PropTypes.number,
        color: PropTypes.string
      })
    ])
  };

  static defaultProps = {
    size: 'normal',
    displayName: false,
    displayFlag: true,
    flagBorder: false
  };

  @computed
  get region () {
    if (this.props.awsRegions.loaded && this.props.regionId) {
      return this.props.awsRegions.getRegion(this.props.regionId);
    }
    if (this.props.awsRegions.loaded && this.props.regionUID) {
      return this.props.awsRegions.getRegionByUID(this.props.regionUID);
    }
    return null;
  }

  @computed
  get zone () {
    if (this.region) {
      return this.region.regionId;
    }
    if (this.props.regionUID) {
      return this.props.regionUID;
    }
    return null;
  }

  @computed
  get provider () {
    if (this.region) {
      return this.region.provider;
    }
    return this.props.provider;
  }

  @computed
  get flagClassName () {
    if (this.zone) {
      let getGlobalFn = () => this.zone.toLowerCase().split('-')[0];
      if (this.provider === 'AZURE' || this.zone.split('-').length === 1) {
        getGlobalFn = () => {
          const checkZones = [
            {
              check: ['germany', 'france', 'northeurope', 'westeurope'],
              result: 'eu'
            },
            {
              check: ['canada'],
              result: 'ca'
            },
            {
              check: ['china'],
              result: 'cn'
            },
            {
              check: ['korea'],
              result: 'ap ap-northeast-2'
            },
            {
              check: ['japan'],
              result: 'ap ap-northeast-3'
            },
            {
              check: ['india'],
              result: 'ap ap-south-1'
            },
            {
              check: ['australia'],
              result: 'ap ap-southeast-2'
            },
            {
              check: ['eastasia'],
              result: 'ap ap-southeast-1'
            },
            {
              check: ['brazil'],
              result: 'sa'
            },
            {
              check: ['uk'],
              result: 'eu'
            },
            {
              check: ['us'],
              result: 'us'
            }
          ];
          const zone = this.zone.toLowerCase();
          const [result] = checkZones.filter(z => {
            const [r] = z.check.filter(c => zone.indexOf(c) >= 0);
            return !!r;
          });
          if (result) {
            return result.result;
          }
          return '';
        };
      } else if (this.provider === 'GCP') {
        getGlobalFn = () => {
          const checkZones = [
            {
              region: 'asia',
              subRegion: 'east1',
              result: 'taiwan'
            },
            {
              region: 'asia',
              subRegion: 'east2',
              result: 'cn'
            },
            {
              region: 'asia',
              subRegion: 'northeast1',
              result: 'ap ap-northeast-1'
            },
            {
              region: 'asia',
              subRegion: 'south1',
              result: 'ap ap-south-1'
            },
            {
              region: 'asia',
              subRegion: 'southeast1',
              result: 'ap ap-southeast-1'
            },

            {
              region: 'australia',
              result: 'ap ap-southeast-2'
            },
            {
              region: 'europe',
              result: 'eu'
            },
            {
              region: 'northamerica',
              result: 'ca'
            },
            {
              region: 'southamerica',
              result: 'sa'
            },
            {
              region: 'us',
              result: 'us'
            }
          ];
          const zone = this.zone.toLowerCase();
          const [region, subRegion] = zone.split('-');
          let [result] = checkZones.filter(z => {
            return z.region.toLowerCase() === region &&
              (z.subRegion || '').toLowerCase() === (subRegion || '');
          });
          if (!result) {
            [result] = checkZones.filter(z => z.region.toLowerCase() === region);
          }
          if (result) {
            return result.result;
          }
          return '';
        };
      }
      const global = getGlobalFn();
      return `${styles.flag} flag ${global} ${this.zone.toLowerCase()}`;
    }
    return null;
  }

  render () {
    const parts = [];
    if (this.props.displayFlag && this.flagClassName) {
      const flagStyle = {};
      if (this.props.flagBorder &&
        typeof this.props.flagBorder === 'string' &&
        this.props.flagBorder.toLowerCase() !== 'false') {
        flagStyle.boxShadow = '0 0 0 1px black';
      } else if (this.props.flagBorder &&
        typeof this.props.flagBorder === 'boolean' &&
        this.props.flagBorder) {
        flagStyle.boxShadow = '0 0 0 1px black';
      } else if (this.props.flagBorder && typeof this.props.flagBorder === 'object') {
        const size = this.props.flagBorder.size || 1;
        const color = this.props.flagBorder.color || 'black';
        flagStyle.boxShadow = `0 0 0 ${size}px ${color}`;
      }
      parts.push(
        <span
          key="flag"
          style={flagStyle}
          className={`${this.flagClassName} ${styles[this.props.size]}`} />
      );
    }
    if (this.props.displayName && this.region) {
      parts.push(
        <span key="name" className={styles.title}>{this.region.name}</span>
      );
    }
    if (parts.length > 0) {
      return (
        <span
          style={this.props.style}
          className={`${styles.container} ${styles[this.props.size]} ${this.props.className || ''}`}>
          {parts}
        </span>
      );
    }
    return null;
  }
}
