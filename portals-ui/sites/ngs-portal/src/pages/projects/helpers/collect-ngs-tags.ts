import type { Pipeline, Project, UserInfo } from '@cloud-pipeline/core';
import type { FilterToDisplay, NgsTags, Tag } from '../types';
import { NgsFilter } from '../../../shared/constants/filters';

type Props = {
  items: (Project | Pipeline)[];
  users: UserInfo[];
  filtersToDisplay: FilterToDisplay[];
};

export const collectNgsTags = ({
  filtersToDisplay,
  items,
  users,
}: Props): NgsTags => {
  // Map for faster lookup
  const tagsMap: Record<string, Map<string, Tag>> = {};

  const isOwnerFilterAllowed =
    filtersToDisplay.find((tag) => tag.id === (NgsFilter.OWNER as string)) &&
    users.length > 0;

  if (isOwnerFilterAllowed) {
    tagsMap[NgsFilter.OWNER] = new Map(
      users.map((user) => [user.name, { id: user.name, count: 0 }]),
    );
  }

  const incrementTagCount = (key: string, value: string) => {
    if (!tagsMap[key]) {
      tagsMap[key] = new Map();
    }

    const currentTagMap = tagsMap[key];
    const tag = currentTagMap.get(value);

    if (tag) {
      tag.count++;
    } else {
      currentTagMap.set(value, { id: value, count: 1 });
    }
  };

  for (const { data, owner } of items) {
    // setup owner tags
    if (isOwnerFilterAllowed) {
      incrementTagCount(NgsFilter.OWNER, owner);
    }

    // setup the rest of tags
    if (!data) {
      continue;
    }

    for (const [key, { value }] of Object.entries(data)) {
      const isTagAllowed = filtersToDisplay.find((tag) => tag.id === key);

      if (!isTagAllowed) {
        continue;
      }

      incrementTagCount(key, value);
    }
  }

  // Convert maps to arrays
  const tags: NgsTags = {};
  for (const [key, tagMap] of Object.entries(tagsMap)) {
    tags[key] = Array.from(tagMap.values());
  }

  return tags;
};
