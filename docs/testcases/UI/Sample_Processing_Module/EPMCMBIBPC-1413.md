# View metadata

Test verifies viewing SampleSet and Sample metadata tables and their entity-details panes.

**Prerequisites**:
- Perform the [EPMCMBIBPC-1570](EPMCMBIBPC-1570.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Click on the **Metadata** object in the library tree |  |
| 2 | Click on **SampleSet** | <li>The table of sample sets is displayed <li>You can see the SampleSet entity's instances with attributes, as a table with columns: ID, Created Date, Name, Samples |
| 3 | Click on any row in the table | <li>The **Entity details** pane opens <li>The **Entity details** pane displays: Name, Samples |
| 4 | Click the hyperlink in the **Samples** column | <li>The **Entity details** pane opens <li>The panel displays the list of linked sample names |
| 5 | Click **Metadata** in the tree on the left panel |  |
| 6 | Click on **Sample** | <li>The table of samples is displayed <li>The table has columns: ID, Created Date, R1_Fastq, R2_Fastq, SampleName |
