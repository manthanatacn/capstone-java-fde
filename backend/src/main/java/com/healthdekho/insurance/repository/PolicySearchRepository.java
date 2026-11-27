package com.healthdekho.insurance.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import com.healthdekho.insurance.entity.CustomerPolicy;

/**
 * Read-only search over policies (FR-5). A null customerId means "all customers" (staff);
 * customers always pass their own id. Name patterns arrive with LIKE wildcards already escaped.
 */
public interface PolicySearchRepository extends Repository<CustomerPolicy, Long> {

    @Query("""
            select p from CustomerPolicy p join fetch p.customer c join fetch p.product
            where p.policyNumber = :policyNumber
              and (:customerId is null or c.id = :customerId)
            """)
    Optional<CustomerPolicy> findOneByPolicyNumber(String policyNumber, Long customerId);

    @Query("""
            select p from CustomerPolicy p join fetch p.customer c join fetch p.product
            where p.policyNumber = :policyNumber and c.dateOfBirth = :dateOfBirth
              and (:customerId is null or c.id = :customerId)
            """)
    Optional<CustomerPolicy> findOneByPolicyNumberAndDateOfBirth(String policyNumber, LocalDate dateOfBirth,
                                                                 Long customerId);

    @Query(value = """
            select p from CustomerPolicy p join fetch p.customer c join fetch p.product
            where lower(c.fullName) like lower(concat('%', :name, '%')) escape '\\'
              and (:customerId is null or c.id = :customerId)
            """,
            countQuery = """
            select count(p) from CustomerPolicy p join p.customer c
            where lower(c.fullName) like lower(concat('%', :name, '%')) escape '\\'
              and (:customerId is null or c.id = :customerId)
            """)
    Page<CustomerPolicy> findByCustomerName(String name, Long customerId, Pageable pageable);

    /**
     * One row per policy and installment status, for a whole page of policies at once. The status is
     * worked out from amounts and dates as of {@code today} (same rules as InstallmentRules.resolveStatus),
     * not read from payment_status, which is only refreshed when a policy is opened.
     */
    @Query("""
            select new com.healthdekho.insurance.repository.InstallmentTally(
                   i.policy.id,
                   case when i.paidAmount >= i.scheduledAmount + i.penaltyAmount
                             then com.healthdekho.insurance.domain.PaymentStatus.PAID
                        when i.gracePeriodEndDate < :today
                             then com.healthdekho.insurance.domain.PaymentStatus.OVERDUE
                        when i.paidAmount > 0
                             then com.healthdekho.insurance.domain.PaymentStatus.PARTIALLY_PAID
                        else com.healthdekho.insurance.domain.PaymentStatus.PENDING end as status,
                   sum(i.penaltyAmount), sum(i.paidAmount))
            from PremiumInstallment i
            where i.policy.id in :policyIds
            group by i.policy.id, status
            """)
    List<InstallmentTally> tallyInstallments(Collection<Long> policyIds, LocalDate today);
}
