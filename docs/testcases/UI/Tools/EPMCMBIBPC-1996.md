# Validation of tool scan results

Test verifies the vulnerabilities report and packages list of a scanned tool version, including package filtering by ecosystem and by search query.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-1995](EPMCMBIBPC-1995.md) case |  |
| 2 | Open the **Tools** page, select the tool from the [EPMCMBIBPC-1995](EPMCMBIBPC-1995.md) case (`shell`) |  |
| 3 | Click **VERSIONS** tab |  |
| 4 | Hover the mouse pointer over the diagram below the scanned version name (**latest**) | Tooltip appears with the labels **Critical**, **High**, **Medium**, **Low**, **Negligible** |
| 5 | Click the scanned version | <li> tool version page appears <li> there are 3 tabs: **VULNERABILITIES REPORT**, **SETTINGS**, **PACKAGES** <li> **VULNERABILITIES REPORT** tab is active <li> the **VULNERABILITIES REPORT** tab contains a table with 2 columns: **Component**, **Severity** |
| 6 | Click the **+** button in front of the `kernel-headers <...>` component | <li> below the component selected at step 6, records with hyperlinks starting with `RHSA-` appear <li> the **Severity** value for each such record is one of: **Critical**, **High**, **Medium**, **Low**, **Negligible** |
| 7 | Click **PACKAGES** tab | Packages list of the **Python.Dist** ecosystem with names and short descriptions appears, e.g. `py <...>`, `pip <...>` packages |
| 8 | Click the **Ecosystem** dropdown list | Dropdown list appears with **Python.Dist**, **System** |
| 9 | Select **System** item in the appeared list | Packages list of the **System** ecosystem with names appears, e.g. `bash <...>`, `bzip2 <...>`, `gcc <...>` packages |
| 10 | Input `krb5` into the **Filter dependencies** search field opposite the **Ecosystem** dropdown list | <li> only 1 found item is displayed in the packages list <li> this item is colored yellow |
| 11 | Input `krb55` into the **Filter dependencies** search field opposite the **Ecosystem** dropdown list | No items are displayed in the packages list |
