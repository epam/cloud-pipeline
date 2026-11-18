# Search in the console output finds a text with the HTML special characters

Test verifies that the search in the console output of a run log (**Ctrl+F**):
- finds and highlights a text with `<`, `>`, `&` and quotes in it
- does not find a match inside the escapes of those characters, e.g. for `amp`, and does not change how they are shown
- takes `|` and `\` as characters: a search with them does not freeze the page or fail
- finds a match in a colored text and in the text of a link, with no regard to the case
- moves between the matches

**Prerequisites**:
- Perform the [client_npm_vulnerabilities_1](client_npm_vulnerabilities_1.md) case up to its step 10, so that the console output of its run is open

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Press **Ctrl+F** | A search field with the **Prev**, **Next** and **Close** buttons appears at the top of the console output |
| 2 | Type `<b>not bold</b> & "quoted"` into the search field | <li> `1 of 1` is shown next to the buttons <li> `<b>not bold</b> & "quoted"` is highlighted in the line `<b>not bold</b> & "quoted" text` |
| 3 | Clear the search field, type `&lt` | <li> No count is shown next to the buttons, and nothing is highlighted <li> The line `<b>not bold</b> & "quoted" text` is shown as it was |
| 4 | Clear the search field, type `&quot` | The same as at step 3 |
| 5 | Clear the search field, type `amp` | <li> `1 of 2` is shown next to the buttons <li> `amp` is highlighted in the text of the `example link` link and in the line `link ]8;;https://example.comunterminated` <li> The line `<b>not bold</b> & "quoted" text` is shown as it was, with its `&` |
| 6 | Clear the search field, type `a\|` | <li> No count is shown next to the buttons, and nothing is highlighted <li> The page stays responsive: the search field takes the next input |
| 7 | Clear the search field, type `a\` | The same as at step 6 |
| 8 | Clear the search field, type `\b` | The same as at step 6 |
| 9 | Clear the search field, type `RED TEXT` | <li> `1 of 1` is shown next to the buttons <li> `red text` is highlighted in the line `red text plain text` |
| 10 | Clear the search field, type `example link` | <li> `1 of 1` is shown next to the buttons <li> The text of the `example link` link is highlighted |
| 11 | Clear the search field, type `after the un` | <li> `1 of 2` is shown next to the buttons <li> `after the un` is highlighted in the lines ` after the unsafe link` and `after the unterminated link`, and its first match has a brighter background than the second one |
| 12 | Press **Enter** | `2 of 2` is shown next to the buttons, and the match in the line `after the unterminated link` has the brighter background |
| 13 | Press **Shift+Enter** | `1 of 2` is shown next to the buttons, and the match in the line ` after the unsafe link` has the brighter background |
| 14 | Press **Esc** | The search field disappears, and nothing is highlighted |

**After**:
- Stop the run launched at step 7 of the [client_npm_vulnerabilities_1](client_npm_vulnerabilities_1.md) case
