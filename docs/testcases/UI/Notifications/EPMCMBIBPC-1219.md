# Validation of edit active notifications

Test verifies that editing an active notification's title/body is reflected when it is shown.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-1213](EPMCMBIBPC-1213.md) case |  |
| 2 | Click the system-settings button (gear icon in the left panel) |  |
| 3 | Click the **System events** tab |  |
| 4 | Click the edit icon opposite any notification |  |
| 5 | Change the **Title** and **Body** values |  |
| 6 | Click **SAVE** |  |
| 7 | Click **Refresh** |  |
| 8 | Wait until a notification appears | A notification appears, with the title and body equal to the values entered at step 5 |

**After**:
- Click the close icon on the notification
