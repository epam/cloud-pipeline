# [MANUAL] "Generate URL" button validation

Test verifies generating a download URL for a single selected file and for several selected files.

**Prerequisites**:
- A bucket with several files exists

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Go to the bucket | |
| 2 | Check the checkbox opposite one file | ***1 item selected*** button appears above the file list |
| 3 | Click ***1 item selected*** button | The **Generate URL** action is available as an entry inside its dropdown |
| 4 | Click the **Generate URL** action | The `Download file url` pop-up opens, containing: <li> a single link shown in the text area <li> the **OK** button at the bottom of the pop-up |
| 5 | Close the pop-up | The pop-up closes, the checkbox state is preserved |
| 6 | Check the checkboxes opposite several files | |
| 7 | Click the **Generate URL** action | The `Download file url` pop-up opens, containing several links in the text area (the number of links equals the number of selected files) |
