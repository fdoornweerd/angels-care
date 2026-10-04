package org.angelscare.management.student.ui;

import java.util.List;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.angelscare.management.calendar.model.TermRef;
import org.angelscare.management.common.ui.ErrorMessages;
import org.angelscare.management.student.model.Residency;
import org.angelscare.management.student.model.Student;
import org.angelscare.management.student.model.StudentEdit;
import org.angelscare.management.student.model.StudentStatus;
import org.angelscare.management.student.service.StudentAccountService;

/**
 * The Edit student dialog: names, National/Refugee, the term joined, and Active/Left. Save applies
 * all of it or, if anything is refused, none of it and shows why. Closing without Save changes
 * nothing.
 */
public class EditStudentViewModel {

    private final StudentAccountService accounts;
    private final Student student;
    private final TermRef term;
    private final List<TermRef> joinedChoices;
    private final Runnable onSaved;
    private final StringProperty firstName;
    private final StringProperty lastName;
    private final ObjectProperty<Residency> residency;
    private final ObjectProperty<TermRef> joined;
    private final ObjectProperty<StudentStatus> status;
    private final ReadOnlyStringWrapper error = new ReadOnlyStringWrapper("");

    /** For {@code student} as seen on {@code term}'s page; {@code onSaved} runs after a save. */
    public EditStudentViewModel(StudentAccountService accounts, Student student, TermRef term,
            List<TermRef> joinedChoices, Runnable onSaved) {
        this.accounts = accounts;
        this.student = student;
        this.term = term;
        this.joinedChoices = List.copyOf(joinedChoices);
        this.onSaved = onSaved;
        firstName = new SimpleStringProperty(student.firstName());
        lastName = new SimpleStringProperty(student.lastName());
        residency = new SimpleObjectProperty<>(student.residency());
        joined = new SimpleObjectProperty<>(
                student.joinedTerm() == null ? term : student.joinedTerm());
        status = new SimpleObjectProperty<>(student.status());
    }

    /** "Edit Ben Okello". */
    public String title() {
        return "Edit " + student.fullName();
    }

    public StringProperty firstNameProperty() {
        return firstName;
    }

    public StringProperty lastNameProperty() {
        return lastName;
    }

    public ObjectProperty<Residency> residencyProperty() {
        return residency;
    }

    public ObjectProperty<TermRef> joinedProperty() {
        return joined;
    }

    /** Every term of every school year, oldest first. */
    public List<TermRef> joinedChoices() {
        return joinedChoices;
    }

    public ObjectProperty<StudentStatus> statusProperty() {
        return status;
    }

    /** False, with the reason in {@link #errorProperty()}, if anything was refused. */
    public boolean save() {
        error.set("");
        try {
            accounts.editStudent(student.id(), term, new StudentEdit(firstName.get(),
                    lastName.get(), residency.get(), joined.get(), status.get()));
        } catch (Exception e) {
            error.set(ErrorMessages.forException(e));
            return false;
        }
        onSaved.run();
        return true;
    }

    public ReadOnlyStringProperty errorProperty() {
        return error.getReadOnlyProperty();
    }

    public String error() {
        return error.get();
    }
}
