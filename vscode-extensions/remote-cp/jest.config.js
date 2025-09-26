module.exports = {
  preset: 'ts-jest/presets/default-esm', 
  testEnvironment: 'node',
  extensionsToTreatAsEsm: ['.ts'],       
  globals: {
    'ts-jest': {
      useESM: true,                      
    },
  },
  // only consider unit tests and ignore other test folders
  roots: ['<rootDir>/src/unit'],
  testPathIgnorePatterns: ['<rootDir>/src/test/'],
};