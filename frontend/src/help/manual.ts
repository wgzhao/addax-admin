import { createHeadingSlugger } from './slug';

export type ManualGroupKey = 'usage' | 'operations';

export interface ManualHeading {
  depth: number;
  text: string;
  id: string;
}

export interface ManualChapter {
  slug: string;
  group: ManualGroupKey;
  order: number;
  title: string;
  description: string;
  content: string;
  headings: ManualHeading[];
  searchText: string;
}

export interface ManualGroup {
  key: ManualGroupKey;
  label: string;
  chapters: ManualChapter[];
}

const GROUP_LABELS: Record<ManualGroupKey, string> = {
  usage: '使用指南',
  operations: '运维排障',
};

const GROUP_ORDER: Record<ManualGroupKey, number> = {
  usage: 0,
  operations: 1,
};

const modules = import.meta.glob(
  ['../../../docs/manual/usage/*.md', '../../../docs/manual/operations/*.md'],
  { query: '?raw', import: 'default', eager: true }
) as Record<string, string>;

function firstHeading(content: string) {
  const matched = content.match(/^#\s+(.+)$/m);
  return matched?.[1]?.trim() || '';
}

function firstParagraph(content: string) {
  const lines = content.split('\n');
  let inCode = false;
  for (const rawLine of lines) {
    const line = rawLine.trim();
    if (line.startsWith('```')) {
      inCode = !inCode;
      continue;
    }
    if (inCode || !line) continue;
    if (/^#{1,6}\s/.test(line)) continue;
    if (/^\|/.test(line) || /^[-*+]\s/.test(line) || /^\d+\.\s/.test(line)) continue;
    if (line.startsWith('>')) continue;
    return line
      .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
      .replace(/[*_`]/g, '')
      .trim();
  }
  return '';
}

function parseHeadings(content: string): ManualHeading[] {
  const slugger = createHeadingSlugger();
  const headings: ManualHeading[] = [];
  let inCode = false;
  for (const rawLine of content.split('\n')) {
    const line = rawLine.trim();
    if (line.startsWith('```')) {
      inCode = !inCode;
      continue;
    }
    if (inCode) continue;
    const matched = line.match(/^(#{2,3})\s+(.+)$/);
    if (!matched) continue;
    const text = matched[2].replace(/`/g, '').trim();
    headings.push({
      depth: matched[1].length,
      text,
      id: slugger(text),
    });
  }
  return headings;
}

function searchableText(content: string) {
  return content
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
    .replace(/[#>*_`|~-]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .toLowerCase();
}

function chapterFrom(path: string, raw: string): ManualChapter | null {
  const matched = path.match(/docs\/manual\/(usage|operations)\/(\d+)-([^/]+)\.md$/);
  if (!matched) return null;
  const group = matched[1] as ManualGroupKey;
  const order = Number(matched[2]);
  const fileSlug = matched[3];
  const slug = `${group}/${matched[2]}-${fileSlug}`;
  const title = firstHeading(raw) || fileSlug;
  return {
    slug,
    group,
    order,
    title,
    description: firstParagraph(raw),
    content: raw,
    headings: parseHeadings(raw),
    searchText: searchableText(`${title}\n${raw}`),
  };
}

export const chapters: ManualChapter[] = Object.entries(modules)
  .map(([path, raw]) => chapterFrom(path, raw))
  .filter((chapter): chapter is ManualChapter => chapter !== null)
  .sort(
    (a, b) => GROUP_ORDER[a.group] - GROUP_ORDER[b.group] || a.order - b.order
  );

export const chapterMap = new Map(chapters.map(chapter => [chapter.slug, chapter]));

export const manualGroups: ManualGroup[] = (['usage', 'operations'] as const)
  .map(key => ({
    key,
    label: GROUP_LABELS[key],
    chapters: chapters.filter(chapter => chapter.group === key),
  }))
  .filter(group => group.chapters.length > 0);

export const defaultChapterSlug =
  chapterMap.has('usage/01-quick-start') ? 'usage/01-quick-start' : chapters[0]?.slug || '';

export interface ManualSearchResult {
  slug: string;
  title: string;
  hash?: string;
  kind: 'chapter' | 'heading';
  context?: string;
}

export function searchManual(query: string, limit = 30): ManualSearchResult[] {
  const normalized = query.trim().toLowerCase();
  if (!normalized) return [];
  const results: ManualSearchResult[] = [];
  for (const chapter of chapters) {
    if (
      chapter.title.toLowerCase().includes(normalized) ||
      chapter.searchText.includes(normalized)
    ) {
      results.push({
        slug: chapter.slug,
        title: chapter.title,
        kind: 'chapter',
        context: chapter.description,
      });
    }
    for (const heading of chapter.headings) {
      if (heading.text.toLowerCase().includes(normalized)) {
        results.push({
          slug: chapter.slug,
          title: heading.text,
          hash: heading.id,
          kind: 'heading',
          context: chapter.title,
        });
      }
    }
    if (results.length >= limit) break;
  }
  return results.slice(0, limit);
}
