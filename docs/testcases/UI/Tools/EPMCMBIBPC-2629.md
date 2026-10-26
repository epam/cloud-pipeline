# Validation of "grace period" option

Test verifies that an unscanned tool version can still be run during the configured grace period while the `security.tools.policy.deny.not.scanned` preference is enabled.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin |  |
| 2 | Open the system **Settings** window |  |
| 3 | Select the **Preferences** tab |  |
| 4 | Click **Docker security** menu item in the left panel |  |
| 5 | Set the `security.tools.policy.deny.not.scanned` checkbox if it's unset |  |
| 6 | Input `1` into the `security.tools.grace.hours` field |  |
| 7 | Click **Save** button, click **OK** button |  |
| 8 | Repeat steps 1-11 of the [EPMCMBIBPC-1994](EPMCMBIBPC-1994.md) case |  |
| 9 | Logout |  |
| 10 | Login as user |  |
| 11 | Open **Tools** page |  |
| 12 | Select and open the tool from step 8 (`shell`) | <li> label with the text `The latest version shall be scanned for vulnerabilities.` is displayed below the header <li> **Run** button in the right upper corner is enabled and has the exclamation-mark icon |
| 13 | Click the **v** button near the **Run** button in the right upper corner of the page |  |
| 14 | Click **Custom settings** in the appeared list | Pop-up with the text `The version shall be scanned for security vulnerabilities, but you can launch it during the grace period (till <date>). Run anyway?` appears |
| 15 | Click **OK** button in the appeared pop-up | Tool launch page appears |

**After**:
- Login as admin and restore the system preferences changed at steps 5 and 6 to their initial values
