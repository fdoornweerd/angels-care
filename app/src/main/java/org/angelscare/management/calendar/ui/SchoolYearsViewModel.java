package org.angelscare.management.calendar.ui;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.angelscare.management.accounts.model.YearTotals;
import org.angelscare.management.accounts.service.SchoolYearSetupService;
import org.angelscare.management.accounts.service.TermAccountsService;
import org.angelscare.management.calendar.model.SchoolYear;
import org.angelscare.management.calendar.model.TermDates;
import org.angelscare.management.calendar.service.CalendarService;
import org.angelscare.management.common.SchoolTime;
import org.angelscare.management.common.ValidationException;
import org.angelscare.management.common.ui.ConfirmDialogs;
import org.angelscare.management.common.ui.ErrorMessages;

/** Page 1: the school years with their totals, and the form to add or change one. */
public class SchoolYearsViewModel {

    /** A row of the list. */
    public record YearRow(SchoolYear year, YearTotals totals) {
    }

    private final SchoolYearSetupService setup;
    private final CalendarService calendar;
    private final TermAccountsService accounts;
    private final ConfirmDialogs dialogs;
    private final Clock clock;

    private final ObservableList<YearRow> rows = FXCollections.observableArrayList();
    private final ReadOnlyBooleanWrapper editing = new ReadOnlyBooleanWrapper();
    private final ObjectProperty<Integer> startYear = new SimpleObjectProperty<>();
    private final ReadOnlyStringWrapper yearName = new ReadOnlyStringWrapper("");
    private final List<ObjectProperty<LocalDate>> starts = new ArrayList<>();
    private final List<ObjectProperty<LocalDate>> ends = new ArrayList<>();
    private final ReadOnlyBooleanWrapper copyOffered = new ReadOnlyBooleanWrapper();
    private final ReadOnlyStringWrapper copyLabel = new ReadOnlyStringWrapper("");
    private final BooleanProperty copyFromPrevious = new SimpleBooleanProperty();
    private final ReadOnlyStringWrapper error = new ReadOnlyStringWrapper("");
    /** The year being edited, or null for a new one. */
    private SchoolYear editingYear;

    public SchoolYearsViewModel(SchoolYearSetupService setup, CalendarService calendar,
            TermAccountsService accounts, ConfirmDialogs dialogs, Clock clock) {
        this.setup = setup;
        this.calendar = calendar;
        this.accounts = accounts;
        this.dialogs = dialogs;
        this.clock = clock;
        for (int i = 0; i < CalendarService.TERMS_PER_YEAR; i++) {
            starts.add(new SimpleObjectProperty<>());
            ends.add(new SimpleObjectProperty<>());
        }
        startYear.addListener((obs, old, year) -> {
            yearName.set(year == null ? "" : SchoolYear.label(year));
            updateCopyOffer();
        });
    }

    public void refresh() {
        rows.setAll(calendar.listYears().stream()
                .sorted(Comparator.comparingInt(SchoolYear::year).reversed())
                .map(year -> new YearRow(year, accounts.yearTotals(year.year())))
                .toList());
    }

    /** Newest first. */
    public ObservableList<YearRow> rows() {
        return rows;
    }

    public String emptyHint() {
        return "No school years yet. Click New school year to set one up.";
    }

    /** Opens the form for a new year, starting the year after the latest one. */
    public void newYear() {
        editingYear = null;
        int next = calendar.listYears().stream().map(SchoolYear::year).max(Integer::compare)
                .map(latest -> latest + 1)
                .orElse(SchoolTime.today(clock).getYear());
        fill(next, null);
        editing.set(true);
    }

    /** Opens the form on an existing year: its dates can change, its start year can't. */
    public void edit(SchoolYear year) {
        editingYear = year;
        fill(year.year(), year);
        editing.set(true);
    }

    /** True while the form is open. */
    public ReadOnlyBooleanProperty editingProperty() {
        return editing.getReadOnlyProperty();
    }

    public ObjectProperty<Integer> startYearProperty() {
        return startYear;
    }

    /** "2027-2028" for the typed start year. */
    public ReadOnlyStringProperty yearNameProperty() {
        return yearName.getReadOnlyProperty();
    }

    /** True while a new year is being added; an existing year's start year is fixed. */
    public boolean isNewYear() {
        return editingYear == null;
    }

    public ObjectProperty<LocalDate> termStart(int number) {
        return starts.get(number - 1);
    }

    public ObjectProperty<LocalDate> termEnd(int number) {
        return ends.get(number - 1);
    }

    /** True for a new year when an earlier year exists to copy from. */
    public ReadOnlyBooleanProperty copyOfferedProperty() {
        return copyOffered.getReadOnlyProperty();
    }

    /** "Copy income and expense categories and items from 2026-2027". */
    public ReadOnlyStringProperty copyLabelProperty() {
        return copyLabel.getReadOnlyProperty();
    }

    public BooleanProperty copyFromPreviousProperty() {
        return copyFromPrevious;
    }

    /** Saves the form; false (with an error) if a rule is broken. */
    public boolean save() {
        error.set("");
        try {
            if (editingYear != null) {
                calendar.updateTermDates(editingYear.year(), dates(1), dates(2), dates(3));
            } else {
                if (startYear.get() == null) {
                    throw new ValidationException("Enter the year it starts in, e.g. 2026.");
                }
                setup.createYear(startYear.get(), dates(1), dates(2), dates(3),
                        copyOffered.get() && copyFromPrevious.get());
            }
        } catch (Exception e) {
            error.set(ErrorMessages.forException(e));
            return false;
        }
        editing.set(false);
        refresh();
        return true;
    }

    public void cancel() {
        editing.set(false);
        error.set("");
    }

    /** Deletes after "Delete 'School year 2026-2027'?"; false if cancelled or blocked. */
    public boolean delete(SchoolYear year) {
        if (!dialogs.confirm("Delete 'School year " + year.label() + "'?")) {
            return false;
        }
        error.set("");
        try {
            calendar.deleteYear(year.id());
        } catch (Exception e) {
            error.set(ErrorMessages.forException(e));
            return false;
        }
        refresh();
        return true;
    }

    public ReadOnlyStringProperty errorProperty() {
        return error.getReadOnlyProperty();
    }

    public String error() {
        return error.get();
    }

    private void fill(int year, SchoolYear existing) {
        error.set("");
        for (int i = 0; i < CalendarService.TERMS_PER_YEAR; i++) {
            starts.get(i).set(existing == null ? null : existing.terms().get(i).dates().start());
            ends.get(i).set(existing == null ? null : existing.terms().get(i).dates().end());
        }
        if (Integer.valueOf(year).equals(startYear.get())) {
            updateCopyOffer();
        } else {
            startYear.set(year);
        }
    }

    private void updateCopyOffer() {
        Optional<SchoolYear> previous = editingYear != null || startYear.get() == null
                ? Optional.empty()
                : setup.previousYear(startYear.get());
        copyOffered.set(previous.isPresent());
        copyLabel.set(previous.map(p -> "Copy income and expense categories and items from "
                + p.label()).orElse(""));
        copyFromPrevious.set(previous.isPresent());
    }

    private TermDates dates(int number) {
        return new TermDates(termStart(number).get(), termEnd(number).get());
    }
}
