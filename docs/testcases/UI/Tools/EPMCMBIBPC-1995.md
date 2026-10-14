# Validation of tool scanning

Test verifies scanning an unscanned tool version: the unscanned-versions list, the scan progress and the resulting vulnerabilities report state.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-1994](EPMCMBIBPC-1994.md) case |  |
| 2 | Open the **Tools** page |  |
| 3 | Select the tool from the [EPMCMBIBPC-1994](EPMCMBIBPC-1994.md) case (`shell`) |  |
| 4 | Click **VERSIONS** tab |  |
| 5 | Click **VIEW UNSCANNED VERSIONS** button | <li> list of the unscanned tool versions with labels `Version was not scanned` appears <li> **VIEW UNSCANNED VERSIONS** button is invisible <li> **HIDE UNSCANNED VERSIONS** button is displayed <li> **Run** buttons opposite the version names have the exclamation-mark icon |
| 6 | Click the version with the label **latest** in the appeared versions list | <li> tool version page appears <li> there are 3 tabs: **VULNERABILITIES REPORT**, **SETTINGS**, **PACKAGES** <li> **SETTINGS** tab is active |
| 7 | Click **VULNERABILITIES REPORT** tab | <li> empty table with columns **Component**, **Severity** appears <li> the table contains the text `No vulnerabilities found` |
| 8 | Click **PACKAGES** tab | Empty **Ecosystem** dropdown list appears |
| 9 | Click the arrow icon in front of the version header |  |
| 10 | Click **VIEW UNSCANNED VERSIONS** button |  |
| 11 | Click **SCAN** button opposite the version with the label **latest** in the appeared versions list | The name of the **SCAN** button changes to **SCANNING**, a throbber near the button name is displayed |
| 12 | Wait until scanning finishes | <li> scanning results appear below the tool version name (text result, e.g. `Successfully scanned at <current date & time>`, and a graphical result in a diagram view) <li> **Run** button opposite the **latest** version has no exclamation-mark icon |
| 13 | Click the **v** button near the **Run** button |  |
| 14 | Click **Custom settings** in the appeared list | **Launch** page opens |
