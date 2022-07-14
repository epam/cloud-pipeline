# Check Pipe CLI Config Store

Test verifies that user can store pipe CLI config in the installation directory.

**Prerequisites**:
- Admin user

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the admin user from the prerequisites | |
| 2 | Open the **Settings** page | |
| 3 | Click the **CLI** tab | |
| 4 | Click the **Pipe CLI** tab | |
| 5 | Select **`Linux-Binary`** from drop-down for ***Operation system:*** | |
| 6 | Click **Generate access key** button | Field ***CLI configure command:*** including pipe configure command with access key appears |
| 7 | Store `pipe config command` | |
| 8 |Open the Tools page | |
| 9 | Select any tool | |
| 10 | Run tool with *Custom settings* | |
| 11 | At the Runs page, click the just-launched run | |
| 12 | Wait until the SSH hyperlink appears | |
| 13 | Click the SSH hyperlink | |
| 14 | In the opened tab, enter and perform the `<pipe config command>` | The *log* window contains the row `Config storing mode is 'home-dir', target path '/root/.pipe/config.json'` |
| 15 | In the opened tab, enter and perform the `<pipe config command> --config-store install-dir` | The *log* window contains the row `Config storing mode is 'install-dir', target path '<install_dir>/config.json'` |