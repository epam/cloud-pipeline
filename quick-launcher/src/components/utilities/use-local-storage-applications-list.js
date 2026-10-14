import {useCallback, useEffect, useState} from 'react';

export default function useLocalStorageApplicationsList (key, property = 'id') {
  const [list, setList] = useState([]);
  useEffect(() => {
    try {
      const storageValue = JSON.parse(localStorage.getItem(key))
      if (Array.isArray(storageValue)) {
        setList(storageValue);
      }
    } catch (_) {}
  }, [setList]);
  const updateList = useCallback((o) => {
    localStorage.setItem(key, JSON.stringify(o || []));
    setList(o || []);
  }, [setList]);
  const isInList = useCallback((application) => {
    return !!application && !!list.find(p => `${application[property]}` === `${p}`);
  }, [list, property]);
  const onChange = useCallback((application, isInList) => {
    if (application) {
      updateList([
        ...list.filter(o => `${o}` !== `${application[property]}`),
        ...(isInList ? [application[property]] : [])
      ]);
    }
  }, [list, updateList, property]);
  const toggleIsInList = useCallback((application) => {
    onChange(application, !isInList(application));
  }, [onChange, isInList]);
  const sorter = useCallback((a, b) => {
    const aIsInList = isInList(a);
    const bIsInList = isInList(b);
    if (aIsInList === bIsInList) {
      return 0;
    }
    if (aIsInList) {
      return -1;
    }
    return 1;
  }, [isInList]);
  return {
    isInList,
    toggleIsInList,
    onChange,
    sorter
  };
}
