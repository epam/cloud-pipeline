# [MANUAL] Multiuser issues

Test verifies that a second, permitted non-admin user can view an issue and its comments, add their own comment, and create a new issue.

**Preparations**:
1. Perform the [EPMCMBIBPC-2697](EPMCMBIBPC-2697.md) case
2. Open the **Library** page, navigate to the folder created at step 3 of the [EPMCMBIBPC-2691](EPMCMBIBPC-2691.md) case
3. Hover over the gear icon in the upper-right corner -> click **Edit folder** in the list that appears
4. Click the **Permissions** tab
5. Click **Add group**
6. In the pop-up that appears, enter `ROLE_USER`, click **OK**
7. In the **Groups and users** table, click on `ROLE_USER`
8. Set the **Allow** checkboxes for the **Read**, **Write** and **Execute** permissions
9. Close the pop-up

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as a non-admin user, different from the one used in the EPMCMBIBPC-2697 case |  |
| 2 | Open the **Library** page, navigate to the folder created at step 3 of the EPMCMBIBPC-2691 case |  |
| 3 | Click the button in front of the gear icon in the upper-right corner |  |
| 4 | Click **Issues** in the list that appears | The **Issues** panel appears, containing a record for the issue created in the EPMCMBIBPC-2691 case, with: <li>the issue title equal to the one specified at step 8 of the EPMCMBIBPC-2691 case <li>the text "Opened {some time} ago by {USER_NAME}", where {USER_NAME} is the name of the user who created the issue at the EPMCMBIBPC-2691 case |
| 5 | Click on the issue created at the EPMCMBIBPC-2691 case | In the **Issues** panel: <li>the issue title equal to the one specified at step 8 of the EPMCMBIBPC-2691 case <li>the **Return** button <li>an issue description section containing: a header with the text "{USER_NAME} commented {some time} ago", where {USER_NAME} is the name of the user who created the issue at the EPMCMBIBPC-2691 case; a description equal to the one specified at step 9 of the EPMCMBIBPC-2691 case <li>a comment section containing: a header with the text "{USER_NAME} commented {some time} ago", where {USER_NAME} is the name of the user who created the comment at the EPMCMBIBPC-2697 case; a comment message equal to the one specified at step 3 of the EPMCMBIBPC-2697 case; the **Write**, **Preview** tabs; the **Send** button (disabled) |
| 6 | Enter some text into the **Comment** field on the **Write** tab |  |
| 7 | Click **Send** | Below the existing issue comment in the **Issues** panel, a new comment section appears, containing: <li>a header with the text "You commented {some time} ago" <li>the **Edit**, **Delete** buttons <li>a comment message equal to the one specified at step 6 |
| 8 | Click **Return** |  |
| 9 | Click **New issue** |  |
| 10 | Specify a valid issue title |  |
| 11 | Enter some text into the description field |  |
| 12 | Click **Create** | The **Issues** panel shows 2 issue records: <li>for the issue created at the EPMCMBIBPC-2691 case, containing the title specified at step 8 of the EPMCMBIBPC-2691 case, and the text "Opened {some time} ago by {USER_NAME}", where {USER_NAME} is the name of the user who created the issue at the EPMCMBIBPC-2691 case <li>for the just-created issue, containing the title specified at step 10, and the text "Opened {some time} ago by {USER_NAME}", where {USER_NAME} is the name of the user from step 1 |
