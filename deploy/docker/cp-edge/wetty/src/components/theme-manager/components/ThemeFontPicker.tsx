import { useCallback, useMemo } from 'react';
import Select from '../../shared/select';
import { FONT_CHOICES, removeFallbacks, addFallbacks } from '../../utils/fonts';
import type { ParameterValue } from '../../utils/types';

type ThemeFontPickerProps = {
	labelWidth: number | string;
	label?: string;
	value: string | undefined;
	onChange: (value: ParameterValue) => void;
	className?: string;
};

export default function ThemeFontPicker({
	label = 'Font family',
	value,
	onChange,
	labelWidth,
	className,
}: ThemeFontPickerProps) {
	const options = useMemo(() => {
		const opts = FONT_CHOICES.map(f => ({
      value: removeFallbacks(f.family),
      label: f.label
    }));
    const first = removeFallbacks(value ?? '');
		if (first && !opts.some(o => o.value === first)) {
			opts.unshift({ value: first, label: `${first} (unregistered font)` });
		}
		return opts;
	}, [value]);

  const onInputChange = useCallback((value: string) => {
    onChange(addFallbacks(value));
  }, [onChange]);

  const trimmedValue = useMemo(() => {
    if (!value) return '';
    return removeFallbacks(value);
  }, [value]);

	return (
		<Select
			label={label}
			className={className}
			wrapperClassName={className || ''}
			style={{
				label: { display: 'flex', width: labelWidth, justifyContent: "flex-end", paddingRight: 8 },
				input: { flex: '1 0' },
			}}
			value={trimmedValue || ''}
			onChange={(v) => typeof v === 'string' && onInputChange(v)}
			placeholder="Select font"
			options={options}
			fullWidth
		/>
	);
}