# SSH terminal theme settings

Test verifies that user can specify SSH terminal theme settings.

**Prerequisites**:
- Admin user

**Preparations:**
1. Open the **Settings** page
2. Open **My Profile** tab
3. Check that `Light` option is selected in the *SSH terminal theme* field. Select `Light` option if needed and relogin.
4. Use a browser with no terminal theme kept from an earlier session, e.g. a new private window: the theme selected in the SSH terminal is kept in the browser

| Steps | Actions | Expected results |
|:-----:|--- |---|
| 1 | Login as the admin user from the prerequisites | |
| 2 | Open the **Tools** page | |
| 3 | Select test tool | |
| 4 | Launch a tool with default settings | |
| 5 | At the **Runs** page, click the just-launched run | |
| 6 | Wait until the **SSH** hyperlink appears | |
| 7 | Click the **SSH** hyperlink | SSH terminal opens with `Light` theme, and contains **Gear** icon in the top right corner |
| 8 | Click **Gear** icon | *Theme Settings* pop up opens that contains <ul> <li> *Theme* (`Light` theme is set by default) drop-down <li> *Background Color*, *Text Color*, *Cursor Color* color selectors, with the colors of the `Light` theme: `#fafafa`, `#333333` and `rgba(50, 50, 50, 0.5)` <li> *Bold text* checkbox <li> *Font size*, *Font family* selectors <li> 16 *ANSI Colors* color selectors <li> *Theme preview* panel with the `Light` theme <li> **Close** button </ul> **Reset to defaults** button is not shown |
| 9 | Click into the field near the *Theme* label	| Drop-down with list of all available themes appears: `Light`, `Default`, `Dracula`, `Solarized dark`, `Solarized light`, `Gruvbox dark`, `Nord`, `One dark`, `Monokai` |
| 10 | Select `Default` option | <li> *Background Color*, *Text Color*, *Cursor Color*  colors are changed accordingly: `rgb(16, 16, 16)`, `rgb(240, 240, 240)` and `rgba(255, 0, 0, 0.5)` <li> `Light` theme in the *Theme preview* panel is changed to `Default` <li> **Reset to defaults** button appears |
| 11 | Click **Close** button | `Light` theme of SSH terminal is changed to `Default` |
| 12 | Close SSH terminal and go back to the log page of run launched at step 4 | |
| 13 | Click the **SSH** hyperlink | SSH terminal opens and has `Default` theme |
| 14 | Click **Gear** icon |  *Theme Settings* pop up opens, with the colors and the *Theme preview* of the `Default` theme |
| 15 | Click **Reset to defaults** button | <li> *Background Color*, *Text Color*, *Cursor Color*  colors are changed accordingly <li> `Default` theme in the *Theme preview* panel is changed to `Light` theme <li> **Reset to defaults** button disappears  |
| 16 | Click **Close** button | `Default` theme of SSH terminal is changed to `Light` |

**After:**
- Stop the run launched at step 4
