# [MANUAL] "Generate URL" button validation

Test verifies generating a download URL for a single selected file and for several selected files on an NFS mount.

**Prerequisites**:
- Perform [EPMCMBIBPC-2290](EPMCMBIBPC-2290.md)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Go to the NFS mount |  |
| 2 | Check the checkbox opposite one file | The **Generate URL** button appears above the file list |
| 3 | Click the appeared **Generate URL** button | The `Download file url` pop-up opens: <li> the text area shows a single link <li> the **OK** button is displayed at the bottom of the pop-up |
| 4 | Close the pop-up | The pop-up closes, the checkbox state is preserved |
| 5 | Check the checkboxes opposite several files |  |
| 6 | Click **Generate URL** button | The `Download file url` pop-up opens: the text area shows several links (the number of links equals the number of selected files) |
