# [MANUAL] Validation of navigation buttons in NFS mount

Test verifies the **Next page**/**Previous page**/**Select page** navigation buttons behavior on an NFS mount with more folders than fit on one page.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform [EPMCMBIBPC-2276](EPMCMBIBPC-2276.md) 42 times | The folders are sorted by name, the **Next page** button at the bottom right of the screen becomes enabled |
| 2 | Click the now-enabled **next page** button | Two folders are displayed |
| 3 | Click **Select page** button | The checkboxes near the two folders on the page switch to the checked state |
| 4 | Click the now-enabled **previous page** button | 40 folders are displayed, the checkboxes are in the unchecked state |
| 5 | Click the now-enabled **next page** button | Two folders are displayed, the checkboxes are in the checked state |
