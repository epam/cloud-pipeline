# Check of default command adding

Test verifies that a default **Cmd template** saved on a tool is persisted and displayed after reopening the tool.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open **Tools** page |  |
| 2 | Select **Default** registry |  |
| 3 | Select a group |  |
| 4 | Click a tool |  |
| 5 | Click **SETTINGS** tab |  |
| 6 | Expand **EXECUTION ENVIRONMENT** section if it's minimized |  |
| 7 | Input a valid value into the **Cmd template** field, e.g. `echo "Hi, I'm nginx!"` | The **SAVE** button becomes enabled |
| 8 | Click **SAVE** button | The **SAVE** button becomes disabled |
| 9 | Click the **return** button in the left upper corner |  |
| 10 | Click the tool selected at step 4 |  |
| 11 | Repeat steps 5-6 | The value entered at step 7 is displayed in the **Cmd template** field |
