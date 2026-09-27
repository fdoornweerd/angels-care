package org.angelscare.management.shell.ui;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import org.angelscare.management.accounts.model.Ledger;
import org.angelscare.management.calendar.model.SchoolYear;
import org.angelscare.management.calendar.model.Term;
import org.angelscare.management.calendar.model.TermRef;
import org.angelscare.management.calendar.service.CalendarService;
import org.angelscare.management.common.SchoolTime;

/**
 * Which page is open, how the user got there (for Back and the breadcrumb), and the term that
 * pages 2-5 show.
 */
public class Navigator {

    /** The pages of the window. */
    public sealed interface Page {

        /** 1: the list of school years. */
        record SchoolYears() implements Page {
        }

        /** 2: the term summary of one school year. */
        record TermSummary(int year) implements Page {
        }

        /** 3 or 4: a ledger's detail page, scrolled to {@code focusCategoryId} (null = top). */
        record Detail(Ledger ledger, int year, String focusCategoryId) implements Page {
        }

        /** 5: the students of the chosen term. */
        record Students(int year) implements Page {
        }
    }

    private final CalendarService calendar;
    private final Clock clock;
    /** The pages opened so far, the current one on top. */
    private final Deque<Page> trail = new ArrayDeque<>();
    private final ReadOnlyObjectWrapper<Page> current = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<TermRef> term = new ReadOnlyObjectWrapper<>();

    public Navigator(CalendarService calendar, Clock clock) {
        this.calendar = calendar;
        this.clock = clock;
    }

    public ReadOnlyObjectProperty<Page> currentProperty() {
        return current.getReadOnlyProperty();
    }

    public Page current() {
        return current.get();
    }

    /** The term pages 2-5 show; null on page 1. */
    public ReadOnlyObjectProperty<TermRef> termProperty() {
        return term.getReadOnlyProperty();
    }

    public TermRef term() {
        return term.get();
    }

    /** Opens page 1. */
    public void start() {
        trail.clear();
        term.set(null);
        push(new Page.SchoolYears());
    }

    /** Page 2 for the year, on the term containing today (Kampala) or the next; else Term 1. */
    public void openYear(int year) {
        term.set(openingTerm(calendar.requireYear(year)));
        push(new Page.TermSummary(year));
    }

    /** A term tab on page 2 (1-3). */
    public void selectTerm(int number) {
        term.set(TermRef.of(term.get().year(), number));
    }

    public void openDetail(Ledger ledger, String focusCategoryId) {
        push(new Page.Detail(ledger, term.get().year(), focusCategoryId));
    }

    public void openStudents() {
        push(new Page.Students(term.get().year()));
    }

    /** Back one page; false on page 1. */
    public boolean back() {
        if (trail.size() <= 1) {
            return false;
        }
        trail.pop();
        if (trail.peek() instanceof Page.SchoolYears) {
            term.set(null);
        }
        current.set(trail.peek());
        return true;
    }

    /** E.g. School years › 2026-2027 › Term 1 › Detailed incomes › Students. */
    public List<String> breadcrumb() {
        List<String> parts = new ArrayList<>();
        trail.descendingIterator().forEachRemaining(page -> {
            switch (page) {
                case Page.SchoolYears p -> parts.add("School years");
                case Page.TermSummary p -> {
                    parts.add(SchoolYear.label(p.year()));
                    parts.add("Term " + term.get().number());
                }
                case Page.Detail p -> parts.add(p.ledger() == Ledger.INCOME
                        ? "Detailed incomes" : "Detailed expenses");
                case Page.Students p -> parts.add("Students");
            }
        });
        return parts;
    }

    private void push(Page page) {
        trail.push(page);
        current.set(page);
    }

    private TermRef openingTerm(SchoolYear year) {
        LocalDate today = SchoolTime.today(clock);
        List<Term> terms = year.terms();
        boolean todayInYear = !today.isBefore(terms.get(0).dates().start())
                && !today.isAfter(terms.get(terms.size() - 1).dates().end());
        if (!todayInYear) {
            return terms.get(0).ref();
        }
        // The first term that hasn't ended yet contains today or is the next one.
        return terms.stream().filter(t -> !today.isAfter(t.dates().end())).findFirst()
                .orElseThrow().ref();
    }
}
