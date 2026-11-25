# [MANUAL] ML Block pool offering search and selection

Test verifies that the `ML Block` mode of a hot node pool lets the user search AWS for a capacity
block offering and select one. The case stops once the offering is selected and the read-only
`Configuration` fields are filled - it never clicks `CREATE`, because that would file a purchase of
a non-refundable AWS Capacity Block.

**Prerequisites**:

- admin user able to create hot node pools
- an AWS cloud region registered in the platform that currently sells at least one instance type as
  an AWS Capacity Block for ML, with AWS able to return an offering for a small node count (e.g. `1`)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the admin user from the prerequisites | |
| 2 | Open the **Cluster state** page | |
| 3 | Click the **HOT NODE POOLS** tab | |
| 4 | Click the "**+ Create**" button | The "Create hot node pool" dialog opens |
| 5 | Click the **Mode** dropdown list and select `ML Block` | The section below "Pool type" is renamed `ML Block`; the `Autoscaled` checkbox and the weekly schedule rows are gone, replaced with `Search from:`, `Search until:` and `Duration (days):` |
| 6 | Specify "_Search from_" - a date and time in the future (e.g. tomorrow) | |
| 7 | Specify `0` for "_Duration (days)_" | An error that the duration should be at least `1` day is displayed |
| 8 | Specify `1` for "_Duration (days)_" | The error from step 7 disappears - `1` day is accepted |
| 9 | Click the **Search in region** dropdown list and select the AWS region from the prerequisites | An "_Instance types:_" label with an "**+ Add instance type**" button appears below "Duration (days)" |
| 10 | Click the "**+ Add instance type**" button | A candidate row appears, with an _Instance type_ dropdown and a _Nodes count_ field |
| 11 | Select an instance type from the prerequisites in the row's _Instance type_ dropdown | |
| 12 | Specify the small node count from the prerequisites (e.g. `1`) in the row's _Nodes count_ field | Shortly after, `Search for offerings...` with a spinner appears on the "Instance types:" line; once it disappears, a blue offering alert renders beside the row, showing the availability zone, the instance type, the offering's time window and its up-front price (`$`-prefixed) |
| 13 | Click the offering alert from step 12 | The alert turns green (its icon becomes visible); in the **Configuration** section, the disabled `Region` field now shows the region from step 9, the disabled `Instance Type:` field shows the instance type from step 11, and the disabled `Nodes count:` field shows the node count from step 12 |
| 14 | Click the **CANCEL** button | The dialog closes without creating a pool - this case never clicks `CREATE`, since that step would purchase the capacity block |
