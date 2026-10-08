# Remove the user

Test verifies that a deleted user no longer appears in the User management search results.

**Preparations**:
1. Perform the [EPMCMBIBPC-3019](EPMCMBIBPC-3019.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the admin user |  |
| 2 | Click the gear icon in the main menu on the left side of the page to open **Settings** |  |
| 3 | Click the **User management** tab, click the **Users** subtab |  |
| 4 | Find the user with the name specified at step 5 of the [EPMCMBIBPC-3019](EPMCMBIBPC-3019.md) case and click the edit button next to the username |  |
| 5 | In the pop-up that appears, click **DELETE** |  |
| 6 | Click **OK** to confirm the removal |  |
| 7 | Clear the search field |  |
| 8 | Repeat steps 2-3 |  |
| 9 | Enter into the search field the user name specified at step 5 of the [EPMCMBIBPC-3019](EPMCMBIBPC-3019.md) case, press **Enter** | The search results are empty, the "No data" message appears |
