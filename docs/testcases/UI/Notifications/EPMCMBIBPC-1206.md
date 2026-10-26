# Validation of create inactive info notification

Test verifies creating a notification with Info severity, left inactive.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-1205](EPMCMBIBPC-1205.md) case |  |
| 2 | Click **+ ADD** | A pop-up appears, containing: <li>the **Title**, **Body** fields <li>the **Severity** combobox, with the "info" item selected by default <li>the **State** checkbox labeled **Active** <li>the **CANCEL**, **CREATE** buttons |
| 3 | Enter a value into the **Title** field |  |
| 4 | Enter a value into the **Body** field |  |
| 5 | Click the **Severity** checkbox | A drop-down list appears, containing the **Info**, **Warning**, **Critical** items |
| 6 | Select **Info** in the list that appears |  |
| 7 | Click **CREATE** | A record appears in the table, containing: <li>a **+** button <li>a severity icon <li>the **Title** value added at step 3 <li>the current date and time <li>an **Active** checkbox (unchecked) <li>edit and remove buttons |
| 8 | Click **OK** |  |
