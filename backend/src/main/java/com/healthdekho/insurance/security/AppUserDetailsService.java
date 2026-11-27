package com.healthdekho.insurance.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.healthdekho.insurance.domain.Role;
import com.healthdekho.insurance.repository.CustomerRepository;
import com.healthdekho.insurance.repository.StaffUserRepository;

/** Customers are checked first, then operations staff. Emails are unique within each table. */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final CustomerRepository customers;
    private final StaffUserRepository staff;

    public AppUserDetailsService(CustomerRepository customers, StaffUserRepository staff) {
        this.customers = customers;
        this.staff = staff;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        return customers.findByEmailIgnoreCase(email)
                .map(c -> new AppUserPrincipal(c.getId(), c.getEmail(), c.getPasswordHash(), c.getFullName(),
                        Role.CUSTOMER, c.isActive()))
                .or(() -> staff.findByEmailIgnoreCase(email)
                        .map(s -> new AppUserPrincipal(s.getId(), s.getEmail(), s.getPasswordHash(), s.getFullName(),
                                Role.STAFF, s.isActive())))
                .orElseThrow(() -> new UsernameNotFoundException("No user with that email"));
    }
}
