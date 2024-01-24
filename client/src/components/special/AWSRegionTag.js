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
import classNames from 'classnames';
import styles from './AWSRegionTag.css';

@inject('awsRegions')
@observer
class AWSRegionTag extends React.Component {
  static propTypes = {
    className: PropTypes.string,
    displayFlag: PropTypes.bool,
    displayName: PropTypes.bool,
    flagStyle: PropTypes.object,
    plainMode: PropTypes.bool,
    provider: PropTypes.string,
    providerStyle: PropTypes.object,
    regionId: PropTypes.number,
    regionUID: PropTypes.string,
    showProvider: PropTypes.oneOf([true, false, undefined]),
    style: PropTypes.object
  };

  static defaultProps = {
    displayName: false,
    displayFlag: true,
    plainMode: false,
    showProvider: undefined
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
  get shouldDisplayProvider () {
    if (this.props.showProvider) {
      return true;
    }
    if (this.props.showProvider === false) {
      return false;
    }
    if (this.props.awsRegions.loaded) {
      return (this.props.awsRegions.value || [])
        .filter(r => r.provider)
        .filter((p, i, a) => a.indexOf(p) === i)
        .length > 1;
    }
    return false;
  }

  get zone () {
    if (this.region) {
      return this.region.regionId;
    }
    if (this.props.regionUID) {
      return this.props.regionUID;
    }
    return null;
  }

  get provider () {
    if (this.region && this.region.provider) {
      return this.region.provider.toUpperCase();
    }
    if (this.props.provider) {
      return this.props.provider.toUpperCase();
    }
    return null;
  }

  get zoneInfo () {
    if (this.zone) {
      const simpleZone = this.zone.toLowerCase().split('-')[0];
      let getGlobalFn = () => ({
        zone: simpleZone,
        result: simpleZone
      });
      if (this.provider === 'AZURE' || this.zone.split('-').length === 1) {
        getGlobalFn = () => {
          const checkZones = [
            {
              check: [
                'germany',
                'france',
                'northeurope',
                'westeurope',
                'eu',
                'europe',
                'ge',
                'fr',
                'it',
                'es',
                'be',
                'po',
                'fi',
                'sw'
              ],
              result: 'eu'
            },
            {
              check: ['canada', 'ca'],
              result: 'ca'
            },
            {
              check: ['china', 'ch'],
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
              check: ['us', 'usa'],
              result: 'us'
            }
          ];
          const zone = this.zone.toLowerCase();
          const [result] = checkZones.filter(z => {
            const [r] = z.check.filter(c => zone.indexOf(c) >= 0);
            return !!r;
          });
          if (result) {
            return {
              region: zone,
              result: result.result,
              zone
            };
          }
          return null;
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
          return {...result, zone};
        };
      }
      return getGlobalFn();
    }
    return null;
  }

  render () {
    const parts = [];
    if (this.provider && this.shouldDisplayProvider) {
      if (this.props.plainMode) {
        parts.push(this.provider.toLowerCase());
      } else {
        parts.push(
          <span
            className={
              classNames(
                styles.provider,
                'provider',
                this.provider.toLowerCase()
              )
            }
            key="provider"
            style={this.props.providerStyle}
          />
        );
      }
    }
    const info = this.zoneInfo;
    if (this.props.displayFlag && info) {
      if (this.props.plainMode) {
        parts.push(info.zone);
      } else {
        parts.push(
          <span
            className={
              classNames(
                styles.flag,
                'flag',
                info.result,
                info.zone.toLowerCase()
              )
            }
            key="flag"
            style={this.props.flagStyle}
          />
        );
      }
    }
    if (this.props.displayName && this.region) {
      if (this.props.plainMode) {
        if (parts.indexOf(this.region.name) === -1) {
          parts.push(this.region.name);
        }
      } else {
        parts.push(
          <span
            className={styles.title}
            key="name"
          >
            {this.region.name}
          </span>
        );
      }
    }
    if (parts.length > 0) {
      if (this.props.plainMode) {
        return <div className={styles.container}>{parts.join('/')}</div>;
      } else {
        return (
          <div
            style={this.props.style}
            className={
              classNames(
                styles.container,
                this.props.className
              )
            }>
            {parts}
          </div>
        );
      }
    }
    return null;
  }
}

export default AWSRegionTag;
