# [MANUAL] Validation of saved request removal

Test verifies deleting a saved search request, with a cancel-then-confirm flow.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | After performing the [EPMCMBIBPC-792](EPMCMBIBPC-792.md) case |  |
| 2 | Perform the [EPMCMBIBPC-727](EPMCMBIBPC-727.md) case |  |
| 3 | Click the down-arrow button near the search input field |  |
| 4 | Click the delete icon opposite the saved request |  |
| 5 | Click **NO** in the appeared dialog window |  |
| 6 | Repeat steps 3-4 |  |
| 7 | Click **YES** in the appeared dialog window | The saved request is deleted |
