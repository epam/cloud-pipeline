# [MANUAL] Check folder permissions for read+execute user

Test verifies the folder and its pipelines' UI for a user granted read+execute permissions.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Repeat the [EPMCMBIBPC-549](EPMCMBIBPC-549.md) case, checking the **execute** checkbox in the **allow** column at step 8 (the **read** checkbox will be checked automatically) | <li>The folder edit button is displayed in the top-right corner of the page header <li>For all pipelines in the folder, the **Run** button is in the top-right corner <li>On the **CODE** tab, the edit/launch buttons (rename, delete, new file, upload) are not displayed <li>On the **DOCUMENTS** tab, the buttons delete, rename, upload are not displayed <li>On the **STORAGE RULES** tab, the buttons Delete, Add new rule are not displayed <li>After step 13, a pop-up opens with an **Info** tab containing a non-editable **Name** field, and a **Permissions** tab showing the owner's name |
