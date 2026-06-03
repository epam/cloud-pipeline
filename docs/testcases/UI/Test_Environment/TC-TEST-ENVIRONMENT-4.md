# SSH page theme settings

Test verifies that user can specify SSH page theme settings.

**Prerequisites**:
- Admin user

| Steps | Actions | Expected results |
|:-----:|--- |---|
| 1 | Login as the admin user from the prerequisites | |
| 2 | Open the **Tools** page | |
| 3 | Select test tool | |
| 4 | Launch a tool with default settings | |
| 5 | At the **Runs** page, click the just-launched run | |
| 6 | Wait until the **SSH** hyperlink appears | |
| 7 | Click the **SSH** hyperlink | SSH page opens that contains **Gear** icon in the top right corner |
| 8 | Click **Gear** icon | *Theme Settings* pop up opens that contains <ul> <li> *Theme* (`Light` theme is set by default) drop-down <li> *Background Color*, *Text Color*, *Cursor Color* color selectors <li> *Bold text* checkbox <li> *Font size*, *Font family* selectors <li> *ANSI Colors* color selectors <li> *Theme preview* panel <li> **Close** button |
| 9 | Click into the field near the *Theme* label	| Drop-down with list of all available themes appears |
| 10 | Select `Default` option | <li> *Background Color*, *Text Color*, *Cursor Color*  colors are changed accordingly <li> `Light` theme in the *Theme preview* panel is changed to `Default` <li> **Reset to defaults** button appears |
| 11 | Click **Close** button | `Light` theme of SSH page is changed to `Default` |
| 12 | Close SSH page and go back to the log page of run launched at step 4 | |
| 13 | Click the **SSH** hyperlink | SSH page opens and has `Default` theme |
| 14 | Click **Gear** icon |  *Theme Settings* pop up opens |
| 15 | Click **Reset to defaults** button | <li> *Background Color*, *Text Color*, *Cursor Color*  colors are changed accordingly <li> `Default` theme in the *Theme preview* panel is changed to `Light` theme <li> **Reset to defaults** button disappears  |
| 16 | Click **Close** button | `Default` theme of SSH page is changed to `Light` |
