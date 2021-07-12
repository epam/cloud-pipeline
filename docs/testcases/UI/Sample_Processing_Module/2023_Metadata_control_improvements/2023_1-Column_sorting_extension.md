#	Column sorting extension

Test verifies
- that the users can combine column sorting with each other in an applied order.

**Prerequisites**:

- Admin user

**Preparations**:

1. Login as admin user from the prerequisites
2. Open the **Library** page
3. Create a new project (`Project1`), open it
4. Click the **Upload metadata** button
5. In OS dialog, navigate to the file contains metadata (_see files in the folder of the test case_)
6. Select the file **`2023_Samples.csv`** and upload it

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin user from the prerequisites | |
| 2 | Open the **Library** page | |
| 3 | Open `Project1` created at step 3 of the preparations | |
| 4 | Click the **Metadata** item | |
| 5 | Click the *Sample [...]* item in the list | Sample metadata table opens |
| 6 | Click on ***R1_Fastq*** column header | <li> Pictogram of decreasing appears near the ***R1_Fastq*** header <li> Table is sorted by ***R1_Fastq*** column in decreasing order |
| 7 | Click on ***Sample Name*** column header | <li> Order number `1` appears near the decrising pictogam for ***R1_Fastq*** header <li> Pictogram of decreasing appears near the ***Sample Name*** header with order number `2` <li> Table is sorted by ***R1_Fastq*** column in decreasing order and by ***Sample Name*** column in decreasing order after that |
| 8 | Click on ***R1_Fastq*** column header again | <li> Pictogram is changed to increasing near the ***R1_Fastq*** header <li> Order numbers `1` and `2` are remains in there places <li> Table is sorted by ***R1_Fastq*** column in increasing order and by ***Sample Name*** column in decreasing order after that |
| 9 | Navigate to table page 2 | Sorting is kept |
| 10 | Click on ***R1_Fastq*** column header again | <li> Pictogram near the ***R1_Fastq*** header disappears <li> Order numbers `2`near the ***Sample Name*** header disappears <li> Table is sorted by ***Sample Name*** column in decreasing order |
| 11 | Navigate to any other entity and return to the Sample metadata table | Sorting isn't kept |
