package org.angelscare.management.student.ui;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.angelscare.management.calendar.model.TermRef;
import org.angelscare.management.common.Ugx;
import org.angelscare.management.common.ValidationException;
import org.angelscare.management.common.ui.ConfirmDialogs;
import org.angelscare.management.common.ui.ErrorMessages;
import org.angelscare.management.common.ui.UgxField;
import org.angelscare.management.student.model.ClassFee;
import org.angelscare.management.student.model.RegisterTotals;
import org.angelscare.management.student.model.Residency;
import org.angelscare.management.student.model.SchoolClass;
import org.angelscare.management.student.model.StudentTermLine;
import org.angelscare.management.student.service.StudentAccountService;

/**
 * Page 5: the students of a term, one table per class (Nursery, then Primary). An edit to one
 * student replaces just their line; class fee edits and added or removed students reload.
 */
public class StudentsViewModel {

    /** One class's table. */
    public static final class ClassSection {
        private final SchoolClass schoolClass;
        private final ObservableList<StudentTermLine> lines = FXCollections.observableArrayList();
        private final ReadOnlyObjectWrapper<ClassFee> fee = new ReadOnlyObjectWrapper<>();
        private final ReadOnlyObjectWrapper<RegisterTotals> totals = new ReadOnlyObjectWrapper<>();

        public ClassSection(SchoolClass schoolClass) {
            this.schoolClass = schoolClass;
        }

        public SchoolClass schoolClass() {
            return schoolClass;
        }

        public ObservableList<StudentTermLine> lines() {
            return lines;
        }

        public ReadOnlyObjectProperty<ClassFee> feeProperty() {
            return fee.getReadOnlyProperty();
        }

        public ReadOnlyObjectProperty<RegisterTotals> totalsProperty() {
            return totals.getReadOnlyProperty();
        }
    }

    /** A value typed into a cell, read or rejected with the "whole amount" message. */
    private interface Parser {
        Ugx parse(String text);
    }

    /** A save of one student's value. */
    private interface LineSave {
        StudentTermLine save(Ugx value);
    }

    private final StudentAccountService accounts;
    private final ConfirmDialogs dialogs;
    private final ObservableList<ClassSection> sections = FXCollections.observableArrayList();
    private final BooleanProperty showLeft = new SimpleBooleanProperty(false);
    private final ReadOnlyObjectWrapper<RegisterTotals> pageTotals = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyStringWrapper error = new ReadOnlyStringWrapper("");
    private TermRef term;

    public StudentsViewModel(StudentAccountService accounts, ConfirmDialogs dialogs) {
        this.accounts = accounts;
        this.dialogs = dialogs;
        showLeft.addListener((obs, old, now) -> {
            if (term != null) {
                reload();
            }
        });
    }

    /** Opens the term (adding Active students not yet on it) and loads it. */
    public void show(TermRef term) {
        this.term = term;
        error.set("");
        accounts.openTerm(term);
        reload();
    }

    /** One section per class, Baby to P7, including classes with no students. */
    public ObservableList<ClassSection> sections() {
        return sections;
    }

    public ClassSection section(SchoolClass schoolClass) {
        return sections.stream().filter(s -> s.schoolClass == schoolClass).findFirst()
                .orElseThrow();
    }

    /** "Show students who left". */
    public BooleanProperty showLeftProperty() {
        return showLeft;
    }

    public ReadOnlyObjectProperty<RegisterTotals> pageTotalsProperty() {
        return pageTotals.getReadOnlyProperty();
    }

    /** Blank clears the student's own amount (back to the class fee). */
    public boolean editAmount(StudentTermLine line, String text) {
        return edit(line, text, UgxField::parse,
                amount -> accounts.setAmount(line.studentId(), term, amount));
    }

    /** A minus sign means credit. Blank goes back to the carried balance. */
    public boolean editDebt(StudentTermLine line, String text) {
        return edit(line, text, UgxField::parseSigned,
                debt -> accounts.setDebt(line.studentId(), term, debt));
    }

