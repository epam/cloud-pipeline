import * as path from "path";
import { runTests } from "@vscode/test-electron";

async function main() {
  try {
    const extensionDevelopmentPath = path.resolve(__dirname, "../");
    const extensionTestsPath = path.resolve(__dirname, "./extension.test");

    await runTests({
      extensionDevelopmentPath,
      extensionTestsPath,
    });
    console.log("Tests passed");
  } catch (err) {
    console.error(`Tests failed.\n ${err}`);
    process.exit(1);
  }
}

main();
