package org.angelscare.management.calendar.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.angelscare.management.calendar.model.SchoolYear;
import org.angelscare.management.calendar.model.Term;
import org.angelscare.management.calendar.model.TermDates;
import org.angelscare.management.calendar.model.TermRef;
import org.angelscare.management.calendar.repository.SchoolYearRepository;
import org.angelscare.management.common.DeletionGuard;
import org.angelscare.management.common.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CalendarService {

    public static final int TERMS_PER_YEAR = 3;

    private final SchoolYearRepository years;
    private final DeletionGuard deletionGuard;

    public CalendarService(SchoolYearRepository years, DeletionGuard deletionGuard) {
        this.years = years;
        this.deletionGuard = deletionGuard;
    }

    public SchoolYear createYear(int year, TermDates term1, TermDates term2, TermDates term3) {
        List<TermDates> terms = List.of(term1, term2, term3);
        validate(year, terms);
        if (years.findByYear(year).isPresent()) {
            throw new ValidationException("School year " + year + " already exists.");
        }
        return years.insert(year, terms);
    }

    public Term updateTermDates(TermRef term, TermDates dates) {
        SchoolYear year = requireYear(term.year());
        Term existing = requireTerm(term);
        List<TermDates> proposed = new ArrayList<>(year.terms().stream().map(Term::dates).toList());
        proposed.set(term.number() - 1, dates);
        validate(term.year(), proposed);
        years.updateTermDates(existing.id(), dates);
        return requireTerm(term);
    }

    @Transactional(readOnly = true)
    public List<SchoolYear> listYears() {
        return years.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<SchoolYear> findYear(int year) {
        return years.findByYear(year);
    }

    /** The year, or a ValidationException saying it has to be set up first. */
    @Transactional(readOnly = true)
    public SchoolYear requireYear(int year) {
        return years.findByYear(year).orElseThrow(() -> new ValidationException(years.anyExists()
                ? "School year " + year + " has not been set up yet."
                : "No school year has been set up yet. Set up school year " + year + " first."));
    }

    /**
     * The term, or a {@link ValidationException} saying the school year has to be set up first.
     */
    @Transactional(readOnly = true)
    public Term requireTerm(TermRef term) {
        SchoolYear year = requireYear(term.year());
        return year.terms().stream()
                .filter(t -> t.ref().equals(term))
                .findFirst()
                .orElseThrow(() -> new ValidationException("There is no Term " + term.number()
                        + " in " + term.year() + "; a school year has Terms 1 to "
                        + TERMS_PER_YEAR + "."));
    }

    /** Soft-deletes the year and its three terms; blocked while anything uses them. */
    public void deleteYear(String schoolYearId) {
        SchoolYear year = years.findById(schoolYearId)
                .orElseThrow(() -> new ValidationException("That school year no longer exists."));
        String name = "school year " + year.year();
        for (Term term : year.terms()) {
            deletionGuard.requireUnused("term", term.id(), name + " (Term " + term.ref().number() + ")");
        }
        // Its terms are part of the year and go with it.
        deletionGuard.requireUnused("school_year", year.id(), name, "term");
        years.softDelete(year);
    }

    private static void validate(int year, List<TermDates> terms) {
        LocalDate previousEnd = null;
        for (int i = 0; i < terms.size(); i++) {
            String name = "Term " + (i + 1);
            TermDates dates = terms.get(i);
            if (dates == null || dates.start() == null || dates.end() == null) {
                throw new ValidationException(name + " needs a start and an end date.");
            }
            if (dates.start().isAfter(dates.end())) {
                throw new ValidationException(name + " can't start after it ends.");
            }
            if (dates.start().getYear() != year || dates.end().getYear() != year) {
                throw new ValidationException(name + " must fall within " + year + ".");
            }
            if (previousEnd != null && !dates.start().isAfter(previousEnd)) {
                throw new ValidationException(name + " must start after Term " + i + " ends.");
            }
            previousEnd = dates.end();
        }
    }
}
