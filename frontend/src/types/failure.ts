export type FailureStage =
  | 'PREPARE'
  | 'SOURCE_SCHEMA'
  | 'TARGET_SCHEMA'
  | 'PARTITION'
  | 'JOB_TEMPLATE'
  | 'COLLECT'
  | 'QUEUE'
  | 'RECOVERY';

export type FailureCategory =
  | 'OOM'
  | 'NETWORK'
  | 'DISK'
  | 'AUTH'
  | 'TABLE_NOT_FOUND'
  | 'CONTRACT'
  | 'DATA'
  | 'TIMEOUT'
  | 'PROCESS'
  | 'UNKNOWN';

export interface Failure {
  id: number;
  tid: number;
  executionId: number | null;
  jobQueueId: number | null;
  logId: number | null;
  logName: string | null;
  occurredAt: string;
  stage: FailureStage;
  category: FailureCategory;
  errorClass: string | null;
  errorCode: string | null;
  summary: string;
  detail: string | null;
  attemptNo: number | null;
  maxAttempts: number | null;
  finalAttempt: boolean;
  runType: string | null;
  bizDate: string | null;
  sourceId: number | null;
  sourceDb: string | null;
  sourceTable: string | null;
  targetDb: string | null;
  targetTable: string | null;
}

export interface FailureOccurrences {
  count: number;
  firstSeenAt: string | null;
  lastSeenAt: string | null;
}

export type EvidenceState = 'available' | 'purged' | 'missing' | 'restricted';

export interface FailureEvidence {
  detail: EvidenceState;
  log: EvidenceState;
  execution: EvidenceState;
}

export interface LatestFailure {
  available: boolean;
  reason: 'NO_FAILURE' | 'NO_EVIDENCE_FOR_STATE' | 'PURGED' | null;
  failure: Failure | null;
  occurrences: FailureOccurrences;
  evidence: FailureEvidence;
  hint: string | null;
}

export interface FailureDetail {
  failure: Failure;
  occurrences: FailureOccurrences;
  evidence: FailureEvidence;
  hint: string | null;
}

export interface FailureGroup {
  fingerprint: string;
  tid: number;
  stage: FailureStage;
  category: FailureCategory;
  summary: string;
  count: number;
  firstSeenAt: string;
  lastSeenAt: string;
  sourceDb: string | null;
  sourceTable: string | null;
  targetDb: string | null;
  targetTable: string | null;
}

export interface FailureStats {
  total: number;
  distinctTables: number;
  byStage: Record<string, number>;
  byCategory: Record<string, number>;
}

export interface FailureCleanupResult {
  matched: number;
  deleted: number;
  dryRun: boolean;
}
