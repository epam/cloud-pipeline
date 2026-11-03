# Validation of run unscanned tool

Test verifies that a re-enabled tool with no scanned versions can still be launched with custom settings, subject to a security-scan confirmation.

**Prerequisites**:
- Login as admin

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open **Tools** page |  |
| 2 | Select **Default** registry |  |
| 3 | Select group |  |
| 4 | Select the **shell** tool |  |
| 5 | Click the gear icon in the right upper corner |  |
| 6 | Click **Delete tool** in the appeared list |  |
| 7 | Click **OK** button in the appeared pop-up |  |
| 8 | Click the gear icon in the right upper corner |  |
| 9 | Click **+ Enable tool** in the appeared list |  |
| 10 | Specify the name of the tool removed at steps 6-7 (`shell`) into the **Image** field in the appeared pop-up |  |
| 11 | Click **ENABLE** button |  |
| 12 | Click the appeared **shell** tool | <li> tool page opens, **DESCRIPTION** tab is active <li> label with the text `The latest version shall be scanned for vulnerabilities.` is displayed below the header |
| 13 | Click **VERSIONS** tab | <li> the versions table is empty <li> **VIEW UNSCANNED VERSIONS** button is displayed |
| 14 | Click **v** button near **Run** button |  |
| 15 | Click **Custom settings** in the appeared list | Pop-up window with the text `The version shall be scanned for security vulnerabilities. Run anyway?` appears |
| 16 | Click **OK** button |  |
| 17 | Specify valid launch parameters |  |
| 18 | Click **Launch** button |  |
| 19 | Click **Launch** button in the appeared pop-up window | The tool is being launched |
