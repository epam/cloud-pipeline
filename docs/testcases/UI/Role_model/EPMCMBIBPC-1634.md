# [MANUAL] Check tool permissions by user with read-only permissions for registry

Test verifies the tool UI for a user with read-only permissions inherited from the registry.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Complete the [EPMCMBIBPC-1547](EPMCMBIBPC-1547.md) case |  |
| 2 | Select the registry with edited permissions |  |
| 3 | Select the tool group in that registry |  |
| 4 | Select the tool |  |
| 5 | Open the **DESCRIPTIONS** tab | The buttons **EDIT**, **Run**, and settings (gear icon in the upper-right corner) are absent |
| 6 | Open the **VERSIONS** tab | The buttons **Run**, delete, and settings are absent |
| 7 | Open the **SETTINGS** tab | <li>The **Run** button is absent <li>The input fields cannot be filled in |
