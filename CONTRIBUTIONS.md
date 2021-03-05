# Cloud Pipeline development contribution processes

Let's take a look to the contribution process of the **`Cloud Pipeline`** development.

All customer/inner tasks are being presented as separate entities. Each such entity shall have its own **_properties_** - type, description, assignee(s), status(es)/label(s)/mark(s) etc. and **_stages_** - formulation, implementation, testing etc.

## Task entity properties

Any task entity shall be described in details as separate issue. If the task has its subtasks - they can be described in the "parent" issue or as separate issues but linked with their "parent".

General task entity properties:

1. _Type_. Defines the "background" of the task. There are two main task types:
    - **`enhancement`** - for description of new features that shall be added to the Cloud Pipeline functionality
    - **`bug`** - for description bugs/errors found during the Cloud Pipeline usage
2. _Description_. Contains the task description, requirements to implement, technical details, additional data
3. _Assignee(s)_. Defines member(s) of the development team who should implement the functionality described in the task
4. _Label(s)_. Optional properties that can define additional different task attributes - priority/project/state etc. These labels are optional, but could be convenient - e.g., for searching or sorting the tasks

## Life cycle stages of the task entity

Any task should go through the following stages:

- _formulation_
- _development_
- _verification_
- _documenting_
- _closing_

For any task, the flowing of its stages should be reflected - via labels or comments from the developer team members.

### Task formulation

This process is fundamental, from which the task development begins.  
It includes the preparation and writing of the task description and setting of task properties.

_Enhancement_ issue description shall contain, at least:

- title
- clear and detailed description of the task/problem/feature that shall be implemented
- (_if necessary_) technical details of the implementation approach for the development team
- (_if necessary_) images or other documents to clearify the task

_Bug_ issue description shall contain, at least:

- title
- clear and detailed description of what works incorrect and should be fixed
- (_if necessary_) images or other documents to clearify the problem

For any task, should be set the _assignee(s)_ - to define specific member(s) of the development team who will perform the development of that task.

Additionally for the task, labels can be set - for specifying the general version of the **`Cloud Pipeline`**, priority of the task, area of the Platform to which the task belongs, etc.

### Development

After the task creation (formulation), it shall be assigned to one or more members of the development team.  
Then assignee(s) can begin the task implementation. If desired, assignee sets the label about the beginning of the task implementation - **`state/underway`**.  
After the task implementation, assignee should set the corresponding label (**`state/verify`**) and/or leave the comment (_with a link to the code_) which mean that task implementation is finished and the next stages can be started.

### Verification

After the task implementation is done, it shall be verified.  
For that:

- basic functionality is being verified manually (_smoke testing_) with the mandatory comment(s) about results into the task entity
- after that, the testing scenario shall be prepared (_test case(s)_). After the scenario is prepared, scenario author sets the label **`state/has-case`** and leave the comment about that (_with a link to the case(s)_) into the task entity
- if the testing scenario can't be automated - manually testing shall be performed
- if the testing scenario can be automated:
    - automatic tests code shall be developed
    - after automatic tests are prepared, the tests developer sets the label **`state/has-e2e`** and leave the comment about that (_with a link to the test code_) into the task entity
    - automatic tests shall be performed and passed at least once
- info about test results shall be specified into the task entity as comment (_with a link to the results_)

If at any verification step errors are found in the implemented task functionality, the team member leaves the corresponding comment into the task entity. After that the development stage start again - to fix found errors/bugs.  
Then the verification repeats on the corrected task functionality.

### Documenting

After, the documentation on the new functionality shall be prepared.  
At least short description of the functionality shall be added into the version Release Notes.  
If necessary, the full detailed description is being added into the User manual.

After the documentation is prepared, the documents author sets the label **`state/has-doc`** and leave the comment about that (_with a link to the documents_) into the task entity.

### Closing

