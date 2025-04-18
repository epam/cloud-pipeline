import { defineConfig } from 'cypress';
import fs from 'fs';
import path from 'path';

const settingsPath = process.env.SETTINGS || './.dev/settings.json';

let settings = {};
try {
  const fullPath = path.resolve(settingsPath);
  const raw = fs.readFileSync(fullPath, 'utf-8');
  settings = JSON.parse(raw);
} catch (err) {
  console.warn(`⚠️ Could not read settings from ${settingsPath}:`, err);
}

export default defineConfig({
  e2e: {
    setupNodeEvents(on, config) {
      // implement node event listeners here
    },
    env: {
      API_BASE: settings['api'] || '/api',
    },
    baseUrl: 'http://127.0.0.1:5173',
    supportFile: 'cypress/support/e2e.js',
    chromeWebSecurity: false,
    specPattern: 'cypress/e2e/**/*.cy.{js,jsx,ts,tsx}',
  },
});
