# Attempt 2 at good UI Screen

The point of this UI is as an accounting feature. It should easily show and track expenses and incomes accross each term. 



Here is how I want the structure:

## 1 - Opening screen
When you open the application it should show all the school years. The user can create a new school year (similar to how was implemented in feature/002-setup-screens)
Have an option that asks the users if they want to autopopulate with last years expenses and income types, so that the names and units for both the groups and items are pre-populated so they don't need to manually input.
When a user clicks on a year row they are brought to (2)


## 2- Selected year screen
When a school year is selected, a new screen should show up that has 3 seperate tables described below:

Income Table:
5 columns: Income Item (name), expected (UGX), 1st month (UGX), 2nd month (UGX), 3rd month (UGX), totals

at the bottom of this table should be something that sums the totals for each row to get the total incomes.

Expense Table:
5 columns: Expense Item (name), budgeted (UGX), 1st month (UGX), 2nd month (UGX), 3rd month (UGX), totals

at the bottom of this table should be something that sums the totals for each row to get the total incomes.

Summary Table:
2 columns, 3 rows: 
Incomes collected, amount (UGX)
Expenditures, amount(UGX)
Surplus/Deficit, amount(UGX)

If a user clicks on a row in the income table they should be brought to (3)
If a user clicks on a row in th expense table they should be brought to (4)
--> in either case make sure the selected income/expense is at the top of the screen so it is easily viewable, so the screen auto scrolls to it.

Also on this screen should be an option to view (Detailed Incomes) and (Detailed Expenses). Clicking on it would bring them to the top of their respected (3 or 4) screen


## 3 - Detailed Incomes Screen
This screen would have a list of incomes, 1 for each income type that shows up in (2), for each income type have a table that shows:
5 columns: Item, Units, quantity, rate, amount (UGX)
Totals at the bottom for amount (should equal what is on (2))
Expected amount next to totals that the user can edit for how much they're planning on having. Should upate and show up on (2)


At the top of the incomes, there should be a table that is 1 row: Students, Amount, Total (should match amount in (2)), and when the user clicks on it is brought to (5)

## 4 - Detailed Expenses Screen
This screen would have a list of expenses, 1 for each expense type that shows up in (2), for each expense type have a table that shows: 
5 columns: Item, Units, quantity, rate, amount (UGX)
Totals at the bottom for amount (should equal what is on (2))
Budgeted amount next to totals that the user can edit for how much they're planning on budgeting. Should update and show up on (2)

## Same for 3 and 4
Can add both Income and Expense category here. within all the existing categories, the user should be able to create a expense/income item within each category. The only requirement is an item name and unit when creating it, but can add to the other fields as well.
The user should be able to directly add information to the tables in 3 & 4 (such as rate & quantity) which auto updates the totals, and will update in (2) when they go back
Can edit the respective 

## 5
Income table for just students. The tables are split into 2 big sections (Nursery and Primary) with classes:
Nursury: Baby, Middle, Top
Primary: P1, P2, ..., P7

In each row is a student. Students can be added via this page as well, and there are 9 columns:

Name, Amount, Debt, Ream, Total, 1st Month, 2nd Month, 3rd Month, Remarks

