package com.featureflagplatform.evaluation.domain;

/**
 * Whitelisted comparison operators for targeting rules. Deliberately small:
 * every value here is something both application code and the AI rule
 * assistant's schema validation can enumerate exhaustively — there is no
 * free-form operator string anywhere in the system.
 */
public enum TargetingOperator {
    EQUALS,
    NOT_EQUALS
}
