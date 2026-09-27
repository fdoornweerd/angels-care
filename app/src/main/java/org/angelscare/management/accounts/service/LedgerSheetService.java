package org.angelscare.management.accounts.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.angelscare.management.accounts.model.ItemMonth;
import org.angelscare.management.accounts.model.Ledger;
import org.angelscare.management.accounts.repository.EntryRepository;
import org.angelscare.management.accounts.repository.PlanRepository;
import org.angelscare.management.calendar.model.SchoolYear;
import org.angelscare.management.calendar.model.Term;
import org.angelscare.management.calendar.model.TermRef;
import org.angelscare.management.calendar.service.CalendarService;
import org.angelscare.management.common.MonthlyAmounts;
import org.angelscare.management.common.Quantity;
import org.angelscare.management.common.Ugx;
import org.angelscare.management.common.ValidationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * One ledger's sheet (pages 3 and 4): quantity and rate per item per month of a term, and the
 * expected/budgeted amount per category per term. The same class serves income and expenses.
 */
@Transactional
public class LedgerSheetService {

    private final Ledger ledger;
    private final EntryRepository entries;
    private final PlanRepository plans;
    private final CalendarService calendar;

    public LedgerSheetService(Ledger ledger, EntryRepository entries, PlanRepository plans,
            CalendarService calendar) {
        this.ledger = ledger;
        this.entries = entries;
        this.plans = plans;
        this.calendar = calendar;
    }

    public Ledger ledger() {
        return ledger;
    }

    /** The item's entry for that month; blank (nulls) if nothing was recorded. */
    @Transactional(readOnly = true)
    public ItemMonth entry(String itemId, TermRef term, int month) {
        Term stored = requireTermOfItem(itemId, term, month);
        return entries.find(itemId, stored.id(), month)
                .map(row -> itemMonth(row, term))
                .orElse(new ItemMonth(itemId, term, month, null, null));
    }

    /** Every recorded entry of this ledger for the term and month, by item id. */
    @Transactional(readOnly = true)
    public Map<String, ItemMonth> monthEntries(TermRef term, int month) {
        Map<String, ItemMonth> byItem = new LinkedHashMap<>();
        findTerm(term).ifPresent(stored -> entries.findByTermAndMonth(stored.id(), month)
                .forEach(row -> byItem.put(row.itemId(), itemMonth(row, term))));
        return byItem;
    }

    /** Sets (or, with null, clears) the quantity. Clearing both quantity and rate removes the entry. */
    public ItemMonth setQuantity(String itemId, TermRef term, int month, Quantity quantity) {
        return save(itemId, term, month, quantity == null ? null : quantity.hundredths(), false);
    }

    /** Sets (or, with null, clears) the rate: a whole, non-negative amount. */
    public ItemMonth setRate(String itemId, TermRef term, int month, Ugx rate) {
        if (rate != null && rate.isNegative()) {
            throw new ValidationException("A rate can't be less than UGX 0.");
        }
        return save(itemId, term, month, rate == null ? null : rate.shillings(), true);
    }

    /** What the category's items came to in each month of the term. */
    @Transactional(readOnly = true)
    public MonthlyAmounts categoryTotals(String categoryId, TermRef term) {
        MonthlyAmounts totals = MonthlyAmounts.ZERO;
        Optional<Term> stored = findTerm(term);
        if (stored.isEmpty()) {
            return totals;
        }
        for (EntryRepository.Row row : entries.findByCategoryAndTerm(categoryId, stored.get().id())) {
            Optional<Ugx> amount = itemMonth(row, term).amount();
            if (amount.isPresent()) {
                totals = totals.with(row.month(), totals.month(row.month()).plus(amount.get()));
            }
        }
        return totals;
    }

    @Transactional(readOnly = true)
    public Optional<Ugx> plan(String categoryId, TermRef term) {
        return findTerm(term)
                .flatMap(stored -> plans.find(categoryId, stored.id()))
                .map(row -> Ugx.of(row.amount()));
    }

    /** Sets (0 or more) or, with null, clears the category's expected/budgeted amount. */
    public void setPlan(String categoryId, TermRef term, Ugx amount) {
        if (amount != null && amount.isNegative()) {
            throw new ValidationException("An amount can't be less than UGX 0.");
        }
        String yearId = plans.schoolYearOfCategory(categoryId)
                .orElseThrow(() -> new ValidationException("That category no longer exists."));
        Term stored = requireTermOfYear(yearId, term);
        Optional<PlanRepository.Row> existing = plans.find(categoryId, stored.id());
        if (amount == null) {
            existing.ifPresent(row -> plans.softDelete(row.id()));
        } else if (existing.isPresent()) {
            plans.update(existing.get().id(), amount.shillings());
        } else {
            plans.insert(categoryId, stored.id(), amount.shillings());
        }
    }

    private ItemMonth save(String itemId, TermRef term, int month, Long value, boolean isRate) {
        Term stored = requireTermOfItem(itemId, term, month);
        Optional<EntryRepository.Row> existing = entries.find(itemId, stored.id(), month);
        Long quantity = isRate ? existing.map(EntryRepository.Row::quantityHundredths).orElse(null) : value;
        Long rate = isRate ? value : existing.map(EntryRepository.Row::rate).orElse(null);
        if (existing.isPresent()) {
            if (quantity == null && rate == null) {
                entries.softDelete(existing.get().id());
            } else {
                entries.update(existing.get().id(), quantity, rate);
            }
        } else if (quantity != null || rate != null) {
            entries.insert(itemId, stored.id(), month, quantity, rate);
        }
        return new ItemMonth(itemId, term, month,
                quantity == null ? null : Quantity.ofHundredths(quantity),
                rate == null ? null : Ugx.of(rate));
    }

    private Term requireTermOfItem(String itemId, TermRef term, int month) {
        if (month < 1 || month > 3) {
            throw new ValidationException("A term has months 1 to 3.");
        }
        String yearId = entries.schoolYearOfItem(itemId)
                .orElseThrow(() -> new ValidationException("That item no longer exists."));
        return requireTermOfYear(yearId, term);
    }

    /** The term, which must belong to the school year the item or category belongs to. */
    private Term requireTermOfYear(String schoolYearId, TermRef term) {
        SchoolYear year = calendar.requireYear(term.year());
        if (!year.id().equals(schoolYearId)) {
            throw new ValidationException("That belongs to another school year than " + year.label()
                    + ".");
        }
        return calendar.requireTerm(term);
    }

    private Optional<Term> findTerm(TermRef term) {
        return calendar.findYear(term.year()).flatMap(year -> year.terms().stream()
                .filter(t -> t.ref().equals(term)).findFirst());
    }

    private static ItemMonth itemMonth(EntryRepository.Row row, TermRef term) {
        return new ItemMonth(row.itemId(), term, row.month(),
                row.quantityHundredths() == null ? null : Quantity.ofHundredths(row.quantityHundredths()),
                row.rate() == null ? null : Ugx.of(row.rate()));
    }
}
