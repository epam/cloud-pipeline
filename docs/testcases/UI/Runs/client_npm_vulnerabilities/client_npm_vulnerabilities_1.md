# Console output shows the ANSI escape codes as styles and the HTML of a log as text

Test verifies that the console output of a run log:
- shows the color and the bold text that ANSI escape codes set
- shows the HTML special characters of a log line as text
- shows an `https` hyperlink of the `OSC 8` escape code as a link, and leaves out a `javascript:` one
- shows the lines that follow an unterminated hyperlink

**Prerequisites**:
- Non-admin user with the permission to launch a test tool
- Test tool with `bash` and `base64`, e.g. `library/centos`

**Preparations**:
1. The command below prints the log lines of the *Table* at the end of this case. They are encoded with `base64`, so that the launch form and the shell change none of their quotes, backslashes or escape codes:
    ```
    echo G1szMW1yZWQgdGV4dBtbMG0gcGxhaW4gdGV4dAobWzFtYm9sZCB0ZXh0G1swbQo8Yj5ub3QgYm9sZDwvYj4gJiAicXVvdGVkIiB0ZXh0ChtdODs7aHR0cHM6Ly9leGFtcGxlLmNvbRtcZXhhbXBsZSBsaW5rG104OzsbXAobXTg7O2phdmFzY3JpcHQ6YWxlcnQoMSkbXHVuc2FmZSBsaW5rG104OzsbXCBhZnRlciB0aGUgdW5zYWZlIGxpbmsKbGluayAbXTg7O2h0dHBzOi8vZXhhbXBsZS5jb20HdW50ZXJtaW5hdGVkCmFmdGVyIHRoZSB1bnRlcm1pbmF0ZWQgbGluawo= | base64 -d; sleep infinity
    ```

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the user from the prerequisites | |
| 2 | Open the **Tools** page | |
| 3 | Select the test tool | |
| 4 | Hover over the **v** button next to **Run**, click **Custom settings** in the list that appears | |
| 5 | On the **Launch** page, expand the **Advanced** section | |
| 6 | Input the command from the Preparations into the **Cmd template** field | |
| 7 | Click **Launch**, then click **Launch** in the pop-up that appears | |
| 8 | On the **Runs** page, click the just-launched run (save the run ID) | The run logs page opens |
| 9 | Wait until the run gets the **Running** state, and the **Console** task appears in the tasks list | |
| 10 | Click the **Console** task | The console output contains the lines of the *Table* below, each shown as its *Expected view* says |
| 11 | Click the `example link` link | The `https://example.com` page opens |

| Line | Printed (`ESC` is the escape character, `BEL` the bell one) | Expected view |
| --- | --- | --- |
| 1 | `ESC[31m` `red text` `ESC[0m` ` plain text` | `red text plain text`: `red text` is red, `plain text` has the default color |
| 2 | `ESC[1m` `bold text` `ESC[0m` | `bold text` is bold, with the default color |
| 3 | `<b>not bold</b> & "quoted" text` | `<b>not bold</b> & "quoted" text` as it is: the tags are text, `not bold` is not bold |
| 4 | `ESC]8;;https://example.com ESC\` `example link` `ESC]8;; ESC\` | `example link` as a link to `https://example.com` |
| 5 | `ESC]8;;javascript:alert(1) ESC\` `unsafe link` `ESC]8;; ESC\` ` after the unsafe link` | ` after the unsafe link` only: no link and no `unsafe link` text, and no alert pop-up appears |
| 6 | `link ` `ESC]8;;https://example.com BEL` `unterminated` | `link ` only |
| 7 | `after the unterminated link` | `after the unterminated link`: the lines after the 6th one are shown |

**After**:
- Stop the run launched at step 7
