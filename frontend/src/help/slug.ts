const punctuationPattern = /[ -⁯⸀-⹿\\'!"#$%&()*+,./:;<=>?@[\]^`{|}~]/g;
const cjkPunctuationPattern =
  /[　-〿！-／：-＠［-｀｛-･]/g;

export function slugifyHeading(text: string): string {
  return text
    .replace(/<[^>]+>/g, '')
    .trim()
    .toLowerCase()
    .replace(punctuationPattern, '')
    .replace(cjkPunctuationPattern, '')
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '');
}

// Keep duplicate heading anchors stable and GitHub-compatible (foo, foo-1, foo-2).
export function createHeadingSlugger() {
  const seen = new Map<string, number>();
  return (text: string) => {
    const base = slugifyHeading(text) || 'section';
    const count = seen.get(base) ?? 0;
    seen.set(base, count + 1);
    return count === 0 ? base : `${base}-${count}`;
  };
}