    public boolean editReam(StudentTermLine line, String text) {
        return edit(line, text, UgxField::parse,
                ream -> accounts.setReam(line.studentId(), term, ream));
    }

    public boolean editPayment(StudentTermLine line, int month, String text) {
        return edit(line, text, UgxField::parse,
                amount -> accounts.setPayment(line.studentId(), term, month, amount));
    }

    public boolean editRemarks(StudentTermLine line, String text) {
        error.set("");
        try {
            replace(accounts.setRemarks(line.studentId(), term, text));
        } catch (Exception e) {
            error.set(line.name() + ": " + ErrorMessages.forException(e));
            return false;
        }
        return true;
    }

    public boolean editClassFee(ClassSection section, String text) {
        ClassFee fee = section.fee.get();
        return editClass(section, text, amount -> accounts.setClassFee(section.schoolClass, term,
                amount, fee.ream()));
    }

    public boolean editClassReam(ClassSection section, String text) {
        ClassFee fee = section.fee.get();
        return editClass(section, text, ream -> accounts.setClassFee(section.schoolClass, term,
                fee.amount(), ream));
    }

    public boolean addStudent(SchoolClass schoolClass, String firstName, String lastName,
            Residency residency) {
        error.set("");
        try {
            accounts.addStudent(term, firstName, lastName, schoolClass, residency);
        } catch (Exception e) {
            error.set(ErrorMessages.forException(e));
            return false;
        }
        reload();
        return true;
    }

    /** After a confirmation; refused when the student has payments this term. */
    public boolean removeFromTerm(StudentTermLine line) {
        if (!dialogs.confirm("Remove " + line.name() + " from " + term.label() + "?")) {
            return false;
        }
        error.set("");
        try {
            accounts.removeFromTerm(line.studentId(), term);
        } catch (Exception e) {
            error.set(ErrorMessages.forException(e));
            return false;
        }
        reload();
        return true;
    }

    public ReadOnlyStringProperty errorProperty() {
        return error.getReadOnlyProperty();
    }

    public String error() {
        return error.get();
    }

    private boolean edit(StudentTermLine line, String text, Parser parser, LineSave save) {
        error.set("");
        try {
            Ugx value = text == null || text.isBlank() ? null : parser.parse(text);
            replace(save.save(value));
        } catch (Exception e) {
            error.set(line.name() + ": " + ErrorMessages.forException(e));
            return false;
        }
        return true;
    }

    private boolean editClass(ClassSection section, String text,
            Consumer<Ugx> save) {
        error.set("");
        try {
            save.accept(text == null || text.isBlank() ? Ugx.ZERO : UgxField.parse(text));
        } catch (ValidationException e) {
            error.set(section.schoolClass.label() + ": " + e.getMessage());
            return false;
        } catch (Exception e) {
            error.set(ErrorMessages.forException(e));
            return false;
        }
        reload();
        return true;
    }

    /** Puts a student's updated line in place, and updates the totals. */
    private void replace(StudentTermLine updated) {
        ClassSection section = section(updated.schoolClass());
        for (int i = 0; i < section.lines.size(); i++) {
            if (section.lines.get(i).studentId().equals(updated.studentId())) {
                section.lines.set(i, updated);
            }
        }
        section.totals.set(RegisterTotals.of(section.lines));
        pageTotals.set(RegisterTotals.of(sections.stream()
                .flatMap(s -> s.lines.stream()).toList()));
    }

    private void reload() {
        List<StudentTermLine> lines = accounts.lines(term, showLeft.get());
        sections.setAll(Arrays.stream(SchoolClass.values()).map(schoolClass -> {
            ClassSection section = new ClassSection(schoolClass);
            section.lines.setAll(lines.stream().filter(l -> l.schoolClass() == schoolClass).toList());
            section.fee.set(accounts.classFee(schoolClass, term));
            section.totals.set(RegisterTotals.of(section.lines));
            return section;
        }).toList());
        pageTotals.set(RegisterTotals.of(lines));
    }
}
