# Debugging Notes

Common debugging issues and their solutions for cp-client projects.

## Source Maps Configuration

### Problem: Breakpoints Open Transpiled Files

**Symptom**: When debugging, breakpoints in workspace packages (`cp-client-api`, `cp-client-common`, `cp-client-tunnel`) open transpiled `dist/**/*.js` files instead of original `src/**/*.ts` files.

**Root Cause**: VS Code debugger doesn't know where to look for source maps of workspace dependencies without explicit configuration.

### Solution

Add all workspace dependencies to the `outFiles` array in `.vscode/launch.json`:

```jsonc
{
  "name": "Debug Configuration",
  "type": "node",
  "request": "launch",
  "program": "${workspaceFolder}/dist/cli.js",
  "sourceMaps": true,
  "outFiles": [
    "${workspaceFolder}/dist/**/*.js",
    "${workspaceFolder}/../cp-client-tunnel/dist/**/*.js",
    "${workspaceFolder}/../cp-client-api/dist/**/*.js",
    "${workspaceFolder}/../cp-client-common/dist/**/*.js"
  ],
  // ... other settings
}
```

### Why This Works

The `outFiles` setting tells VS Code debugger:
1. Where to find transpiled JavaScript files
2. Where to look for associated source maps (`.js.map`)
3. How to map breakpoints from `dist/*.js` back to `src/*.ts`

Without explicit paths to workspace dependencies, the debugger falls back to opening the transpiled `.js` files directly.

### Verification Checklist

Before debugging, ensure:

- ✅ All workspace packages have `sourceMap: true` and `inlineSources: true` in `tsconfig.json`
- ✅ Each dependency's `dist/` folder is included in `outFiles` array
- ✅ Source map files (`*.js.map`) exist alongside transpiled files in `dist/` folders
- ✅ All packages are built with `npm run build` before debugging

### Related Configuration

#### tsconfig.json (Each Package)

```jsonc
{
  "compilerOptions": {
    "sourceMap": true,        // Generate .js.map files
    "inlineSources": true,    // Embed source code in maps
    "declarationMap": true    // Generate .d.ts.map for types
  }
}
```

#### .vscode/launch.json

```jsonc
{
  "configurations": [
    {
      "sourceMaps": true,     // Enable source map support
      "outFiles": [/* ... */] // List all output directories
    }
  ]
}
```

### Troubleshooting

**Problem**: Still opening `.js` files after adding `outFiles`

Possible solutions:
1. Rebuild all packages: `npm run build` in each dependency
2. Restart VS Code debugger completely
3. Check that `.js.map` files exist in all `dist/` folders
4. Verify source map paths with: `cat dist/index.js.map | grep sources`

**Problem**: Source maps point to wrong paths

Check `sourceRoot` in `.js.map` files - should be empty or `""` for relative paths to work correctly.
