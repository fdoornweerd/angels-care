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
            throw new ValidationException("School year " + SchoolYear.label(year) + " already exists.");
        }
        requireNoOverlapWithNeighbours(year, terms);
        return years.insert(year, terms);
    }

    public Term updateTermDates(TermRef term, TermDates dates) {
        requireTerm(term);
        List<TermDates> proposed = new ArrayList<>(
                requireYear(term.year()).terms().stream().map(Term::dates).toList());
        proposed.set(term.number() - 1, dates);
        updateTermDates(term.year(), proposed.get(0), proposed.get(1), proposed.get(2));
        return requireTerm(term);
    }

    /** Replaces all three terms' dates at once, so terms can be moved past each other's old dates. */
    public SchoolYear updateTermDates(int year, TermDates term1, TermDates term2, TermDates term3) {
        SchoolYear schoolYear = requireYear(year);
        List<TermDates> proposed = List.of(term1, term2, term3);
        validate(year, proposed);
        requireNoOverlapWithNeighbours(year, proposed);
        for (int i = 0; i < TERMS_PER_YEAR; i++) {
            years.updateTermDates(schoolYear.terms().get(i).id(), proposed.get(i));
        }
        return requireYear(year);
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
                ? "School year " + SchoolYear.label(year) + " has not been set up yet."
                : "No school year has been set up yet. Set up school year " + SchoolYear.label(year)
                        + " first."));
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
                        + " in " + year.label() + "; a school year has Terms 1 to "
                        + TERMS_PER_YEAR + "."));
    }

    /** Soft-deletes the year and its three terms; blocked while anything uses them. */
    public void deleteYear(String schoolYearId) {
        SchoolYear year = years.findById(schoolYearId)
                .orElseThrow(() -> new ValidationException("That school year no longer exists."));
        String name = "school year " + year.label();
        for (Term term : year.terms()) {
            deletionGuard.requireUnused("term", term.id(), name + " (Term " + term.ref().number() + ")");
        }
        // Its terms are part of the year and go with it.
        deletionGuard.requireUnused("school_year", year.id(), name, "term");
        years.softDelete(year);
    }

    /**
     * The rules within one school year: every term has both dates, starts before it ends, and
     * starts after the previous term ends; Term 1 starts in {@code year}; and nothing ends after
     * 31 December of the following year (a school year may run from, say, September to July).
     */
    private static void validate(int year, List<TermDates> terms) {
        LocalDate latestEnd = LocalDate.of(year + 1, 12, 31);
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
            if (i == 0 && dates.start().getYear() != year) {
                throw new ValidationException("Term 1 of " + SchoolYear.label(year)
                        + " must start in " + year + ".");
            }
            if (previousEnd != null && !dates.start().isAfter(previousEnd)) {
                throw new ValidationException(name + " must start after Term " + i + " ends.");
            }
            if (dates.end().isAfter(latestEnd)) {
                throw new ValidationException(name + " of " + SchoolYear.label(year)
                        + " must end by the end of " + (year + 1) + ".");
            }
            previousEnd = dates.end();
        }
    }

    /**
     * Now that a school year can run into the next calendar year, two school years could overlap:
     * each must end before the next one's Term 1 starts.
     */
    private void requireNoOverlapWithNeighbours(int year, List<TermDates> terms) {
        List<SchoolYear> others = years.findAll().stream().filter(y -> y.year() != year).toList();
        others.stream().filter(y -> y.year() < year).reduce((a, b) -> b).ifPresent(previous -> {
            if (!terms.get(0).start().isAfter(lastDay(previous))) {
                throw new ValidationException(SchoolYear.label(year) + "'s Term 1 must start after "
                        + previous.label() + "'s Term 3 ends.");
            }
        });
        others.stream().filter(y -> y.year() > year).findFirst().ifPresent(next -> {
            if (!terms.get(terms.size() - 1).end().isBefore(next.terms().get(0).dates().start())) {
                throw new ValidationException(SchoolYear.label(year) + "'s Term 3 must end before "
                        + next.label() + "'s Term 1 starts.");
            }
        });
    }

    private static LocalDate lastDay(SchoolYear year) {
        return year.terms().get(year.terms().size() - 1).dates().end();
    }

    /**
     * The term pickers' starting term for {@code today}: the term containing it; between terms, the
     * next one; after the last term, the last one; before the first, the first. Empty when there
     * are no school years.
     */
    @Transactional(readOnly = true)
    public Optional<TermRef> defaultTerm(LocalDate today) {
        List<Term> terms = years.findAll().stream().flatMap(y -> y.terms().stream()).toList();
        // The first term that hasn't ended yet contains today or is the next one.
        return terms.stream()
                .filter(term -> !today.isAfter(term.dates().end()))
                .findFirst()
                .or(() -> terms.stream().reduce((first, second) -> second))
                .map(Term::ref);
    }
}
