import { defineConfig } from 'vite';
import { resolve } from 'node:path';
import react from '@vitejs/plugin-react';
import dts from 'vite-plugin-dts';
import tailwindcss from 'tailwindcss';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react(), dts({ include: ['lib'] })],
  build: {
    emptyOutDir: true,
    lib: {
      entry: resolve(__dirname, 'lib/index.ts'),
      formats: ['es', 'umd'],
      name: 'index',
      fileName: (format) => `index.${format}.js`,
    },
    rollupOptions: {
      external: [
        'react',
        'react/jsx-runtime',
        'react-dom',
        'react-router-dom',
        'react-router',
        'tailwindcss',
        'classnames',
        '@heroicons/react',
        '@headlessui/react',
        '@cloud-pipeline/core',
        '@epam/uui',
        '@epam/uui-components',
        '@epam/uui-core',
      ],
      output: {
        globals: {
          react: 'React',
          'react-dom': 'ReactDOM',
          'react/jsx-runtime': 'jsxRuntime',
          tailwindcss: 'tailwindcss',
          classnames: 'classnames',
          '@epam/uui': 'epamuui',
        },
      },
    },
    sourcemap: true,
  },
  css: {
    postcss: {
      plugins: [tailwindcss],
    },
  },
});
