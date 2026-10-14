# Try to navigate to another bucket by navigation bar

Test verifies that a user with read-only access to one storage cannot navigate to another storage via the address bar.

**Prerequisites**:
- The user has READ permission only on the specific storage

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the user from the prerequisites |  |
| 2 | Open the **Library** page |  |
| 3 | Click on the storage from the prerequisites |  |
| 4 | Click on the navigation address bar |  |
| 5 | Enter the path to another existing storage |  |
| 6 | Press **Enter** | An error message appears: "You cannot navigate to another storage." |
