# Validation of "white list" flag

Test verifies that adding an unscanned tool version to the white list allows a non-admin user to run it without a security warning.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin |  |
| 2 | Open the system **Settings** window |  |
| 3 | Select the **Preferences** tab |  |
| 4 | Click **Docker security** menu item in the left panel |  |
| 5 | Set the `security.tools.policy.deny.not.scanned` checkbox if it's unset |  |
| 6 | Input `0` into the `security.tools.grace.hours` field |  |
| 7 | Click **Save** button, click **OK** button |  |
| 8 | Repeat steps 1-11 of the [EPMCMBIBPC-1994](EPMCMBIBPC-1994.md) case |  |
| 9 | Open the tool from step 8 (`shell`) |  |
| 10 | Click **VERSIONS** tab |  |
| 11 | Click **VIEW UNSCANNED VERSIONS** |  |
| 12 | Click **Add to white list** button opposite the **latest** version | <li> **Add to white list** button opposite the **latest** version is not displayed <li> **Remove from white list** button opposite the **latest** version is displayed <li> the row of the **latest** version is colored green |
| 13 | Logout |  |
| 14 | Login as user |  |
| 15 | Open **Tools** page |  |
| 16 | Select and open the tool from step 8 (`shell`) |  |
| 17 | Click **VERSIONS** tab |  |
| 18 | Click **VIEW UNSCANNED VERSIONS** | <li> **Run** button opposite the **latest** version is enabled and has the exclamation-mark icon <li> the row of the **latest** version is colored green |
| 19 | Click the **v** button near the **Run** button opposite the **latest** version |  |
| 20 | Click **Custom settings** in the appeared list | Pop-up with the text `The version shall be scanned for security vulnerabilities. Run anyway?` appears |
| 21 | Click **OK** button in the appeared pop-up | Tool launch page appears |

**After**:
- Login as admin and restore the system preferences changed at steps 5 and 6 to their initial values
