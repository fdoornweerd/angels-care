package org.angelscare.management.income.service;

import java.util.Comparator;
import java.util.List;
import org.angelscare.management.calendar.model.TermRange;
import org.angelscare.management.calendar.model.TermRef;
import org.angelscare.management.calendar.service.CalendarService;
import org.angelscare.management.common.Ugx;
import org.angelscare.management.common.ValidationException;
import org.angelscare.management.income.model.ApplicableFee;
import org.angelscare.management.income.model.BillingFrequency;
import org.angelscare.management.income.model.FeeAssignment;
import org.angelscare.management.income.model.FeeTarget;
import org.angelscare.management.income.model.IncomeCategory;
import org.angelscare.management.income.model.IncomeItem;
import org.angelscare.management.income.repository.FeeAssignmentRepository;
import org.angelscare.management.income.repository.IncomeCategoryRepository;
import org.angelscare.management.income.repository.IncomeItemRepository;
import org.angelscare.management.student.model.GroupMembership;
import org.angelscare.management.student.model.Student;
import org.angelscare.management.student.model.StudentGroup;
import org.angelscare.management.student.model.StudentStatus;
import org.angelscare.management.student.repository.GroupMembershipRepository;
import org.angelscare.management.student.repository.StudentGroupRepository;
import org.angelscare.management.student.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FeeAssignmentService {

    private final FeeAssignmentRepository assignments;
    private final IncomeItemRepository items;
    private final IncomeCategoryRepository categories;
    private final StudentRepository students;
    private final StudentGroupRepository groups;
    private final GroupMembershipRepository memberships;
    private final CalendarService calendar;
    private final FeeCoverageGuard coverageGuard;

    public FeeAssignmentService(FeeAssignmentRepository assignments,
            IncomeItemRepository items, IncomeCategoryRepository categories,
            StudentRepository students, StudentGroupRepository groups,
            GroupMembershipRepository memberships, CalendarService calendar,
            FeeCoverageGuard coverageGuard) {
        this.assignments = assignments;
        this.items = items;
        this.categories = categories;
        this.students = students;
        this.groups = groups;
        this.memberships = memberships;
        this.calendar = calendar;
        this.coverageGuard = coverageGuard;
    }

    public FeeAssignment assign(String incomeItemId, FeeTarget target, Ugx amount,
            BillingFrequency frequency, TermRange terms) {
        items.findById(incomeItemId)
                .orElseThrow(() -> new ValidationException("That income item no longer exists."));
        requireTarget(target);
        validate(amount, frequency);
        if (terms == null) {
            throw new ValidationException("Choose the first term the fee applies to.");
        }
        String startTermId = calendar.requireTerm(terms.start()).id();
        String endTermId = endTermId(terms.end());
        coverageGuard.checkAssignment(
                new FeeAssignment(null, incomeItemId, target, amount, frequency, terms));
        return assignments.insert(incomeItemId, target, amount, frequency, startTermId, endTermId);
    }

    /**
     * Corrects amount, frequency and last term ({@code end} null = open-ended). Item, target and
     * first term never change: end the assignment and start a new one instead.
     */
    public FeeAssignment update(String assignmentId, Ugx amount, BillingFrequency frequency,
            TermRef end) {
        FeeAssignment existing = require(assignmentId);
        validate(amount, frequency);
        TermRange terms = end == null
                ? TermRange.from(existing.terms().start())
                : TermRange.between(existing.terms().start(), end);
        String endTermId = endTermId(end);
        coverageGuard.checkAssignment(new FeeAssignment(assignmentId, existing.incomeItemId(),
                existing.target(), amount, frequency, terms));
        assignments.update(assignmentId, amount, frequency, endTermId);
        return require(assignmentId);
    }

    public void delete(String assignmentId) {
        require(assignmentId);
        assignments.softDelete(assignmentId);
    }

    @Transactional(readOnly = true)
    public List<FeeAssignment> listForItem(String incomeItemId) {
        return assignments.findByItem(incomeItemId);
    }

    /**
     * Every fee that applies to the student in {@code term}, sorted by category then item;
     * empty for a LEFT student.
     */
    @Transactional(readOnly = true)
    public List<ApplicableFee> feesFor(String studentId, TermRef term) {
        Student student = students.findById(studentId)
                .orElseThrow(() -> new ValidationException("That student no longer exists."));
        if (student.status() == StudentStatus.LEFT) {
            return List.of();
        }
        List<GroupMembership> studentMemberships = memberships.findByStudent(studentId);
        return assignments.findAll().stream()
                .filter(a -> Coverage.appliesIn(a, student, studentMemberships, term))
                .map(this::toApplicableFee)
                .sorted(Comparator.comparing((ApplicableFee f) -> f.category().name(),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(f -> f.item().name(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private ApplicableFee toApplicableFee(FeeAssignment assignment) {
        IncomeItem item = items.findById(assignment.incomeItemId()).orElseThrow();
        IncomeCategory category = categories.findById(item.categoryId()).orElseThrow();
        return new ApplicableFee(assignment.id(), category, item, assignment.amount(),
                assignment.frequency(), assignment.frequency().termTotal(assignment.amount()),
                assignment.target(), viaName(assignment.target()));
    }

    private String viaName(FeeTarget target) {
        return switch (target) {
            case FeeTarget.OneStudent t -> "Student";
            case FeeTarget.Group t -> groups.findById(t.groupId()).map(StudentGroup::name).orElseThrow();
            case FeeTarget.WholeClass t -> t.schoolClass().label();
        };
    }

    private void requireTarget(FeeTarget target) {
        switch (target) {
            case null -> throw new ValidationException("Choose who pays the fee.");
            case FeeTarget.OneStudent t -> students.findById(t.studentId())
                    .orElseThrow(() -> new ValidationException("That student no longer exists."));
            case FeeTarget.Group t -> groups.findById(t.groupId())
                    .orElseThrow(() -> new ValidationException("That group no longer exists."));
            case FeeTarget.WholeClass t -> {
                if (t.schoolClass() == null) {
                    throw new ValidationException("Choose a class for the fee.");
                }
            }
        }
    }

    private static void validate(Ugx amount, BillingFrequency frequency) {
        if (amount == null || amount.shillings() <= 0) {
            throw new ValidationException("A fee must be more than UGX 0.");
        }
        if (frequency == null) {
            throw new ValidationException("Choose whether the fee is charged per term or per month.");
        }
    }

    private String endTermId(TermRef end) {
        return end == null ? null : calendar.requireTerm(end).id();
    }

    private FeeAssignment require(String assignmentId) {
        return assignments.findById(assignmentId)
                .orElseThrow(() -> new ValidationException("That fee assignment no longer exists."));
    }
}
