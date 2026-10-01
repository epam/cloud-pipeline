# Check "Use another docker image" drop-down

Test verifies the pop-ups opened from the Use another docker image and Use another compute node checkboxes.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-608](EPMCMBIBPC-608.md) case |  |
| 2 | Set the **Use another docker image** checkbox |  |
| 3 | Click on the appeared field | The **Select docker image** pop-up appears, containing: an address bar in the form `{Registry} > {Tool_Group} > {Tool_name}`, a search field; **Cancel**, **OK** buttons |
| 4 | Click **Cancel** button |  |
| 5 | Unset the **Use another docker image** checkbox |  |
| 6 | Set the **Use another compute node** checkbox |  |
| 7 | Click on the combobox | A dropdown list of available node types appears |
| 8 | Unset the **Use another compute node** checkbox |  |
