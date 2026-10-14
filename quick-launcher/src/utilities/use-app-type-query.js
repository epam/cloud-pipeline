import { useEffect, useMemo, useState } from 'react';
import { escapeRegExp } from "../models/utilities/escape-reg-exp";

const QUERY_PARAMETER = 'app-type';

export function useAppTypeQuery(applicationTypes) {
  const initial = useMemo(() => {
    try {
      const url = new URL(document.location);
      return url.searchParams.get(QUERY_PARAMETER) || undefined;
    } catch (e) {
      console.warn(`Error parsing app type from query: ${e.message}`);
      console.warn(e);
    }
  }, []);
  const [appType, setAppType] = useState(initial);
  useEffect(() => {
    try {
      const url = new URL(document.location);
      if (appType) {
        url.searchParams.set(QUERY_PARAMETER, appType);
      } else {
        url.searchParams.delete(QUERY_PARAMETER);
      }
      window.history.pushState({}, '', url.href);
    } catch (e) {
      console.warn(`Error building query for app type: ${e.message}`);
      console.warn(e);
    }
  }, [appType]);
  const filtered = useMemo(() => {
    if (!appType) {
      return undefined;
    }
    const regExp = new RegExp(`^${escapeRegExp(appType)}$`, 'i');
    return applicationTypes.find((t) => regExp.test(t));
  }, [applicationTypes, appType]);
  return [filtered, setAppType];
}
