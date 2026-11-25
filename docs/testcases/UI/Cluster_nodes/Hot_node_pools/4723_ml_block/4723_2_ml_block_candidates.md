# [MANUAL] Instance type candidates for an ML Block pool

Test verifies adding and removing instance-type candidate rows of an `ML Block` hot node pool,
that a type already held by one row is not offered to another, that a candidate AWS has no
capacity for shows a `No offerings` warning, and that the candidate dropdown always lists the
region's capacity-block instance types in name order. The case never clicks `CREATE` - it stops at
reading the candidates and their offerings, short of purchasing anything.

**Prerequisites**:

- admin user able to create hot node pools
- an AWS cloud region registered in the platform whose capacity-block catalogue has at least two
  different instance types

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the admin user from the prerequisites | |
| 2 | Open the **Cluster state** page | |
| 3 | Click the **HOT NODE POOLS** tab | |
| 4 | Click the "**+ Create**" button | |
| 5 | Click the **Mode** dropdown list and select `ML Block` | |
| 6 | Specify "_Search from_" - a date and time in the future | |
| 7 | Specify `1` for "_Duration (days)_" | |
| 8 | Click the **Search in region** dropdown list and select the AWS region from the prerequisites | |
| 9 | Click the "**+ Add instance type**" button twice | Two candidate rows appear, each with its own _Instance type_ dropdown and _Nodes count_ field |
| 10 | Open the _Instance type_ dropdown of the first row | The list holds only the region's capacity-block instance types, and only those, sorted alphabetically by name |
| 11 | Select an instance type (_Type A_) in the first row | |
| 12 | Open the _Instance type_ dropdown of the second row | _Type A_ is absent from the list; every other capacity-block instance type of the region is present, still sorted by name |
| 13 | Select a different instance type (_Type B_) in the second row | |
| 14 | In the first row's _Nodes count_ field, specify an unreasonably large count (e.g. `999999`) | |
| 15 | In the second row's _Nodes count_ field, specify a small count (e.g. `1`) | |
| 16 | Wait for the search indicator (`Search for offerings...`) next to "Instance types:" to disappear | The first row shows a `No offerings` warning alert (AWS has no block that large); the second row shows either an offering alert or, if AWS currently has none for _Type B_ either, its own `No offerings` warning |
| 17 | Click the remove (trash) icon on the second row | The second row disappears; the first row and its `No offerings` warning stay as they were |
| 18 | Click the "**+ Add instance type**" button again | A new, empty candidate row appears |
| 19 | Open the new row's _Instance type_ dropdown | _Type B_ is available again (no other row holds it any more), and the list is still sorted by name |
| 20 | Click the **CANCEL** button | The dialog closes without creating a pool |
