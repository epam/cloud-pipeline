# Cross button validation if user delete bucket

Test verifies that closing the delete-bucket pop-up with the cross button cancels the deletion.

**Prerequisites**:
- Perform [EPMCMBIBPC-470](EPMCMBIBPC-470.md)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Click **Delete** button |  |
| 2 | Click the cross button in the upper corner of the appeared pop-up window | <li> the pop-up window closes <li> the list in the library-tree on the left panel doesn't change |
