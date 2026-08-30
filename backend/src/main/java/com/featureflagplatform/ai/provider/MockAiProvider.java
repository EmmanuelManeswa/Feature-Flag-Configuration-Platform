package com.featureflagplatform.ai.provider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The default provider — no network call, no external dependency, always
 * available. This is deliberately the safety-net default (see
 * {@code app.ai.provider} in application.yml): the assessment explicitly
 * requires the AI feature to be demonstrable even when a real model isn't,
 * and this is what makes that true without any setup at all.
 *
 * <p>Not an LLM — a small deterministic keyword parser that handles the
 * assessment's own canonical example ("enable this for 20% of users in
 * Harare except internal staff") precisely, plus reasonable heuristics for
 * similar phrasing. It intentionally does not try to be a general-purpose
 * NLU system; when it can't confidently extract a rollout percentage, it
 * falls back to a plain BOOLEAN proposal and says so in the explanation
 * rather than guessing.
 */
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockAiProvider implements AiProvider {

    private static final Pattern PERCENTAGE = Pattern.compile("(\\d{1,3})\\s*%");
    private static final Pattern LOCATION = Pattern.compile("\\bin\\s+([A-Z][a-z]+)\\b");
    private static final Pattern DEPARTMENT = Pattern.compile(
            "\\bin\\s+the\\s+([A-Z][a-z]+)\\s+(?:department|team)\\b");
    private static final Pattern EXCLUDE_INTERNAL_STAFF = Pattern.compile(
            "\\b(?:except|excluding|but not|not for)\\s+(?:internal\\s+staff|staff|employees|internal\\s+users)\\b",
            Pattern.CASE_INSENSITIVE);

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        Matcher percentageMatch = PERCENTAGE.matcher(userPrompt);
        Integer percentage = percentageMatch.find()
                ? Math.min(100, Math.max(0, Integer.parseInt(percentageMatch.group(1))))
                : null;

        List<String> rules = new ArrayList<>();
        List<String> explanationParts = new ArrayList<>();

        Matcher locationMatch = LOCATION.matcher(userPrompt);
        if (locationMatch.find()) {
            String location = locationMatch.group(1);
            rules.add(rule("location", "EQUALS", location));
            explanationParts.add("restricted to location " + location);
        }

        Matcher departmentMatch = DEPARTMENT.matcher(userPrompt);
        if (departmentMatch.find()) {
            String department = departmentMatch.group(1);
            rules.add(rule("department", "EQUALS", department));
            explanationParts.add("restricted to the " + department + " department");
        }

        if (EXCLUDE_INTERNAL_STAFF.matcher(userPrompt).find()) {
            rules.add(rule("userType", "NOT_EQUALS", "INTERNAL_STAFF"));
            explanationParts.add("excluding internal staff");
        }

        String rulesJson = String.join(",", rules);

        if (percentage != null) {
            String explanation = "Enable for " + percentage + "% of matching users"
                    + (explanationParts.isEmpty() ? "." : ", " + String.join(", ", explanationParts) + ".");
            return """
                    {"strategy":"PERCENTAGE_ROLLOUT","rolloutPercentage":%d,"rules":[%s],"explanation":"%s"}
                    """.formatted(percentage, rulesJson, escape(explanation)).trim();
        }

        String explanation = "Enable for all matching users"
                + (explanationParts.isEmpty() ? "" : " (" + String.join(", ", explanationParts) + ")")
                + ". No rollout percentage was mentioned, so this defaults to a plain on/off flag"
                + " — review before applying.";
        return """
                {"strategy":"BOOLEAN","rolloutPercentage":null,"rules":[%s],"explanation":"%s"}
                """.formatted(rulesJson, escape(explanation)).trim();
    }

    private static String rule(String attribute, String operator, String value) {
        return """
                {"attribute":"%s","operator":"%s","value":"%s"}""".formatted(attribute, operator, escape(value));
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
