# [MANUAL] Check folder permissions for read+write user

Test verifies the folder and its pipelines' UI for a user granted read+write permissions.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Repeat the [EPMCMBIBPC-549](EPMCMBIBPC-549.md) case, checking the **write** checkbox in the **allow** column at step 8 (the **read** checkbox will be checked automatically) | <li>The folder edit button is displayed in the top-right corner of the page header <li>For all pipelines in the folder, on the **CODE** tab, the buttons **Delete**, **Rename**, **Upload**, **+ NEW FILE**, **+** are displayed <li>On the **DOCUMENTS** tab, the buttons **Delete**, **Rename**, **Upload**, **EDIT** are displayed <li>On the **STORAGE RULES** tab, the buttons **Delete**, **Add new rule** are displayed <li>After step 13, a pop-up opens with an **Info** tab containing an editable **Name** field, and a **Permissions** tab showing the owner's name and the name of the user granted the privileges |
