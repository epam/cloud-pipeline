# [MANUAL] Advanced_filtering by parameter.{parameter_name}

Test verifies filtering the advanced search results by a specific launch parameter's value.

**Prerequisites**:
- Several completed pipelines with different parameters

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-727](EPMCMBIBPC-727.md) case |  |
| 2 | Enter `parameter.{parameter_name}="{parameter_value}"` into the search field and press **Enter** (e.g. `parameter.p1="value1"`) |  |
| 3 | Click on a pipeline |  |
| 4 | Click the **Parameters** section | For all found pipelines, the **Parameters** section shows the `p1` field value specified at step 2 (`value1`) |
| 5 | Return to the search page |  |
| 6 | Enter `parameter.output_dir="s3://cmbi-analysis/proteins/rosetta-vaccine/${RUN_ID}"` into the search field and press **Enter** |  |
| 7 | Click on a pipeline |  |
| 8 | Click the **Parameters** section | For all found pipelines, the **Parameters** section shows the `output_dir` field value specified at step 6 (`s3://cmbi-analysis/proteins/rosetta-vaccine/${RUN_ID}`) |
