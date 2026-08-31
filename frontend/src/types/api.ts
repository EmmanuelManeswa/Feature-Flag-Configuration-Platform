// Mirrors the backend DTOs exactly (see backend/src/main/java/.../*/dto/).
// The backend is always the source of truth for validation — these types
// exist for editor/compile-time safety, not as a second source of rules.

export type Role = "ADMIN" | "VIEWER";

export interface UserSummary {
  id: string;
  email: string;
  displayName: string;
  role: Role;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: UserSummary;
}

export interface EnvironmentDto {
  id: string;
  name: string;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export type FlagType = "BOOLEAN" | "PERCENTAGE_ROLLOUT";
export type TargetingOperator = "EQUALS" | "NOT_EQUALS";

export interface TargetingRuleDto {
  attribute: string;
  operator: TargetingOperator;
  value: string;
}

export interface FeatureFlagDto {
  id: string;
  key: string;
  name: string;
  description: string | null;
  environmentId: string;
  environmentName: string;
  type: FlagType;
  enabled: boolean;
  rolloutPercentage: number | null;
  targetingRules: TargetingRuleDto[];
  version: number;
  createdByEmail: string;
  updatedByEmail: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateFeatureFlagRequest {
  key: string;
  name: string;
  description?: string | null;
  environmentId: string;
  type: FlagType;
  enabled: boolean;
  rolloutPercentage?: number | null;
  targetingRules: TargetingRuleDto[];
}

export interface UpdateFeatureFlagRequest {
  name: string;
  description?: string | null;
  enabled: boolean;
  rolloutPercentage?: number | null;
  targetingRules: TargetingRuleDto[];
  expectedVersion: number;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface AuditLogDto {
  id: string;
  actorEmail: string;
  action: "CREATE" | "UPDATE" | "DELETE";
  entityType: string;
  entityId: string;
  environmentId: string | null;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  previousValue: any;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  newValue: any;
  version: number | null;
  correlationId: string;
  createdAt: string;
}

export type EvaluationReason =
  | "FLAG_DISABLED"
  | "TARGETING_RULE_NOT_MATCHED"
  | "BOOLEAN_MATCH"
  | "ROLLOUT_INCLUDED"
  | "ROLLOUT_EXCLUDED";

export interface EvaluationResultDto {
  value: boolean;
  reason: EvaluationReason;
  bucket: number | null;
  unmatchedRule: { attribute: string; operator: string; value: string } | null;
  flagKey: string;
  environmentName: string;
  cacheHit: boolean;
  evaluationLatencyMicros: number;
}

export interface RuleProposalDto {
  strategy: FlagType;
  rolloutPercentage: number | null;
  rules: TargetingRuleDto[];
  explanation: string;
}

export interface UserDto {
  id: string;
  email: string;
  displayName: string;
  role: Role;
  enabled: boolean;
  createdAt: string;
}

export interface CreateUserRequest {
  email: string;
  displayName: string;
  role: Role;
}

export interface CreatedUserDto {
  user: UserDto;
  generatedPassword: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface EvaluationMetricsDto {
  flagKey: string;
  countsByResult: Record<string, number>;
  totalEvaluations: number;
}

export type FlagChangeType = "CREATED" | "UPDATED" | "DELETED";

export interface FlagChangeEvent {
  flagId: string;
  flagKey: string;
  environmentId: string;
  type: FlagChangeType;
  occurredAt: string;
}

export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance: string;
  correlationId: string;
  errors?: Record<string, string>;
  currentVersion?: number;
  expectedVersion?: number;
}
