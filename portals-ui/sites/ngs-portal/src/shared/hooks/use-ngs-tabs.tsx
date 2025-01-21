import type { ReactNode } from 'react';
import { useMemo, useCallback, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

type Tab<T extends string = string> = {
  key: T;
  label: ReactNode;
  content: ReactNode;
  aside?: ReactNode[];
};

type Props<T extends string = string> = {
  entityId: number;
  tabs: Tab<T>[];
  generatePath: (entityId: number, tabId: T) => string;
};

export const useNgsTabs = <T extends string = string>({
  entityId,
  tabs,
  generatePath,
}: Props<T>) => {
  const { tabId } = useParams();
  const navigate = useNavigate();

  useEffect(() => {
    const availableTabs = tabs.map(({ key }) => key);

    const isValidTab = !tabId || (availableTabs as string[]).includes(tabId);

    if (!isValidTab) {
      navigate(generatePath(entityId, tabs[0].key));
    }
  }, [entityId, generatePath, navigate, tabId, tabs]);

  const handleChangeTab = useCallback(
    (key: string) => {
      navigate(generatePath(entityId, key as T));
    },
    [navigate, generatePath, entityId],
  );

  const activeTab = useMemo(
    () => tabs.find((tab) => tab.key === tabId) ?? tabs[0],
    [tabId, tabs],
  );

  return {
    activeTab,
    tabs,
    handleChangeTab,
  };
};
