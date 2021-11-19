# Run pipeline on "on_demand" instance

**Actions**:

1. Check the System Preference `cluster.spot` is enabled (set if it's not)
2. Launch a pipeline with forcible setting of the `on_demand` price type
3. Wait once a new Cloud instance is booted
4. Check the instance type of the instance from step 3
5. Wait till the pipeline is completed

***

**Expected result**:

After step 4, instance has `on_demand` price type

After step 5:

- the pipeline launched at step 2 is completed with the status `SUCCESS`
- there are no nodes in the cluster without labels
