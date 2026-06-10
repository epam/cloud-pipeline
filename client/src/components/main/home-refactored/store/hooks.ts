import {useCallback, useEffect, useRef} from 'react';
import {useStore} from 'zustand';
import {useNavigate, useLocation} from 'react-router-dom';
import continuousFetch from '../../../../utils/continuous-fetch';
import dayjs from '../../../../utils/dayjs';
import {getAuthenticatedUser} from '../../../../stores/users/hooks.ts';
import {HOME_CONTINUOUS_FETCH_IDS, HOME_UPDATE_TIMEOUT_MS} from './constants.ts';
import {homeStore} from './home-store.ts';
import type {HomeDataSources, PanelHandle, PanelLayoutItem} from './types.ts';

export function useHomeStore() {
  return useStore(homeStore);
}

export function useHomePanelsLayout(): PanelLayoutItem[] {
  return useStore(homeStore, (state) => state.panelsLayout);
}

export function useHomeLayoutLoaded(): boolean {
  return useStore(homeStore, (state) => state.layoutLoaded);
}

export function useHomeLayoutError(): string | undefined {
  return useStore(homeStore, (state) => state.layoutError);
}

export function useHomeConfigureModalVisible(): boolean {
  return useStore(homeStore, (state) => state.configureModalVisible);
}

export function useHomeDataSources(): HomeDataSources | undefined {
  return useStore(homeStore, (state) => state.dataSources);
}

export function useLegacyRouter() {
  const navigate = useNavigate();
  const location = useLocation();
  return {
    push: navigate,
    replace: (to: string) => navigate(to, {replace: true}),
    location,
  };
}

export function useHomeInitialization() {
  const layoutLoaded = useHomeLayoutLoaded();
  const layoutError = useHomeLayoutError();
  const initializeLayout = useStore(homeStore, (state) => state.initializeLayout);
  const initializeDataSources = useStore(homeStore, (state) => state.initializeDataSources);

  useEffect(() => {
    initializeLayout().catch(() => undefined);
  }, [initializeLayout]);

  useEffect(() => {
    const user = getAuthenticatedUser();
    if (user.userName && user.userName !== 'NOT_AUTHENTICATED') {
      initializeDataSources(user.userName);
    }
  }, [initializeDataSources]);

  return {layoutLoaded, layoutError};
}

export function useHomeContainerDimensions() {
  const containerWidth = useStore(homeStore, (state) => state.containerWidth);
  const containerHeight = useStore(homeStore, (state) => state.containerHeight);
  const setContainerDimensions = useStore(homeStore, (state) => state.setContainerDimensions);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const resizeTimeoutRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  const updateDimensions = useCallback(() => {
    const container = containerRef.current;
    if (!container) {
      return;
    }
    setContainerDimensions(
      container.clientWidth || window.innerWidth,
      container.clientHeight || window.innerHeight,
    );
  }, [setContainerDimensions]);

  const assignContainerRef = useCallback(
    (node: HTMLDivElement | null) => {
      containerRef.current = node;
      if (node) {
        setContainerDimensions(
          node.clientWidth || window.innerWidth,
          node.clientHeight || window.innerHeight,
        );
      }
    },
    [setContainerDimensions],
  );

  useEffect(() => {
    const onWindowResized = () => {
      if (resizeTimeoutRef.current) {
        clearTimeout(resizeTimeoutRef.current);
      }
      resizeTimeoutRef.current = setTimeout(updateDimensions, 250);
    };
    window.addEventListener('resize', onWindowResized);
    return () => {
      window.removeEventListener('resize', onWindowResized);
      if (resizeTimeoutRef.current) {
        clearTimeout(resizeTimeoutRef.current);
      }
    };
  }, [updateDimensions]);

  return {
    containerRef,
    assignContainerRef,
    containerWidth,
    containerHeight,
  };
}

export function useHomePolling() {
  const refreshActiveRuns = useStore(homeStore, (state) => state.refreshActiveRuns);
  const refreshCompletedRuns = useStore(homeStore, (state) => state.refreshCompletedRuns);
  const refreshServices = useStore(homeStore, (state) => state.refreshServices);
  const refreshIssues = useStore(homeStore, (state) => state.refreshIssues);
  const refreshAll = useStore(homeStore, (state) => state.refreshAll);

  useEffect(() => {
    const stopFns: Array<() => void> = [];
    const register = (id: string, call: () => Promise<void>) => {
      const {stop} = continuousFetch({
        continuous: true,
        intervalMS: HOME_UPDATE_TIMEOUT_MS,
        call,
        fetchImmediate: true,
      });
      stopFns.push(stop as () => void);
    };

    register(HOME_CONTINUOUS_FETCH_IDS.activeRuns, refreshActiveRuns);
    register(HOME_CONTINUOUS_FETCH_IDS.completedRuns, refreshCompletedRuns);
    register(HOME_CONTINUOUS_FETCH_IDS.services, refreshServices);
    register(HOME_CONTINUOUS_FETCH_IDS.issues, refreshIssues);

    return () => {
      stopFns.forEach((stop) => stop());
      localStorage.setItem('LAST_VISITED', dayjs.utc().format('YYYY-MM-DD HH:mm:ss'));
    };
  }, [refreshActiveRuns, refreshCompletedRuns, refreshServices, refreshIssues]);

  return refreshAll;
}

export function useHomePanelRegistration(panelKey: string) {
  const registerPanel = useStore(homeStore, (state) => state.registerPanel);

  return useCallback(
    (handle: PanelHandle | null) => {
      registerPanel(panelKey, handle);
    },
    [panelKey, registerPanel],
  );
}

export {homeStore};
