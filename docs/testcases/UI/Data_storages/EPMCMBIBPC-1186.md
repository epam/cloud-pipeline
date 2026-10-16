# [MANUAL] "Generate URL" button validation

Test verifies generating a download URL for a single selected file and for several selected files.

**Prerequisites**:
- A bucket with several files exists

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Go to the bucket |  |
| 2 | Check the checkbox opposite one file | A selection-actions button appears above the file list; the **Generate URL** action is available either as that button directly (if it is the only enabled action) or as an entry inside its dropdown (if other actions, such as Download, are also enabled) |
| 3 | Click the **Generate URL** action | The `Download file url` pop-up opens, containing: <li> a single link shown in the text area <li> the **OK** button at the bottom of the pop-up |
| 4 | Close the pop-up | The pop-up closes, the checkbox state is preserved |
| 5 | Check the checkboxes opposite several files |  |
| 6 | Click the **Generate URL** action | The `Download file url` pop-up opens, containing several links in the text area (the number of links equals the number of selected files) |
