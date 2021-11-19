const Sorting = {
  nameAsc: 'name-asc',
  nameDesc: 'name-desc',
  sizeAsc: 'size-asc',
  sizeDesc: 'size-desc',
  changedAsc: 'changed-asc',
  changedDesc: 'changed-desc',
};

const SortingProperty = {
  name: 'name',
  size: 'size',
  changed: 'changed',
};

const PropertySorters = {
  [SortingProperty.name]: [Sorting.nameAsc, Sorting.nameDesc],
  [SortingProperty.size]: [Sorting.sizeAsc, Sorting.sizeDesc],
  [SortingProperty.changed]: [Sorting.changedAsc, Sorting.changedDesc],
};

function sortingFactory (property, ascending) {
  return function sorting (item1, item2) {
    if (item1.isBackLink !== item2.isBackLink) {
      if (item1.isBackLink) {
        return -1;
      }
      if (item2.isBackLink) {
        return 1;
      }
    }
    let aValue = item1[property];
    let bValue = item2[property];
    if (typeof aValue === 'string' && typeof bValue === 'string') {
      aValue = (aValue || '').toLowerCase();
      bValue = (bValue || '').toLowerCase();
    }
    if (!aValue && !bValue) {
      return 0;
    }
    if (!aValue) {
      return 1;
    }
    if (!bValue) {
      return -1;
    }
    if (aValue < bValue) {
      return ascending ? -1 : 1;
    } else if (aValue > bValue) {
      return ascending ? 1 : -1;
    }
    return 0;
  }
}

function getSorter(sorting) {
  switch (sorting) {
    case Sorting.changedAsc: return sortingFactory(SortingProperty.changed, true);
    case Sorting.changedDesc: return sortingFactory(SortingProperty.changed, false);
    case Sorting.nameAsc: return sortingFactory(SortingProperty.name, true);
    case Sorting.nameDesc: return sortingFactory(SortingProperty.name, false);
    case Sorting.sizeAsc: return sortingFactory(SortingProperty.size, true);
    case Sorting.sizeDesc: return sortingFactory(SortingProperty.size, false);
    default:
      return sortingFactory('name', true);
  }
}

function nextSorter(current, property) {
  let currentProperty;
  switch (current) {
    case Sorting.changedAsc:
    case Sorting.changedDesc:
      currentProperty = SortingProperty.changed;
      break;
    case Sorting.sizeAsc:
    case Sorting.sizeDesc:
      currentProperty = SortingProperty.size;
      break;
    case Sorting.nameAsc:
    case Sorting.nameDesc:
    default:
      currentProperty = SortingProperty.name;
      break;
  }
  if (currentProperty !== property) {
    return PropertySorters[property][0];
  }
  const sorters = PropertySorters[property];
  const index = (sorters.indexOf(current) + 1) % sorters.length;
  return sorters[index];
}

function sort(sorting, data) {
  const result = (data || []).slice();
  result.sort(getSorter(sorting));
  return result;
}

export {
  Sorting,
  sort,
  nextSorter,
  PropertySorters,
  SortingProperty,
};
