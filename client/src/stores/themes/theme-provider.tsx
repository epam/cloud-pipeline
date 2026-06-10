import {ReactNode, useEffect, useMemo} from 'react';
import {App as AntdApp, ConfigProvider} from 'antd';
import enUS from 'antd/locale/en_US';
import AntdStaticMethodsProvider from '../../components/main/AntdStaticMethodsProvider.jsx';
import buildAntdTheme from '../../themes/tokens/antd-theme-config.js';
import {DefaultLightThemeIdentifier} from '../../themes/themes.js';
import {useCurrentTheme, useCurrentThemeConfiguration, useThemesLoaded} from './hooks.ts';
import {
  fetchThemesPreference,
  initializeThemesLocally,
  onAppReadyForThemePreferences,
} from './themes-store.ts';

// ConfigProvider only mounts DesignTokenContext when `theme` is truthy; switching
// from undefined → defined remounts the entire subtree (including LoadingPage).
const fallbackAntdTheme = buildAntdTheme({}, DefaultLightThemeIdentifier);

type ThemeProviderProps = {
  children: ReactNode;
  className?: string;
};

function ThemeProvider({children, className}: ThemeProviderProps) {
  useEffect(() => {
    initializeThemesLocally();
  }, []);

  useEffect(() => {
    return onAppReadyForThemePreferences(() => {
      fetchThemesPreference();
    });
  }, []);

  const loaded = useThemesLoaded();
  const currentTheme = useCurrentTheme();
  const currentThemeConfiguration = useCurrentThemeConfiguration();

  const antdTheme = useMemo(() => {
    if (!loaded || !currentThemeConfiguration) {
      return fallbackAntdTheme;
    }
    return buildAntdTheme(currentThemeConfiguration, currentTheme);
  }, [loaded, currentThemeConfiguration, currentTheme]);

  return (
    <ConfigProvider locale={enUS} theme={antdTheme}>
      <AntdStaticMethodsProvider theme={antdTheme}>
        <AntdApp className={className}>{children}</AntdApp>
      </AntdStaticMethodsProvider>
    </ConfigProvider>
  );
}

export {ThemeProvider};
