# Validation of unchecked "deny not scanned tool" option

Test verifies the behavior of running an unscanned tool version when the `security.tools.policy.deny.not.scanned` preference is disabled.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin |  |
| 2 | Open the system **Settings** window |  |
| 3 | Select the **Preferences** tab |  |
| 4 | Click **Docker security** menu item in the left panel |  |
| 5 | Unset the `security.tools.policy.deny.not.scanned` checkbox if it's set |  |
| 6 | Input `0` into the `security.tools.grace.hours` field |  |
| 7 | Click **Save** button, click **OK** button |  |
| 8 | Repeat steps 1-11 of the [EPMCMBIBPC-1994](EPMCMBIBPC-1994.md) case |  |
| 9 | Logout |  |
| 10 | Login as user |  |
| 11 | Open **Tools** page |  |
| 12 | Select and open the tool from step 8 (`shell`) | <li> label with the text `The latest version shall be scanned for vulnerabilities. You can try an older one.` is displayed below the header <li> **Run** button in the right upper corner is enabled and has the exclamation-mark icon |
| 13 | Click **VERSIONS** tab |  |
| 14 | Click **VIEW UNSCANNED VERSIONS** | **Run** buttons opposite the version names are enabled and have exclamation-mark icons |
| 15 | Hover the mouse pointer over the **Run** button opposite the **latest** version | Tooltip with the text `The latest version shall be scanned for vulnerabilities. You can try an older one.` appears |
| 16 | Click the **v** button near the **Run** button opposite the **latest** version |  |
| 17 | Click **Custom settings** in the appeared list | Pop-up with the text `The version shall be scanned for security vulnerabilities. Run anyway?` appears |
| 18 | Click **OK** button in the appeared pop-up | Tool launch page appears |

**After**:
- Login as admin and restore the system preferences changed at steps 5 and 6 to their initial values
