# Check bucket navigation when "Show files versions" mode on

Test verifies that page navigation stays correct after bulk-deleting files while **Show files versions** is on.

**Prerequisites**:
- Perform [EPMCMBIBPC-813](EPMCMBIBPC-813.md)
- Uncheck the **Show files versions** checkbox

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Upload more than 50 files to the storage | **Next page** navigation button is enabled |
| 2 | Click **Select page** button |  |
| 3 | Click the appeared **Remove all selected** button |  |
| 4 | Click **OK** button in the appeared pop-up window |  |
| 5 | Repeat steps 2-4 |  |
| 6 | Set the **Show files versions** checkbox | **Next page** navigation button is enabled |
| 7 | Click the appeared **Next page** button | The second page is displayed, the **Previous page** navigation button is enabled |
