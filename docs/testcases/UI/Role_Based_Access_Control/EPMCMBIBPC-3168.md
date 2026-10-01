# Generate a list of users

Test verifies exporting the user list to a CSV file.

**Prerequisites**:
- A valid admin user

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the admin user from the prerequisites |  |
| 2 | Click the gear icon in the main menu on the left side of the page to open **Settings** |  |
| 3 | Click the **User management** tab |  |
| 4 | Click the **Export users** button | The file named "export.csv" is automatically downloaded |
| 5 | Open the downloaded file | <li>The table header contains at least the following columns: "id", "userName", "registrationDate UTC", "firstLoginDate UTC", "roles", "groups", "blocked", "defaultStorageId", "defaultStoragePath" <li>In the table, at least 1 row is filled in for the admin user from the prerequisites; it contains the admin username in the "userName" column, the same one used at step 1 |
