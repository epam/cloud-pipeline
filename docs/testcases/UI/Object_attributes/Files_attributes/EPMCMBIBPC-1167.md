# Validation of file preview for files more than 10kb

Test verifies the file preview panel and enlarged view for a file larger than 10KB.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Library** page |  |
| 2 | Open the storage created at step 5 of the [EPMCMBIBPC-1159](EPMCMBIBPC-1159.md) case |  |
| 3 | Click **Upload** |  |
| 4 | In the pop-up that appears, select a text file larger than 10KB, confirm the upload |  |
| 5 | Click on the file uploaded at step 4 | The panel appears on the right side, containing: <li>a text area with the first 10KB of the file content <li>the message "File is too large to be shown. Download file to view full contents" |
| 6 | Click **Enlarge** | A pop-up appears, containing: <li>a header with the file name <li>the file content in the text area <li>the message "File is too large to be shown. Download file to view full contents" <li>the **EDIT** button is not displayed |
| 7 | Click **Close** | The pop-up is closed |