After the task code is implemented, the implementation of the new functionality/bug fixes is verified, documents are prepared, all necessary comments and labels are set, the task can be closed and considered fully implemented.  
The task author shall set the label **`state/ready`** into the task entity.

***

## Example. Current approach

For the described approach to the contribution process of the **`Cloud Pipeline`** development, `GitHub` abilities are currently used.

In this case, the task entity is `GitHub` issue.

### Stage 1. Task creation

When somebody wants to create a new task, he/she clicks the "**New issue**" button at the [Issues](https://github.com/epam/cloud-pipeline/issues) page. The page with the selection of possible issue types described above appears:  
    ![CloudPipelineContributions](docs/md/attachments/CONTRIBUTIONS/Contributions_01.png)  
After the issue type is selected ("**Get started**" button), the template of the corresponding issue type (with hints that simple the task creation) appears, e.g. the template for the `bug` type:  
    ![CloudPipelineContributions](docs/md/attachments/CONTRIBUTIONS/Contributions_02.png)  
**_Note_**: if none of the proposed template are suitable for the task, author can create a blank issue.  
Then, the issue author:

- fills in all necessary fields (title and detailed description)
- (_optionally_) sets labels for simpler search/sorting issues. By default, to `bug` and `enhancement` issues the corresponding labels are being set automatically (![CloudPipelineContributions](docs/md/attachments/CONTRIBUTIONS/Contributions_03.png) and ![CloudPipelineContributions](docs/md/attachments/CONTRIBUTIONS/Contributions_04.png)). Author can remove them or add another ones.
- (_optionally_) assigns the implementation of the task to a specific member of the development team
- confirms the issue creation - clicks the "**Submit new issue**" button

**_Note_**: all optional actions can be also performed in any moment after the issue creation

### Stage 2. Develop functionality

After the task creation, it shall be assigned to one or more members of the development team (if it wasn't done on the previous step).  
This action is being displayed at the issue card - at the "**Assignees**" section and in the issue card, e.g.:  
    ![CloudPipelineContributions](docs/md/attachments/CONTRIBUTIONS/Contributions_05.png)  
    ![CloudPipelineContributions](docs/md/attachments/CONTRIBUTIONS/Contributions_06.png)

Then assignee(s) can begin the task implementation:

- the developer changes the state of the issue by setting the label ![CloudPipelineContributions](docs/md/attachments/CONTRIBUTIONS/Contributions_07.png)
- after the new functionality or its part (or bug fixes) is implemented, the developer creates a pull request with the code changes to the main **`Cloud Pipeline`** project with a link to the current issue.  
It is also displayed at the issue card, e.g.:  
    ![CloudPipelineContributions](docs/md/attachments/CONTRIBUTIONS/Contributions_08.png)
- then, the pull request is being merged to the base branch (`develop`). It is also displayed at the issue card, e.g.:  
    ![CloudPipelineContributions](docs/md/attachments/CONTRIBUTIONS/Contributions_09.png)
- the developer changes the state of the issue by setting the label ![CloudPipelineContributions](docs/md/attachments/CONTRIBUTIONS/Contributions_10.png), other "state" labels are being removed
- the developer adds to the issue page a comment about requiring task implementation verification.

### Stage 3. Verification

After that, the verification stage starts:

**Smoke testing**  
Smoke testing is being performed to fast check and confirm that the customer task has implemented as required, in general.  
It is being performed manually. After the smoke testing, tester leaves a corresponding comment to the issue - with results or notes/errors.

**Test cases**  
Test case(s) are being created for futher tests preparation, which shall check and confirm that the customer task has fully implemented as required.  
In the **`Cloud Pipeline`** development process, test case(s) for each customer task shall be created as a separate issue. Such issue is being created from a blank template with the following features:

- issue title and description shall have links to the original task
- issue description shall contain steps to verify implemented functionality in details
- if the current test can't be automated, the issue description shall contain a specific note about it
- (_optionally_) for such issue, the special label can be set - ![CloudPipelineContributions](docs/md/attachments/CONTRIBUTIONS/Contributions_11.png)
- (_optionally_) for such issue, the Project "**E2E Tests Automation**" can be set:  
    ![CloudPipelineContributions](docs/md/attachments/CONTRIBUTIONS/Contributions_12.png)
- after the test case is prepared, the label shall be set to the original task issue - ![CloudPipelineContributions](docs/md/attachments/CONTRIBUTIONS/Contributions_13.png)

**Tests development**  
After the test case creation, it shall be assigned to one or more testers from the development team.  
This action is being displayed at the issue card - at the "**Assignees**" section and in the issue card.

If the test case can't be automated, its testing is being performed manually. Manual testing results are reflected in the test case issue comments. If wrong implemented functionality is being encountered during the testing, tester leaves comment(s) with the error(s) description to the original task issue and waits for their fixes.

Else if the test case can be automated, the assigned tester(s) starts the implementation of the test code:

- (_optionally_) the tester changes the state of the test case issue by setting the label ![CloudPipelineContributions](docs/md/attachments/CONTRIBUTIONS/Contributions_07.png)
- after the test functionality is implemented, the tester launches the new test from the own branch and checks results
- if the test is passed according to the current test case, tester creates a pull request with the code changes to the main Cloud Pipeline project with a link to the current test case issue
- if the test isn't passed according to wrong implemented functionality, tester leaves comment(s) with the error(s) description to the original task issue and waits for their fixes
- when all fixes are done and conflicts are resolved, the pull request is being merged to the base branch (`develop`). So, the new test is included for the tests running on a daily basis
- test case issue is being closed after that
- after the test is prepared, the label shall be set to the original task issue - ![CloudPipelineContributions](docs/md/attachments/CONTRIBUTIONS/Contributions_14.png)

**Tests results**  
Regardless of testing type (manually/automated), after all fixes done and the test is being passed, tester adds link to the test results as a comment to the test case issue.  
Full test results are also being uploaded into the `GitHub`, in a separate branch of the **`Cloud Pipeline`** project.

### Stage 4. Documenting

After, the documentation on the new functionality shall be prepared:

- documentation is being performed as a separate pull request
- link to the prepared documentation pull request is being added to the original task issue as a comment
- after the documents are prepared, the label shall be set to the original task issue - ![CloudPipelineContributions](docs/md/attachments/CONTRIBUTIONS/Contributions_15.png)

### Stage 5. Go-live

After all, the original task issue has labels ![CloudPipelineContributions](docs/md/attachments/CONTRIBUTIONS/Contributions_10.png), ![CloudPipelineContributions](docs/md/attachments/CONTRIBUTIONS/Contributions_13.png), ![CloudPipelineContributions](docs/md/attachments/CONTRIBUTIONS/Contributions_14.png), ![CloudPipelineContributions](docs/md/attachments/CONTRIBUTIONS/Contributions_15.png). Also in issue comments, there is a link to the results of tests that checks the required functionality.  
After that, the task author set the label ![CloudPipelineContributions](docs/md/attachments/CONTRIBUTIONS/Contributions_16.png) to the issue and can close it.

***

In general, the whole contribution procedure looks like:

1. Task creation: new issue (title, description, assignees)
2. Task implementation: state labels, pull request(s), comment "_to verify_"
3. Manual testing of the implementation of the base task functionality: comment "_verified_" or errors description and return to step 2
4. Test case(s) creation: new issue (title, description, link to original task, labels)
5. Tests implementation:
    - _manually_ (for non-automated test cases) - testing, comment "_passed_" to the test case issue or errors description to the task issue and return to step 2
    - _automated_ - state labels, pull request(s), comment "_passed_" to the test case issue or errors description to the task issue and return to step 2
6. Test results: test case and test issues are closed, automated test results are being uploaded to the Cloud Pipeline repo, comment "_tests are passed_" to the original task
7. Documentation writing: pull request(s), comment "_docs were updated_" to the original task
8. Task is closed
