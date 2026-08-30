package com.featureflagplatform.featureflag.service;

import com.featureflagplatform.audit.domain.AuditAction;
import com.featureflagplatform.audit.service.AuditService;
import com.featureflagplatform.auth.domain.User;
import com.featureflagplatform.common.exception.ResourceNotFoundException;
import com.featureflagplatform.common.exception.StaleVersionConflictException;
import com.featureflagplatform.environment.domain.Environment;
import com.featureflagplatform.environment.service.EnvironmentService;
import com.featureflagplatform.evaluation.domain.FlagType;
import com.featureflagplatform.evaluation.domain.TargetingRule;
import com.featureflagplatform.evaluation.service.FeatureFlagCache;
import com.featureflagplatform.featureflag.domain.FeatureFlag;
import com.featureflagplatform.featureflag.dto.CreateFeatureFlagRequest;
import com.featureflagplatform.featureflag.dto.FeatureFlagDto;
import com.featureflagplatform.featureflag.dto.TargetingRuleDto;
import com.featureflagplatform.featureflag.dto.UpdateFeatureFlagRequest;
import com.featureflagplatform.featureflag.event.FlagChangeEvent;
import com.featureflagplatform.featureflag.event.FlagChangeType;
import com.featureflagplatform.featureflag.mapper.FeatureFlagMapper;
import com.featureflagplatform.featureflag.repository.FeatureFlagRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class FeatureFlagService {

    private final FeatureFlagRepository featureFlagRepository;
    private final EnvironmentService environmentService;
    private final FeatureFlagMapper mapper;
    private final AuditService auditService;
    private final FeatureFlagCache cache;
    private final ApplicationEventPublisher eventPublisher;

    public FeatureFlagService(
            FeatureFlagRepository featureFlagRepository,
            EnvironmentService environmentService,
            FeatureFlagMapper mapper,
            AuditService auditService,
            FeatureFlagCache cache,
            ApplicationEventPublisher eventPublisher) {
        this.featureFlagRepository = featureFlagRepository;
        this.environmentService = environmentService;
        this.mapper = mapper;
        this.auditService = auditService;
        this.cache = cache;
        this.eventPublisher = eventPublisher;
    }

    public Page<FeatureFlagDto> list(UUID environmentId, Pageable pageable) {
        Page<FeatureFlag> page = environmentId == null
                ? featureFlagRepository.findAll(pageable)
                : featureFlagRepository.findByEnvironmentId(environmentId, pageable);
        return page.map(mapper::toDto);
    }

    public FeatureFlagDto getById(UUID id) {
        return mapper.toDto(findEntity(id));
    }

    private FeatureFlag findEntity(UUID id) {
        return featureFlagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FeatureFlag", id));
    }

    @Transactional
    public FeatureFlagDto create(CreateFeatureFlagRequest request, User actor) {
        validateRolloutInvariant(request.type(), request.rolloutPercentage());

        Environment environment = environmentService.findEntity(request.environmentId());

        if (featureFlagRepository.existsByEnvironmentIdAndKey(environment.getId(), request.key())) {
            throw new IllegalArgumentException(
                    "A flag with key '%s' already exists in environment '%s'".formatted(request.key(), environment.getName()));
        }

        List<TargetingRule> targetingRules = toDomainRules(request.targetingRules());

        FeatureFlag flag = new FeatureFlag(
                request.key(), request.name(), request.description(), environment,
                request.type(), request.enabled(), request.rolloutPercentage(), targetingRules, actor);
        flag = featureFlagRepository.save(flag);

        FeatureFlagDto dto = mapper.toDto(flag);
        auditService.record(actor, AuditAction.CREATE, "FeatureFlag", flag.getId(), environment, null, dto, flag.getVersion());
        cache.put(flag.toSnapshot());
        publishChange(flag, FlagChangeType.CREATED);

        return dto;
    }

    @Transactional
    public FeatureFlagDto update(UUID id, UpdateFeatureFlagRequest request, User actor) {
        FeatureFlag flag = findEntity(id);

        if (!request.expectedVersion().equals(flag.getVersion())) {
            throw new StaleVersionConflictException(id, request.expectedVersion(), flag.getVersion());
        }

        validateRolloutInvariant(flag.getType(), request.rolloutPercentage());

        FeatureFlagDto previousDto = mapper.toDto(flag);
        List<TargetingRule> targetingRules = toDomainRules(request.targetingRules());

        flag.applyUpdate(request.name(), request.description(), request.enabled(), request.rolloutPercentage(), targetingRules, actor);
        // saveAndFlush, not save: plain save() defers the actual UPDATE (and the
        // @Version increment that comes with it) to end-of-transaction commit, so
        // flag.getVersion() read straight after it would still show the *old*
        // version. Caught by manual smoke-testing: the API response and the audit
        // record were both reporting the pre-update version, which would make the
        // very next legitimate edit look "stale" against the real DB state.
        flag = featureFlagRepository.saveAndFlush(flag);

        FeatureFlagDto newDto = mapper.toDto(flag);
        auditService.record(actor, AuditAction.UPDATE, "FeatureFlag", flag.getId(), flag.getEnvironment(), previousDto, newDto, flag.getVersion());
        cache.put(flag.toSnapshot());
        publishChange(flag, FlagChangeType.UPDATED);

        return newDto;
    }

    @Transactional
    public void delete(UUID id, User actor) {
        FeatureFlag flag = findEntity(id);
        FeatureFlagDto previousDto = mapper.toDto(flag);
        UUID environmentId = flag.getEnvironment().getId();
        String key = flag.getKey();

        featureFlagRepository.delete(flag);

        auditService.record(actor, AuditAction.DELETE, "FeatureFlag", id, flag.getEnvironment(), previousDto, null, flag.getVersion());
        cache.evict(id);
        eventPublisher.publishEvent(new FlagChangeEvent(id, key, environmentId, FlagChangeType.DELETED, Instant.now()));
    }

    /**
     * Published from inside the same {@code @Transactional} method as the
     * mutation itself, but {@link com.featureflagplatform.featureflag.event.FlagChangeNotifier}
     * only broadcasts it {@code AFTER_COMMIT} — see that class's Javadoc for
     * why a subscriber must never hear about a change that rolls back.
     */
    private void publishChange(FeatureFlag flag, FlagChangeType type) {
        eventPublisher.publishEvent(new FlagChangeEvent(
                flag.getId(), flag.getKey(), flag.getEnvironment().getId(), type, Instant.now()));
    }

    private static void validateRolloutInvariant(FlagType type, Integer rolloutPercentage) {
        if (type == FlagType.PERCENTAGE_ROLLOUT && rolloutPercentage == null) {
            throw new IllegalArgumentException("rolloutPercentage is required for PERCENTAGE_ROLLOUT flags");
        }
        if (type == FlagType.BOOLEAN && rolloutPercentage != null) {
            throw new IllegalArgumentException("rolloutPercentage must not be set for BOOLEAN flags");
        }
    }

    private static List<TargetingRule> toDomainRules(
            List<TargetingRuleDto> dtos) {
        return dtos == null ? List.of() : dtos.stream().map(TargetingRuleDto::toDomain).toList();
    }
}
