import { useMemo } from "react";
import Input from "../../shared/input";
import { rgbStringToHex, TERMINAL_ANSI_DEFAULTS } from "../../utils/colors";
import { type ThemeConfig, ConfigKeys } from "../../utils/types";

type ANSIColorsProps = {
  themeConfig: ThemeConfig;
  onChange: (colors: Record<string, string>) => void;
};

export default function ANSIColors(props: ANSIColorsProps) {
  const { themeConfig, onChange } = props;
  const colors = useMemo<Record<string, string>>(
    () => themeConfig[ConfigKeys.colorPaletteOverrides] ?? {},
    [themeConfig]
  );
  const onInputChange = (key: string, value: string) => {
    const newColors = { ...colors } as Record<string, string>;
    newColors[String(key)] = value;
    onChange(newColors);
  };
  return (
    <div style={{ display: "flex", flexDirection: "column" }}>
      <span style={{ color: "var(--color-text-secondary)" }}>ANSI Colors:</span>
      <div style={{ display: "flex", flexWrap: "wrap" }}>
        {Array.from({ length: 16 }).map((_, idx) => {
          const color = colors[idx];
          const key = String(idx);
          return (
            <div key={key} style={{ width: "calc(100% / 8)" }}>
              <Input
                label={key}
                type="color"
                style={{
                  label: {
                    width: 18,
                    display: "flex",
                    justifyContent: "flex-end",
                  },
                  input: { padding: 0 },
                }}
                value={color
                  ? (rgbStringToHex(colors[key]) as string)
                  : TERMINAL_ANSI_DEFAULTS[idx]
                }
                onChange={(value) => onInputChange(key, value as string)}
              />
            </div>
          );
        })}
      </div>
    </div>
  );
}
