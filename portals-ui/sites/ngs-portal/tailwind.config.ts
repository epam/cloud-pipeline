import type { Config } from 'tailwindcss';

export default {
  content: ['./src/**/*.{html,js,jsx,ts,tsx}'],
  darkMode: ['class', '.dark-theme'],
  theme: {
    extend: {
      fontFamily: {
        serif: [
          '-apple-system',
          'blinkmacsystemfont',
          'Segoe UI',
          'roboto',
          'Helvetica Neue',
          'arial',
          'Noto Sans',
          'sans-serif',
          'Apple Color Emoji',
          'Segoe UI Emoji',
          'Segoe UI Symbol',
          'Noto Color Emoji',
        ],
      },
      boxShadow: {
        outline: '0 0 1px 1px var(--tw-shadow-color)',
        'centered-sm': '0 0 2px 0 var(--tw-shadow-color)',
        centered: '0 0 3px 0 var(--tw-shadow-color)',
        'centered-md': '0 0 6px 0 var(--tw-shadow-color)',
        'centered-lg': '0 0 15px 0 var(--tw-shadow-color)',
        'centered-xl': '0 0 25px 0 var(--tw-shadow-color)',
        'centered-2xl': '0 0 30px 0 var(--tw-shadow-color)',
      },
      backgroundImage: {
        'gradient-radial-t':
          'radial-gradient(ellipse at top, var(--tw-gradient-stops))',
        'gradient-radial-b':
          'radial-gradient(ellipse at bottom, var(--tw-gradient-stops))',
        'gradient-radial-l':
          'radial-gradient(ellipse at left, var(--tw-gradient-stops))',
        'gradient-radial-r':
          'radial-gradient(ellipse at right, var(--tw-gradient-stops))',
        'gradient-radial-c':
          'radial-gradient(ellipse at center, var(--tw-gradient-stops))',
        'gradient-radial':
          'radial-gradient(ellipse at center, var(--tw-gradient-stops))',
      },
    },
  },
  plugins: [],
} satisfies Config;
