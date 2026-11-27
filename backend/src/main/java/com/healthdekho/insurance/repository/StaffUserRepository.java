package com.healthdekho.insurance.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.healthdekho.insurance.entity.StaffUser;

public interface StaffUserRepository extends JpaRepository<StaffUser, Long> {

    Optional<StaffUser> findByEmailIgnoreCase(String email);
}
