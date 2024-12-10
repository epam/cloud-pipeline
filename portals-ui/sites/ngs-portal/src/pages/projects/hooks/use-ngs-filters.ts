import { useCallback, useMemo, useState } from 'react';
import type { Pipeline, Project } from '@cloud-pipeline/core';
import { noop } from '@cloud-pipeline/core';
import type { TagFilters } from '../types';
import { loadUsersInfo } from '../../../state/users-info/load-users-info';
import { useUsersInfoState } from '../../../state/users-info/hooks';
import { NgsFilter } from '../../../shared/constants/filters';

export const useNgsFilters = () => {
  const [tagsToFilter, setTagsToFilter] = useState<TagFilters>({});
  const { usersInfo, pending: isUserInfoPending, loaded } = useUsersInfoState();

  const isMatchingFilters = useCallback(
    (item: Project | Pipeline, overrideFilters: TagFilters = {}) => {
      if (!Object.keys(tagsToFilter).length) {
        return true;
      }

      const effectiveFilters = Object.entries({
        ...tagsToFilter,
        ...overrideFilters,
      });

      return effectiveFilters.every(([filterName, values]) => {
        if (filterName === (NgsFilter.OWNER as string)) {
          return values.includes(item.owner);
        }

        const tagValue = item.data?.[filterName]?.value;
        return tagValue && values.includes(tagValue);
      });
    },
    [tagsToFilter],
  );

  const handleOwnersFilterFocus = useCallback(() => {
    if (!usersInfo?.length && !isUserInfoPending && !loaded) {
      loadUsersInfo().then(noop).catch(noop);
    }
  }, [isUserInfoPending, loaded, usersInfo?.length]);

  const handleFilterValueChange = useCallback(
    (tagName: string, selectedItems?: string[]) => {
      setTagsToFilter((prevTags) => {
        if (!selectedItems) {
          const newTags = { ...prevTags };
          delete newTags[tagName];
          return newTags;
        }

        return { ...prevTags, [tagName]: selectedItems };
      });
    },
    [],
  );

  return useMemo(
    () => ({
      handleOwnersFilterFocus,
      handleFilterValueChange,
      isMatchingFilters,
      tagsToFilter,
      usersInfo,
    }),
    [
      handleOwnersFilterFocus,
      handleFilterValueChange,
      isMatchingFilters,
      tagsToFilter,
      usersInfo,
    ],
  );
};
