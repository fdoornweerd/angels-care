package org.angelscare.management.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.angelscare.management.calendar.model.TermRef;
import org.angelscare.management.common.Ugx;
import org.angelscare.management.student.model.Residency;
import org.angelscare.management.student.model.SchoolClass;
import org.angelscare.management.student.model.Student;
import org.angelscare.management.student.model.StudentEdit;
import org.angelscare.management.student.model.StudentStatus;
import org.angelscare.management.support.ScreenTest;

/**
 * A 2026-2027 year with data in Term 1 only: two income categories (one without items), one
 * expense category, and three students in P4 and Baby, one of whom left, one with a credit.
 */
abstract class YearReportFixture extends ScreenTest {

    protected Student amina;
    protected Student ben;
    protected Student cara;
    /** Active, at the school, but put on no term: the report must not add them anywhere. */
    protected Student dan;

    protected void createFullYear() {
        createYear(2026);
        TermRef t1 = t(2026, 1);
        String church = incomeItem(2026, "Donations", "Church support", "months").id();
        record(finance.incomeSheet, church, t1, 1, "1", 200_000);
        record(finance.incomeSheet, church, t1, 3, "2.5", 1_000);
        finance.incomeSheet.setPlan(incomeCategory(2026, "Donations").id(), t1, Ugx.of(600_000));
        incomeCategory(2026, "Sales");
        String maize = expenseItem(2026, "Feeding", "Maize flour", "kg").id();
        record(finance.expenseSheet, maize, t1, 2, "50", 3_500);
        finance.expenseSheet.setPlan(expenseCategory(2026, "Feeding").id(), t1, Ugx.of(500_000));

        amina = createStudent("Amina", "Nakato", SchoolClass.P4);
        ben = createStudent("Ben", "Okello", SchoolClass.P4);
        cara = createStudent("Cara", "Akello", SchoolClass.BABY);
        finance.studentAccounts.setClassReam(SchoolClass.P4, t1, Ugx.of(10_000));
        finance.studentAccounts.openTerm(t1);
        finance.studentAccounts.setAmount(amina.id(), t1, Ugx.of(10_000_000));
        finance.studentAccounts.setPayment(amina.id(), t1, 1, Ugx.of(100_000));
        finance.studentAccounts.setAmount(ben.id(), t1, Ugx.of(250_000));
        finance.studentAccounts.setRemarks(ben.id(), t1, "Moved to Gulu");
        finance.studentAccounts.editStudent(ben.id(), t1, new StudentEdit("Ben", "Okello",
                Residency.NATIONAL, t1, StudentStatus.LEFT));
        finance.studentAccounts.setAmount(cara.id(), t1, Ugx.ZERO);
        finance.studentAccounts.setDebt(cara.id(), t1, Ugx.of(-20_000));
        dan = createStudent("Dan", "Mugisha", SchoolClass.P1);
    }

    /** Every row of every table, to check that making a report writes nothing. */
    protected Map<String, List<Map<String, Object>>> snapshot() {
        Map<String, List<Map<String, Object>>> tables = new LinkedHashMap<>();
        for (String table : jdbc.queryForList(
                "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name", String.class)) {
            tables.put(table, new ArrayList<>(jdbc.queryForList(
                    "SELECT * FROM \"" + table + "\" ORDER BY rowid")));
        }
        return tables;
    }
}
