# [MANUAL] Changing the search region resets an ML Block pool's candidates

Test verifies that changing `Search in region` of an `ML Block` hot node pool clears its
candidates, their offerings and any selected offering, and blanks the `Configuration` section's
read-only fields that the offering had filled. The case never clicks `CREATE` - it stops at
observing the reset, short of purchasing anything.

**Prerequisites**:

- admin user able to create hot node pools
- two AWS cloud regions registered in the platform, each with a capacity-block catalogue and at
  least one offering AWS can return for a small node count (e.g. `1`)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the admin user from the prerequisites | |
| 2 | Open the **Cluster state** page | |
| 3 | Click the **HOT NODE POOLS** tab | |
| 4 | Click the "**+ Create**" button | |
| 5 | Click the **Mode** dropdown list and select `ML Block` | |
| 6 | Specify "_Search from_" - a date and time in the future | |
| 7 | Specify `1` for "_Duration (days)_" | |
| 8 | Click the **Search in region** dropdown list and select the first AWS region from the prerequisites (_Region A_) | |
| 9 | Click the "**+ Add instance type**" button | |
| 10 | Select an instance type and a small node count (e.g. `1`) in the new row | After the search settles, an offering alert renders beside the row |
| 11 | Click the offering alert from step 10 | The alert turns green; the **Configuration** section's disabled `Region`, `Instance Type:` and `Nodes count:` fields show _Region A_, the selected instance type and the node count |
| 12 | Click the **Search in region** dropdown list and select the second AWS region from the prerequisites (_Region B_) | The candidate row added at step 9 disappears, together with its offering alert; the **Configuration** section's `Instance Type:` and `Nodes count:` fields go blank again, and its `Region` field now shows _Region B_ |
| 13 | Click the "**+ Add instance type**" button again | A fresh, empty candidate row appears |
| 14 | Open the row's _Instance type_ dropdown | The list now holds _Region B_'s own capacity-block instance types (not _Region A_'s) |
| 15 | Click the **CANCEL** button | The dialog closes without creating a pool |
