# Validation of active notification

Test verifies that an active notification is shown in the upper-right corner and can be closed.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-1206](EPMCMBIBPC-1206.md) case |  |
| 2 | Click the system-settings button (gear icon in the left panel) |  |
| 3 | Click the **System events** tab |  |
| 4 | Set the **Active** checkbox opposite the notification added at step 1 |  |
| 5 | Click **Refresh** |  |
| 6 | Wait until a notification appears | In the upper-right corner, a notification appears containing: <li>the **Title** value entered at step 3 of the [EPMCMBIBPC-1206](EPMCMBIBPC-1206.md) case <li>the **Body** value entered at step 4 of the [EPMCMBIBPC-1206](EPMCMBIBPC-1206.md) case <li>a severity icon <li>a close icon <li>the date and time of the notification's creation |
| 7 | Click the close icon on the notification | The notification that appeared at step 6 disappears |
