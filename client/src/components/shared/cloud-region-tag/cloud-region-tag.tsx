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

import React, {useMemo} from 'react';
import classNames from 'classnames';
import {CommonProps} from '../../../@types/common.ts';
import {useCloudRegion} from '../../../queries/cloud-regions/hooks.ts';
import {CloudRegion} from '../../../@types/regions.ts';
import './cloud-region-tag.css';
import {CloudProviderTag} from '../cloud-provider-tag/cloud-provider-tag.tsx';

type RegionTagProps = CommonProps & {
  regionId?: string | number;
  displayFlag?: boolean;
  displayName?: boolean;
  displayProvider?: boolean;
};

type GlobalFn = () => {zone: string; result: string; region?: string} | null;

function getZoneInfo(region: CloudRegion) {
  const {provider, regionId} = region;
  const simpleZone = regionId.toLowerCase().split('-')[0];
  let getGlobalFn: GlobalFn = () => ({
    zone: simpleZone,
    result: simpleZone,
  });
  if (provider === 'AZURE' || regionId.split('-').length === 1) {
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
            'sw',
          ],
          result: 'eu',
        },
        {
          check: ['canada', 'ca'],
          result: 'ca',
        },
        {
          check: ['china', 'ch'],
          result: 'cn',
        },
        {
          check: ['korea'],
          result: 'ap ap-northeast-2',
        },
        {
          check: ['japan'],
          result: 'ap ap-northeast-3',
        },
        {
          check: ['india'],
          result: 'ap ap-south-1',
        },
        {
          check: ['australia'],
          result: 'ap ap-southeast-2',
        },
        {
          check: ['eastasia'],
          result: 'ap ap-southeast-1',
        },
        {
          check: ['brazil'],
          result: 'sa',
        },
        {
          check: ['uk'],
          result: 'eu',
        },
        {
          check: ['us', 'usa'],
          result: 'us',
        },
      ];
      const zone = regionId.toLowerCase();
      const [result] = checkZones.filter((z) => {
        const [r] = z.check.filter((c) => zone.indexOf(c) >= 0);
        return !!r;
      });
      if (result) {
        return {
          region: zone,
          result: result.result,
          zone,
        };
      }
      return null;
    };
  } else if (provider === 'GCP') {
    getGlobalFn = () => {
      const checkZones = [
        {
          region: 'asia',
          subRegion: 'east1',
          result: 'taiwan',
        },
        {
          region: 'asia',
          subRegion: 'east2',
          result: 'cn',
        },
        {
          region: 'asia',
          subRegion: 'northeast1',
          result: 'ap ap-northeast-1',
        },
        {
          region: 'asia',
          subRegion: 'south1',
          result: 'ap ap-south-1',
        },
        {
          region: 'asia',
          subRegion: 'southeast1',
          result: 'ap ap-southeast-1',
        },

        {
          region: 'australia',
          result: 'ap ap-southeast-2',
        },
        {
          region: 'europe',
          result: 'eu',
        },
        {
          region: 'northamerica',
          result: 'ca',
        },
        {
          region: 'southamerica',
          result: 'sa',
        },
        {
          region: 'us',
          result: 'us',
        },
      ];
      const zone = regionId.toLowerCase();
      const [region, subRegion] = zone.split('-');
      let [result] = checkZones.filter((z) => {
        return (
          z.region.toLowerCase() === region &&
          (z.subRegion || '').toLowerCase() === (subRegion || '')
        );
      });
      if (!result) {
        [result] = checkZones.filter((z) => z.region.toLowerCase() === region);
      }
      return {...result, zone};
    };
  }
  return getGlobalFn();
}

function CloudRegionTag(props: RegionTagProps) {
  const {
    className,
    style,
    regionId,
    displayFlag = true,
    displayName = true,
    displayProvider = false,
  } = props;
  const region = useCloudRegion(regionId);
  const info = useMemo(() => (region ? getZoneInfo(region) : undefined), [region]);
  if (!region) return null;
  return (
    <div className={classNames(className, 'cloud-region-tag')} style={style}>
      {displayProvider && <CloudProviderTag regionId={regionId} />}
      {info && displayFlag && (
        <span
          className={classNames(
            'cloud-region-tag-flag',
            'flag',
            info.result,
            info.zone.toLowerCase(),
          )}
        />
      )}
      {displayName && <span className="cloud-region-tag-name">{region.name}</span>}
    </div>
  );
}

export {CloudRegionTag};
