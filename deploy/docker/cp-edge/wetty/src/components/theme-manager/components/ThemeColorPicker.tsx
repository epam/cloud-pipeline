import { useCallback, useMemo } from "react";
import Input from "../../shared/input";
import { getAlphaFromRgba, rgbStringToHex } from "../../utils/colors";

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
  const alpha = useMemo(() => (value ? getAlphaFromRgba(value) : null), [value]);
  return (
    <Input
      label={label}
      type="color"
      style={{
        label: { width: labelWidth },
        input: { padding: 0, opacity: alpha !== null && alpha < 1 ? alpha : 1 },
        wrapper: { display: "flex", flexDirection: "row" },
      }}
      value={value ? (rgbStringToHex(value) as string) : ""}
      onChange={onInputChange}
    />
  );
}
