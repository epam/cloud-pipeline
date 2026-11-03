# Validation of create active warning notification

Test verifies creating a Warning-severity notification and seeing it displayed.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-1205](EPMCMBIBPC-1205.md) case |  |
| 2 | Click **+ ADD** |  |
| 3 | Enter a value into the **Title** field |  |
| 4 | Enter a value into the **Body** field |  |
| 5 | Click the **Severity** checkbox |  |
| 6 | Select **Warning** in the list that appears |  |
| 7 | Set the **Active** checkbox |  |
| 8 | Click **CREATE** |  |
| 9 | Click **Refresh** |  |
| 10 | Wait until a notification appears | The notification appears: <li>the severity is "Warning" <li>the notification title is equal to the value entered at step 3 <li>the notification body is equal to the value entered at step 4 |

**After**:
- Click the close icon on the notification
