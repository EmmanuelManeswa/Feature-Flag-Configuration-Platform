package com.featureflagplatform.environment.service;

import com.featureflagplatform.common.exception.ResourceNotFoundException;
import com.featureflagplatform.environment.domain.Environment;
import com.featureflagplatform.environment.dto.CreateEnvironmentRequest;
import com.featureflagplatform.environment.dto.EnvironmentDto;
import com.featureflagplatform.environment.repository.EnvironmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class EnvironmentService {

    private final EnvironmentRepository environmentRepository;

    public EnvironmentService(EnvironmentRepository environmentRepository) {
        this.environmentRepository = environmentRepository;
    }

    public List<EnvironmentDto> listAll() {
        return environmentRepository.findAll().stream().map(EnvironmentDto::from).toList();
    }

    public EnvironmentDto getById(UUID id) {
        return EnvironmentDto.from(findEntity(id));
    }

    public Environment findEntity(UUID id) {
        return environmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Environment", id));
    }

    @Transactional
    public EnvironmentDto create(CreateEnvironmentRequest request) {
        if (environmentRepository.existsByName(request.name())) {
            throw new IllegalArgumentException("An environment named '%s' already exists".formatted(request.name()));
        }
        Environment environment = new Environment(request.name(), request.description());
        return EnvironmentDto.from(environmentRepository.save(environment));
    }
}
