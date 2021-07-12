# Samples preselect for a rerun

Test verifies
- that the users can preselect the sample information for a rerun in some cases.

**Prerequisites**:

- Admin user
- Perform [_2023_1_](2023_1-Column_sorting_extension.md) case

**Preparations**:

1. Login as admin user from the prerequisites
2. Open the **Library** page
3. Create a new project (`Project1`), open it
4. Hover over **+Create** button
5. Select *Configuration* option
6. Enter `config1` into the *Name* field and click **Create** button
7. Fill required fields on the Configuration page
8. In the ***Parameters*** section
    - Select `Sample` in the *Root entity type:* field
    - Add parameter `SampleName` with value `this.SampleName`
    - Add parameter `R1_Fastq` with value `this.R1_Fastq`
9. Save Configuration

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin user from the prerequisites | |
| 2 | Open the **Library** page | |
| 3 | Open `Project1` created in [_2023_1_](2023_1-Column_sorting_extension.md) | |
| 4 | Click the **Metadata** item | |
| 5 | Click the *Sample [...]* item in the list | <li> Sample metadata table opens <li> ***Show columns*** button is shown near the Search field |
| 6 | Select any Sample (`Sample1`). Click **Run** button | *Select configuration* modal appears |
| 7 | Select `config1` created at step 6 of Preparations | |
| 8 | Click **OK** button. Confirm running | |
| 9 | At the **Runs** page, click the just-launched run Store its `run_id` | Log run page opens |
| 10 | Expand the ***Parameters*** section | Parameter `SampleName:` with value equals `Sample1` SampleName and Parameter `R1_Fastq:` with value equals `Sample1` R1_Fastq are shown in the ***Parameters*** section |
| 11 | Click the **Stop** hyperlink | |
| 12 | Wait until the **Rerun** hyperlink appears | |
| 13 | Click the **Rerun** hyperlink | *Launch* form opens |
| 14 | Hover over arrow near the **Launch** button | Menu with option *Select metadata entries and launch* appears |
| 15 | Select *Select metadata entries and launch* option | ***Select metadata*** modal appears |
| 16 | Click the **Metadata** item. Click the *Sample* item in the list | Sample metadata table opens |
| 17 | Select Sample not equal to `Sample1` (`Sample2`). Click **OK** button. Confirm launch |
| 18 | Repeat steps 9-10 | Parameter `SampleName:` with value equals `Sample2` SampleName and Parameter `R1_Fastq:` with value equals `Sample2` R1_Fastq are shown in the ***Parameters*** section |
| 19 | Go to the **Runs** page. Switch to the ***Completed Runs tab*** | |
| 20 | Click the **Rerun** hyperlink for run with `run_id` from step 9 | *Launch* form opens |
| 21 | Click **Launch** button. Confirm launch | |
| 22 | Repeat steps 9-10 | Parameter `SampleName:` with value equals `Sample1` SampleName and Parameter `R1_Fastq:` are shown in the ***Parameters*** section |
| 23 | Repeat steps 11-13 | *Launch* form opens <li> arrow isn't shown near the **Launch** button |

***NOTE:*** Stops all runs.