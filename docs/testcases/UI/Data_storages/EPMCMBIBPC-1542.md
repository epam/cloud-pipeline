# [MANUAL] Validate of file content editing

Test verifies viewing and editing the content of a text file through the attributes panel.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open storage |  |
| 2 | Create an empty file |  |
| 3 | Click the file | The attributes tab opens, containing: <li> the "go upper hierarchy level" button, file name, **+ Add key**, **File preview**, the two-arrow button, the file content field <li> the **Show attributes** button switches to **Hide attributes** |
| 4 | Click the two-arrow button opposite **File preview** | The editing pop-up appears, containing: file name, **EDIT** button, **CLOSE** button, and the file content |
| 5 | Click **Edit** button in the pop-up | The **SAVE** button appears in place of the **EDIT** button, the file content field becomes editable |
| 6 | Edit the file content |  |
| 7 | Click **Save** button | A confirmation pop-up appears asking `Save changes to <file name>?`, with **Cancel** and **OK** buttons |
| 8 | Click **OK** button in the appeared pop-up | The system returns to the state of step 3, but the file content is changed according to step 6 |
