# Check bucket navigation when "Show file versions" mode on

Test verifies that page navigation stays correct after bulk-deleting files while **Show file versions** is on.

**Prerequisites**:
- Perform [EPMCMBIBPC-813](EPMCMBIBPC-813.md)
- Click the **Grid menu** in the right upper corner and unset the **Show file versions** item

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Upload more than 50 files to the storage | **Next page** navigation button is enabled |
| 2 | Click **Select page** button |  |
| 3 | Click the appeared selection-actions button (**N item(s) selected**) and select the **Remove** item |  |
| 4 | Click **OK** button in the appeared pop-up window |  |
| 5 | Repeat steps 2-4 |  |
| 6 | Click the **Grid menu** in the right upper corner and set the **Show file versions** item | **Next page** navigation button is enabled |
| 7 | Click the appeared **Next page** button | The second page is displayed, the **Previous page** navigation button is enabled |
