import type { CollectionPlanReviewRow } from '@/types/database';

export type ReviewRowIssue = 'EMPTY_TARGET' | 'DUPLICATE_TARGET' | 'MISSING_TARGET' | null;

// Why: the wizard and the review list must agree on what blocks a commit,
// otherwise a row can look green while the confirm button stays disabled.
export function reviewRowIssue(row: CollectionPlanReviewRow): ReviewRowIssue {
  const targets = row.mappings.map(mapping => mapping.targetColumnName?.trim().toLowerCase() ?? '');
  if (targets.some(target => !target)) return 'EMPTY_TARGET';
  if (new Set(targets).size !== targets.length) return 'DUPLICATE_TARGET';

  if (row.plan.schemaManagementMode === 'EXTERNAL') {
    const available = new Set(
      row.plan.actualTargetTable.columns.map(column => column.name.toLowerCase())
    );
    if (targets.some(target => !available.has(target))) return 'MISSING_TARGET';
  }

  return null;
}

export function reviewRowValid(row: CollectionPlanReviewRow): boolean {
  return reviewRowIssue(row) === null;
}

export function reviewRowIssueLabel(issue: ReviewRowIssue): string {
  if (issue === 'EMPTY_TARGET') return '映射不完整';
  if (issue === 'DUPLICATE_TARGET') return '目标字段重复';
  if (issue === 'MISSING_TARGET') return '目标字段不存在';
  return '';
}
