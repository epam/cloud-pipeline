# Set pipeline permissions and check it for group

Test verifies that permissions granted to a group on a pipeline apply equally to every member of that group.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-578](EPMCMBIBPC-578.md) case |  |
| 2 | In the list that appears, click the group name specified at step 7 of the EPMCMBIBPC-578 case |  |
| 3 | Set the checkboxes in the **Allow** column opposite all permissions |  |
| 4 | Close the pop-up |  |
| 5 | Log out |  |
| 6 | Login as user1 from the group whose permissions were changed at step 3 |  |
| 7 | Open the **Library** page |  |
| 8 | Click on the pipeline selected at step 3 of the EPMCMBIBPC-578 case |  |
| 9 | Log out |  |
| 10 | Login as user2 from the group whose permissions were changed at step 3 |  |
| 11 | Repeat steps 7-8 | The permissions for user1 and user2 are equal |
