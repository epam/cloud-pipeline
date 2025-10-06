import { useCallback } from "react";
import Input from "../../shared/input";
import { rgbStringToHex } from "../../utils/colors";

type ThemeColorPickerProps = {
  labelWidth: number | string;
  label: string;
  value: string | undefined;
  onChange: (value: string) => void;
}

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
        label: { width: labelWidth },
        input: { padding: 0 },
        wrapper: { display: "flex", flexDirection: "row" },
      }}
      value={value ? (rgbStringToHex(value) as string) : ""}
      onChange={onInputChange}
    />
  );
}
