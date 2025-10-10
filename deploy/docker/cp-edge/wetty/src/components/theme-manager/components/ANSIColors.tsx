import { useMemo } from "react";
import Input from "../../shared/input";
import { rgbStringToHex, TERMINAL_ANSI_DEFAULTS } from "../../utils/colors";
import { type ANSIPalette, type TerminalTheme, ConfigKeys } from "../../utils/types";
import type { CommonProps } from "../../../types/types";

type ANSIColorsProps = CommonProps & {
  parameters: TerminalTheme;
  onChange: (colors: Record<string, string>) => void;
};

export default function ANSIColors(props: ANSIColorsProps) {
  const { parameters, onChange, style = {} } = props;
  const colors = useMemo<ANSIPalette>(
    () => parameters[ConfigKeys.colorPaletteOverrides] ?? {},
    [parameters]
  );
  const onInputChange = (key: string, value: string) => {
    const newColors = { ...colors } as ANSIPalette;
    newColors[Number(key)] = value;
    onChange(newColors);
  };
  return (
    <div style={{ display: "flex", flexDirection: "column", ...style}}>
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
                  ? (rgbStringToHex(colors[idx]) as string)
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
