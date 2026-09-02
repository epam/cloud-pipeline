import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

import { Commands } from "../src/commands";

const allCommands: Set<string> = (() => {
  const resCommandList: string[] = [];
  function recurse(o: any) {
    Object.values(o).forEach((v) => {
      if (typeof v === "string") {
        resCommandList.push(v);
      } else {
        recurse(v);
      }
    });
  }
  recurse(Commands);
  return new Set<string>(resCommandList);
})();

interface PackageContributes {
  readonly commands?: { command: string }[];
  readonly menus?: Record<string, any>;
}
interface PackageJson {
  readonly contributes?: PackageContributes;
}

const pkg: PackageJson = (() => {
  const __filename = fileURLToPath(import.meta.url);
  const __dirname = path.dirname(__filename);

  const pkgPath = path.resolve(__dirname, "../package.json");
  const pkgContent: string = fs.readFileSync(pkgPath, "utf-8");
  return JSON.parse(pkgContent) as PackageJson;
})();

type CommandContribution = { command: string };
// Extract command identifiers from package.json contributions
// Extract command identifiers from package.json contributions
const contributedCommands =
  (pkg.contributes?.commands as CommandContribution[]) ?? [];
const pkgCommands = new Set<string>(contributedCommands.map((c) => c.command));

// Checks
const errors: string[] = [];

// Check for commands in Commands that are not in package.json
for (const command of allCommands) {
  if (!pkgCommands.has(command)) {
    const errMsg = `Command '${command}' is defined in 'commands.ts' but not declared in 'package.json'.`;
    errors.push(errMsg);
  }
}

// Check for commands in package.json that are not in Commands
for (const command of pkgCommands) {
  if (!allCommands.has(command)) {
    const errMsg = `Command '${command}' is declared in 'package.json' but not defined in 'commands.ts'.`;
    errors.push(errMsg);
  }
}

const menus = pkg.contributes?.menus || {};

// Recursive check for all menus
function checkMenu(obj: Record<string, any>, parentKey = ""): void {
  for (const key in obj) {
    const items = obj[key];
    if (Array.isArray(items)) {
      items.forEach((item: any): void => {
        if (item.command && !pkgCommands.has(item.command)) {
          errors.push(
            "Menu item in " +
              (parentKey || key) +
              " uses unknown command: " +
              item.command,
          );
        }
      });
    } else if (typeof items === "object") {
      checkMenu(items, key);
    }
  }
}

checkMenu(menus);

if (errors.length) {
  console.error("Menu validation failed:\n" + errors.join("\n"));
  process.exit(1); // остановка сборки
} else {
  console.log("Menu validation passed");
}
