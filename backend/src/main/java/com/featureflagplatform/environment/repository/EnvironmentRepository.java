package com.featureflagplatform.environment.repository;

import com.featureflagplatform.environment.domain.Environment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EnvironmentRepository extends JpaRepository<Environment, UUID> {

    Optional<Environment> findByName(String name);

    boolean existsByName(String name);
}
