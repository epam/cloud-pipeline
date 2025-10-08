import React, { useCallback, useEffect, useMemo, useState } from "react";
import Button from "../shared/button/Button";
import Input from "../shared/input";
import Select from "../shared/select";
import styles from "./ThemeManager.module.css";
import { ConfigKeys, type ThemeConfig } from "../utils/types";
import ANSIColors from "./components/ANSIColors";
import { type ThemeManagerProps } from "./types";
import ThemeColorPicker from "./components/ThemeColorPicker";
import ThemeFontPicker from "./components/ThemeFontPicker";
import { checkConfigChanged, DEFAULT_THEMES } from "../utils/themes";
import ThemeCard from "./components/theme-card/ThemeCard";

const ThemeManager: React.FC<ThemeManagerProps> = ({ onCancel, terminal }) => {
  const [themeConfig, setThemeConfig] = useState<ThemeConfig>(
    Object.fromEntries(Object.values(ConfigKeys).map((key) => [key, undefined]))
  );
  const [theme, setTheme] = useState<string | undefined>(undefined);
  const updateThemeConfig = useCallback(() => {
    const config = Object.fromEntries(
      Object.values(ConfigKeys).map((key) => {
        const value = terminal!.prefs!.get(key);
        return [key, value];
      })
    );
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
  const onInputChange = (
    field: keyof ThemeConfig,
    value: string | Record<string, string> | boolean
  ) => {
    terminal!.prefs!.set(field, value);
    setThemeConfig((prev: ThemeConfig) => ({
      ...prev,
      [field]: value,
    }));
  };
  const onChangeTheme = async (value: string | number) => {
    if (typeof value === "string") {
      await terminal!.setTheme(value, true);
      setTheme(value);
      updateThemeConfig();
    }
  };
  const hasChanges = useMemo(
    () => checkConfigChanged(themeConfig, terminal),
    [themeConfig, terminal]
  );
  const onReset = async () => {
    await terminal!.resetTheme(terminal!.currentTheme);
    updateThemeConfig();
  };
  const labelWidth = 120;
  const labelStyle = useMemo(
    () => ({
      width: labelWidth,
      display: "flex",
      justifyContent: "flex-end",
      paddingRight: 8,
    }),
    [labelWidth]
  );
  if (!terminal?.prefs) {
    return null;
  }
  return (
    <div className={styles.themeManager}>
      <div className={styles.themeManager__form}>
        <Select
          label="Theme"
          className={styles.themeManager__field}
          wrapperClassName={styles.themeManager__field}
          style={{
            label: labelStyle,
            input: { flex: "1 0" },
          }}
          value={theme}
          onChange={onChangeTheme}
          placeholder="Select theme"
          options={Object.keys(DEFAULT_THEMES).map((key) => ({
            value: key,
            label: key.charAt(0).toUpperCase() + key.replaceAll('-', ' ').slice(1),
          }))}
          fullWidth
        />
        <ThemeColorPicker
          label="Background Color"
          value={themeConfig[ConfigKeys.backgroundColor]}
          onChange={(v) => onInputChange(ConfigKeys.backgroundColor, v)}
          labelWidth={labelWidth}
        />
        <ThemeColorPicker
          label="Text Color"
          value={themeConfig[ConfigKeys.foregroundColor]}
          onChange={(v) => onInputChange(ConfigKeys.foregroundColor, v)}
          labelWidth={labelWidth}
        />
        <ThemeColorPicker
          label="Cursor Color"
          value={themeConfig[ConfigKeys.cursorColor]}
          onChange={(v) => onInputChange(ConfigKeys.cursorColor, v)}
          labelWidth={labelWidth}
        />
        <Input type="checkbox" label="Bold text"
          style={{
            label: labelStyle,
            input: { marginLeft: 0 },
          }}
          className={styles.themeManager__field}
          checked={themeConfig[ConfigKeys.enableBold] === true ||
            themeConfig[ConfigKeys.enableBold] === null}
          onChange={(v) =>
            typeof v === "boolean" && onInputChange(ConfigKeys.enableBold, v ? true : false)
          }
        />
        <Input
          label="Font size"
          type="number"
          style={{
            label: labelStyle,
            input: { flex: "1 0" },
          }}
          min={6}
          max={32}
          className={styles.themeManager__field}
          value={themeConfig[ConfigKeys.fontSize]}
          onChange={(v) =>
            typeof v === "string" && onInputChange(ConfigKeys.fontSize, v)
          }
        />
        <ThemeFontPicker
          labelWidth={labelWidth}
          value={themeConfig[ConfigKeys.fontFamily]}
          onChange={(v) => onInputChange(ConfigKeys.fontFamily, v)}
          className={styles.themeManager__field}
        />
        <ANSIColors
          themeConfig={themeConfig}
          onChange={(v: Record<string, string>) =>
            typeof v === "object" &&
            onInputChange(ConfigKeys.colorPaletteOverrides, v)
          }
          style={{ marginBottom: 4 }}
        />
        <ThemeCard themeConfig={themeConfig} />
      </div>
      <div className={styles.themeManager__actions}>
        {hasChanges && (
          <Button variant="secondary" onClick={onReset}>
            Reset
          </Button>
        )}
        <Button variant="primary" onClick={onCancel}>
          Close
        </Button>
      </div>
    </div>
  );
};

export default ThemeManager;
