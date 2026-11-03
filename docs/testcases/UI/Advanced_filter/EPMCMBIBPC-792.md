# [MANUAL] Validation of "SAVE" button

Test verifies saving a search request and re-running it from the saved requests list.

**Prerequisites**:
- Several pipelines launched by different users, with different disk sizes, launched on different instance types, completed with different statuses

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform steps 1-2 of the [EPMCMBIBPC-791](EPMCMBIBPC-791.md) case |  |
| 2 | Click **SAVE** button |  |
| 3 | Enter a name for the saved request |  |
| 4 | Go to the **Pipeline library** page |  |
| 5 | Perform the [EPMCMBIBPC-727](EPMCMBIBPC-727.md) case |  |
| 6 | Click the down-arrow button near the search input field |  |
| 7 | Click the label with the text entered at step 3 | The request from the [EPMCMBIBPC-791](EPMCMBIBPC-791.md) case is executed, the results match |
