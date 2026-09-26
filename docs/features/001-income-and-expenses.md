# Feature 001-income-and-expenses
In this feature I want to implement the backend code (no UI features yet) for the expenses and revenue sources


Think through proper design patterns when implementing these.

Each school year is broken into 3 terms (1st, 2nd, 3rd term), with each term consisting of 3 months (month 1, month 2, month 3).

## Income
The Income sources are from income categories. Within each income categories there are different categories. 

One big one that can be created is students:

Students. The user should be able to create new students. The students have:
Name
Level: Primary, Nursery
Class: [P1, P2 ... P6, P7] for Primary, [Baby, Middle Top] for Nursery 
Status: National or Refugee
Photo: File that can be uploaded to hold a photo of the student

There are expenses for the students. These are things like tuition, boarding fees, medical fees, shaving fees, uniforms, etc. Expenses can be added and if they are there should be an option to charge a specific student a charge.

Students can also be grouped so fees can be imposed upon them from a higher level some groups could be:
- Students
- Boarding Students
- Soccer players

None of these should be already created, but the user should have the ability to create fees and assign them to students to be paid per month or per term.

## Expenses

A user should be able to create expense categories and expenses. 

Examples of expense categories are Staff Salaries, Construction Costs, Instructional Material, Feeding, Administrative Costs

Within each expense categories should be expenses that can be created and have names. For example expenses within the administrative costs expense category could be Airtime bundles, databundles, etc.


A expense should also have a budgeted amount and is charged per month.



## final notes

States should be stored in the database

make the code reusable and structure good
 