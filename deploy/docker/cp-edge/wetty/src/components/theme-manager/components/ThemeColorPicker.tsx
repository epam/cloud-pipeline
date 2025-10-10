import { useCallback } from "react";
import Input from "../../shared/input";
import { rgbStringToHex } from "../../utils/colors";
import type { ParameterValue } from "../../utils/types";

type ThemeColorPickerProps = {
  labelWidth: number | string;
  label: string;
  value: ParameterValue;
  onChange: (value: ParameterValue) => void;
};

export default function ThemeColorPicker({
  label,
  value,
  onChange,
  labelWidth,
}: ThemeColorPickerProps) {
  const onInputChange = useCallback(
    (nextValue: string | boolean) => {
      if (typeof nextValue === "string") {
        onChange(nextValue);
      }
    },
    [onChange]
  );
  return (
    <Input
      label={label}
      type="color"
      style={{
        label: {
          display: "flex",
          width: labelWidth,
          justifyContent: "flex-end",
          paddingRight: 8,
        },
        input: { padding: 0 },
        wrapper: { display: "flex", flexDirection: "row" },
      }}
      value={typeof value === "string" ? (rgbStringToHex(value) as string) : ""}
      onChange={onInputChange}
    />
  );
}
