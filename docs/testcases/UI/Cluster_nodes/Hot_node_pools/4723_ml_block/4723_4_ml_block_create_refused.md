# [MANUAL] [Negative] ML Block pool cannot be created without a chosen offering

Test verifies that `CREATE` stays refused for a new `ML Block` hot node pool until an offering is
selected, that leaving `Search until` empty does not block it, and that losing the selected
offering to a re-search refuses `CREATE` again. Clicking the (disabled) `CREATE` button is the one
place this case touches it, to confirm the refusal; no step ever lets the click through, so no
capacity block is ever purchased.

**Prerequisites**:

- admin user able to create hot node pools
- an AWS cloud region registered in the platform with a capacity-block catalogue and an offering
  AWS can return for a small node count (e.g. `1`)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the admin user from the prerequisites | |
| 2 | Open the **Cluster state** page | |
| 3 | Click the **HOT NODE POOLS** tab | |
| 4 | Click the "**+ Create**" button | |
| 5 | Click the **Mode** dropdown list and select `ML Block` | |
| 6 | Specify "_Search from_" - a date and time in the future | |
| 7 | Leave "_Search until_" empty | No validation error appears for "Search until" - the field is optional in `ML Block` mode |
| 8 | Specify `1` for "_Duration (days)_" | |
| 9 | Click the **Search in region** dropdown list and select the AWS region from the prerequisites | |
| 10 | Click the "**+ Add instance type**" button, then select an instance type and the small node count from the prerequisites | The row is complete, but no offering has been selected yet |
| 11 | Observe the **CREATE** button | It is disabled (greyed out), even though the window and the candidate row are both valid |
| 12 | Click the **CREATE** button | Nothing happens: no pool is created and the dialog stays open |
| 13 | Wait for the search to settle and click the offering alert that appears beside the row | The alert turns green; the **CREATE** button becomes enabled |
| 14 | Without clicking **CREATE**, change the row's _Nodes count_ to a different value | As soon as the new search is scheduled (before it even settles), the alert loses its green highlight, the **Configuration** section's `Instance Type:` and `Nodes count:` fields go blank, and the **CREATE** button becomes disabled again |
| 15 | Click the **CREATE** button | Nothing happens: no pool is created and the dialog stays open, confirming a selection dropped by a re-search cannot be used to create the pool |
| 16 | Click the **CANCEL** button | The dialog closes without creating a pool |
