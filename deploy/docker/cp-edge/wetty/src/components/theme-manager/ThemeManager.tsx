import React, { useCallback, useEffect, useState } from 'react';
import Button from '../shared/button/Button';
import Input from '../shared/input';
import Select from '../shared/select';
import styles from './ThemeManager.module.css';
import type { Terminal } from '../utils/terminal';
import { DEFAULT_THEMES } from '../utils/types';

export type ThemeManagerProps = {
  onCancel: () => void;
  terminal?: Terminal;
};

const ConfigKeys = {
  backgroundColor: 'background-color',
  foregroundColor: 'foreground-color',
  fontSize: 'font-size',
  fontFamily: 'font-family',
  enableBold: 'enable-bold',
} as const;

type ConfigKeys = typeof ConfigKeys[keyof typeof ConfigKeys];

export type ThemeConfig = {
  [ConfigKeys.backgroundColor]?: string;
  [ConfigKeys.foregroundColor]?: string;
  [ConfigKeys.fontSize]?: string;
  [ConfigKeys.fontFamily]?: string;
  [ConfigKeys.enableBold]?: boolean;
};

const ThemeManager: React.FC<ThemeManagerProps> = ({ onCancel, terminal }) => {
  const [themeConfig, setThemeConfig] = useState<ThemeConfig>(
    Object.fromEntries(Object.values(ConfigKeys).map(key => ([key, undefined])))
  );
  const [theme, setTheme] = useState<string | undefined>(undefined);
  const updateThemeConfig = useCallback(() => {
    const config = Object.fromEntries(Object.values(ConfigKeys).map((key) => {
      const value = terminal!.prefs!.get(key);
      return [key, value];
    }));
    setThemeConfig(config);
  }, [terminal]);

  useEffect(() => {
    if (terminal?.prefs) {
      updateThemeConfig();
      if (theme !== terminal?.currentTheme) {
        setTheme(terminal?.currentTheme);
      }
    }
  }, [terminal?.currentTheme, terminal?.prefs, theme, updateThemeConfig]);

  const onInputChange = (field: keyof ThemeConfig, value: string) => {
    terminal!.prefs!.set(field, value);
    setThemeConfig(prev => ({
      ...prev,
      [field]: value
    }));
  };

  const onChangeTheme = async (value: string | number) => {
    if (typeof value === 'string') {
      await terminal!.setTheme(value);
      setTheme(value);
      updateThemeConfig();
    }
  };

  if (!terminal?.prefs) {
    return null;
  }

  return (
    <div className={styles.themeManager}>
      <div className={styles.themeManager__form}>
        <Select
          label="Theme"
          className={styles.themeManager__field}
          value={theme}
          onChange={onChangeTheme}
          placeholder="Select theme"
          options={Object.keys(DEFAULT_THEMES).map(key => ({
            value: key,
            label: key
          }))}
          size="md"
          fullWidth
        />
        <Input
          label="Background Color"
          type="color"
          className={styles.themeManager__field}
          value={themeConfig[ConfigKeys.backgroundColor]}
          onChange={(v) => typeof v === 'string' && onInputChange(ConfigKeys.backgroundColor, v)}
        />
        <Input
          label="Text Color"
          type="color"
          className={styles.themeManager__field}
          value={themeConfig[ConfigKeys.foregroundColor]}
          onChange={(v) => typeof v === 'string' && onInputChange(ConfigKeys.foregroundColor, v)}
        />
        <Select
          label="Font Size"
          className={styles.themeManager__field}
          value={themeConfig[ConfigKeys.fontSize]}
          onChange={(v) => typeof v === 'string' && onInputChange(ConfigKeys.fontSize, v)}
          placeholder="Select size"
          options={[
            { label: '12px', value: '12' },
            { label: '14px', value: '14' },
            { label: '16px', value: '16' },
            { label: '18px', value: '18' }
          ]}
          size="md"
          fullWidth
        />
      </div>
      <div className={styles.themeManager__actions}>
        <Button variant="secondary" onClick={onCancel}>
          Close
        </Button>
      </div>
    </div>
  );
};

export default ThemeManager;
