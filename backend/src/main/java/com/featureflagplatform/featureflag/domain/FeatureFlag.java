package com.featureflagplatform.featureflag.domain;

import com.featureflagplatform.auth.domain.User;
import com.featureflagplatform.environment.domain.Environment;
import com.featureflagplatform.evaluation.domain.FeatureFlagSnapshot;
import com.featureflagplatform.evaluation.domain.FlagType;
import com.featureflagplatform.evaluation.domain.TargetingRule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The mutable, persisted representation of a feature flag. Deliberately
 * distinct from {@link FeatureFlagSnapshot}: this type knows about JPA,
 * optimistic locking, and its owning {@link Environment}/{@link User}
 * associations; the snapshot knows about none of that and is what actually
 * flows into {@link com.featureflagplatform.evaluation.domain.FeatureFlagEvaluator}.
 * {@link #toSnapshot()} is the one-way bridge between them.
 *
 * <p>{@code targetingRules} is stored as JSONB via Hibernate's native JSON
 * mapping ({@code @JdbcTypeCode(SqlTypes.JSON)}) rather than a hand-rolled
 * {@code AttributeConverter} — it serializes the {@code TargetingRule} record
 * list with the same Jackson instance the rest of the app uses, with no extra
 * code to maintain.
 */
@Entity
@Table(name = "feature_flags")
public class FeatureFlag {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 100)
    private String key;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "environment_id", nullable = false)
    private Environment environment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FlagType type;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "rollout_percentage")
    private Integer rolloutPercentage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "targeting_rules", nullable = false, columnDefinition = "jsonb")
    private List<TargetingRule> targetingRules = List.of();

    @Version
    @Column(nullable = false)
    private long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "updated_by", nullable = false)
    private User updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FeatureFlag() {
        // JPA
    }

    public FeatureFlag(String key, String name, String description, Environment environment,
                        FlagType type, boolean enabled, Integer rolloutPercentage,
                        List<TargetingRule> targetingRules, User createdBy) {
        this.key = Objects.requireNonNull(key);
        this.name = Objects.requireNonNull(name);
        this.description = description;
        this.environment = Objects.requireNonNull(environment);
        this.type = Objects.requireNonNull(type);
        this.enabled = enabled;
        this.rolloutPercentage = rolloutPercentage;
        this.targetingRules = targetingRules == null ? List.of() : List.copyOf(targetingRules);
        this.createdBy = Objects.requireNonNull(createdBy);
        this.updatedBy = createdBy;
    }

    /** Immutable, framework-free view for the evaluation engine. */
    public FeatureFlagSnapshot toSnapshot() {
        return new FeatureFlagSnapshot(id, key, environment.getName(), type, enabled, rolloutPercentage, targetingRules, version);
    }

    public void applyUpdate(String name, String description, boolean enabled, Integer rolloutPercentage,
                             List<TargetingRule> targetingRules, User updatedBy) {
        this.name = Objects.requireNonNull(name);
        this.description = description;
        this.enabled = enabled;
        this.rolloutPercentage = rolloutPercentage;
        this.targetingRules = targetingRules == null ? List.of() : List.copyOf(targetingRules);
        this.updatedBy = Objects.requireNonNull(updatedBy);
    }

    public UUID getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public FlagType getType() {
        return type;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Integer getRolloutPercentage() {
        return rolloutPercentage;
    }

    public List<TargetingRule> getTargetingRules() {
        return targetingRules;
    }

    public long getVersion() {
        return version;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public User getUpdatedBy() {
        return updatedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
