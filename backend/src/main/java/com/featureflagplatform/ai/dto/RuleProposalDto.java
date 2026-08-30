package com.featureflagplatform.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.featureflagplatform.evaluation.domain.FlagType;
import com.featureflagplatform.featureflag.dto.TargetingRuleDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The exact schema the assessment's own example uses — {@code strategy},
 * {@code rolloutPercentage}, {@code rules}, {@code explanation} — deserialized
 * directly from whatever JSON the AI provider returns, then validated with
 * this same Bean Validation annotations before it's ever shown to a user,
 * let alone accepted as input to a create/update flag request.
 *
 * <p>Reuses {@link TargetingRuleDto} — the identical type (and identical
 * validation) a human-submitted flag's targeting rules go through — rather
 * than a parallel "AI rule" shape. This is what makes "AI output passes
 * through the same validation as human input" a fact about the code rather
 * than a policy someone has to remember to enforce.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)}: the AI is treated
 * as an unreliable narrator that might add fields nobody asked for (a
 * confidence score, a second explanation, whatever) — extra fields are
 * silently dropped rather than failing deserialization over noise that
 * doesn't affect correctness.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RuleProposalDto(
        @NotNull FlagType strategy,

        @Min(0) @Max(100) Integer rolloutPercentage,

        @NotNull
        @Size(max = 10, message = "at most 10 targeting rules per proposal")
        @Valid
        List<TargetingRuleDto> rules,

        @NotBlank
        @Size(max = 1000)
        String explanation
) {
}
