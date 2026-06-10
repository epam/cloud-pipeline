import {useQuery} from '@tanstack/react-query';
import {useMemo} from 'react';
import {CloudRegion} from '../../@types/regions.ts';
import {cloudRegionsQueryOptions} from './cloud-regions.ts';
import {QueryOptionsParams} from '../types.ts';

function findCloudRegion(
  regions: CloudRegion[],
  regionId?: string | number,
): CloudRegion | undefined {
  if (regionId === undefined || regionId === null) {
    return undefined;
  }
  if (typeof regionId === 'number' || !Number.isNaN(Number(regionId))) {
    const id = Number(regionId);
    return regions.find((region) => region.id === id);
  }
  return regions.find((region) => region.regionId.toLowerCase() === regionId.toLowerCase());
}

export function useCloudRegion(
  regionId: string | number | undefined,
  options?: QueryOptionsParams,
): CloudRegion | undefined {
  const {data: regions = []} = useQuery(cloudRegionsQueryOptions(options));
  return useMemo(() => findCloudRegion(regions, regionId), [regions, regionId]);
}
