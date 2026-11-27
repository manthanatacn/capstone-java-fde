package com.healthdekho.insurance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.healthdekho.insurance.domain.Role;
import com.healthdekho.insurance.dto.search.PolicySearchCriteria;
import com.healthdekho.insurance.exception.InvalidSearchCriteriaException;
import com.healthdekho.insurance.repository.PolicySearchRepository;
import com.healthdekho.insurance.security.AuthenticatedUser;
import com.healthdekho.insurance.support.TestClockConfig;

@ExtendWith(MockitoExtension.class)
class PolicySearchServiceTest {

    private static final AuthenticatedUser STAFF = new AuthenticatedUser(9001L, "ops@x", "Ops", Role.STAFF);
    private static final AuthenticatedUser AARAV = new AuthenticatedUser(1001L, "aarav@x", "Aarav", Role.CUSTOMER);
    private static final LocalDate DOB = LocalDate.of(1990, 4, 15);

    @Mock
    private PolicySearchRepository repository;

    private PolicySearchService service;

    @BeforeEach
    void setUp() {
        service = new PolicySearchService(repository, Clock.fixed(
                TestClockConfig.TODAY.atStartOfDay(TestClockConfig.ZONE).toInstant(), TestClockConfig.ZONE));
    }

    @Test
    void policyNumberAloneSearchesByNumber() {
        when(repository.findOneByPolicyNumber(any(), any())).thenReturn(Optional.empty());

        service.search(new PolicySearchCriteria(" hdp-2026-0001 ", null, null), 0, 10, STAFF);

        verify(repository).findOneByPolicyNumber(eq("HDP-2026-0001"), isNull());
    }

    @Test
    void customerNameAloneSearchesByName() {
        when(repository.findByCustomerName(any(), any(), any())).thenReturn(Page.empty());

        service.search(new PolicySearchCriteria("", "meera", null), 0, 10, STAFF);

        verify(repository).findByCustomerName(eq("meera"), isNull(), any());
    }

    @Test
    void policyNumberWithDateOfBirthSearchesByBoth() {
        when(repository.findOneByPolicyNumberAndDateOfBirth(any(), any(), any())).thenReturn(Optional.empty());

        service.search(new PolicySearchCriteria("HDP-2026-0001", null, DOB), 0, 10, STAFF);

        verify(repository).findOneByPolicyNumberAndDateOfBirth(eq("HDP-2026-0001"), eq(DOB), isNull());
    }

    @Test
    void customersAreRestrictedToTheirOwnPolicies() {
        when(repository.findByCustomerName(any(), any(), any())).thenReturn(Page.empty());

        service.search(new PolicySearchCriteria(null, "meera", null), 0, 10, AARAV);

        verify(repository).findByCustomerName(eq("meera"), eq(1001L), any());
    }

    @ParameterizedTest(name = "number={0}, name={1}, dob={2}")
    @CsvSource(nullValues = "null", value = {
            "null, null, null",
            "'  ', '  ', null",
            "null, null, 1990-04-15",
            "null, Aarav, 1990-04-15",
            "HDP-2026-0001, Aarav, null",
            "HDP-2026-0001, Aarav, 1990-04-15"
    })
    void unsupportedCombinationsAreRejected(String number, String name, LocalDate dob) {
        assertThatThrownBy(() -> service.search(new PolicySearchCriteria(number, name, dob), 0, 10, STAFF))
                .isInstanceOf(InvalidSearchCriteriaException.class)
                .hasMessage(PolicySearchService.VALID_COMBINATIONS)
                .extracting("code").isEqualTo("INVALID_SEARCH_CRITERIA");
        verifyNoInteractions(repository);
    }

    @Test
    void oneLetterNameIsRejected() {
        assertThatThrownBy(() -> service.search(new PolicySearchCriteria(null, " a ", null), 0, 10, STAFF))
                .isInstanceOf(InvalidSearchCriteriaException.class)
                .hasMessageContaining("at least 2 characters");
        verifyNoInteractions(repository);
    }

    @Test
    void pageSizeIsCappedAndSortedByPolicyNumber() {
        when(repository.findByCustomerName(any(), any(), any())).thenReturn(Page.empty());

        service.search(new PolicySearchCriteria(null, "meera", null), -1, 500, STAFF);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByCustomerName(any(), any(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(PolicySearchService.MAX_PAGE_SIZE);
        assertThat(pageable.getValue().getSort().getOrderFor("policyNumber")).isNotNull();
    }
}
