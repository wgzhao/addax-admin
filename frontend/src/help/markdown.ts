import DOMPurify from 'dompurify';
import hljs from 'highlight.js/lib/common';
import { Marked, Renderer, type Tokens } from 'marked';
import { chapterMap } from './manual';
import { createHeadingSlugger } from './slug';

const GITHUB_BLOB_BASE = 'https://github.com/wgzhao/addax-admin/blob/master/';
const MANUAL_PREFIX = 'docs/manual/';

function escapeHtml(value: string) {
  return value.replace(
    /[&<>"']/g,
    character =>
      ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;',
      })[character] || character
  );
}

function stripHtml(value: string) {
  return value.replace(/<[^>]+>/g, '');
}

function normalizeDocsPath(currentSlug: string, relativePath: string) {
  const parts = ['docs', 'manual', ...currentSlug.split('/').slice(0, -1)];
  for (const part of relativePath.split('/')) {
    if (!part || part === '.') continue;
    if (part === '..') {
      parts.pop();
      continue;
    }
    parts.push(part);
  }
  return parts.join('/');
}

interface ResolvedLink {
  href: string;
  external: boolean;
  internal?: boolean;
  slug?: string;
  hash?: string;
}

function resolveLink(href: string, currentSlug: string): ResolvedLink {
  if (!href) return { href: '', external: false };
  if (/^(https?:|mailto:)/i.test(href)) return { href, external: true };
  if (href.startsWith('#')) return { href, external: false };

  const [rawPath, rawHash = ''] = href.split('#');
  if (!rawPath || !rawPath.endsWith('.md')) return { href, external: false };

  const path = decodeURIComponent(rawPath);
  const hash = rawHash ? decodeURIComponent(rawHash) : '';
  const target = normalizeDocsPath(currentSlug, path);

  if (target.startsWith(MANUAL_PREFIX)) {
    const slug = target.slice(MANUAL_PREFIX.length).replace(/\.md$/, '');
    if (chapterMap.has(slug)) {
      return {
        href: `/help?chapter=${encodeURIComponent(slug)}${hash ? `#${encodeURIComponent(hash)}` : ''}`,
        external: false,
        internal: true,
        slug,
        hash,
      };
    }
  }

  if (target.startsWith('docs/') || target.endsWith('.md')) {
    return { href: `${GITHUB_BLOB_BASE}${target}`, external: true };
  }

  return { href, external: false };
}

function createRenderer(currentSlug: string) {
  const slugger = createHeadingSlugger();
  return {
    heading(this: Renderer, { tokens, depth }: Tokens.Heading) {
      const inner = this.parser.parseInline(tokens);
      const id = slugger(stripHtml(inner));
      return `<h${depth} id="${escapeHtml(id)}">${inner}</h${depth}>\n`;
    },
    code({ text, lang }: Tokens.Code) {
      const language = (lang || '').trim().split(/\s+/)[0];
      if (language && hljs.getLanguage(language)) {
        const highlighted = hljs.highlight(text, { language }).value;
        return `<pre><code class="hljs language-${escapeHtml(language)}">${highlighted}</code></pre>\n`;
      }
      return `<pre><code class="hljs">${escapeHtml(text)}</code></pre>\n`;
    },
    link(this: Renderer, { href, title, tokens }: Tokens.Link) {
      const inner = this.parser.parseInline(tokens);
      const resolved = resolveLink(href, currentSlug);
      const attributes = [`href="${escapeHtml(resolved.href)}"`];
      if (title) attributes.push(`title="${escapeHtml(title)}"`);
      if (resolved.internal) {
        attributes.push(
          'data-manual-link="true"',
          `data-chapter="${escapeHtml(resolved.slug || '')}"`,
          `data-hash="${escapeHtml(resolved.hash || '')}"`
        );
      }
      if (resolved.external) {
        attributes.push('target="_blank"', 'rel="noopener noreferrer"');
      }
      return `<a ${attributes.join(' ')}>${inner}</a>`;
    },
  };
}

export function renderMarkdown(content: string, currentSlug: string) {
  const marked = new Marked({ gfm: true });
  marked.use({ renderer: createRenderer(currentSlug) });
  const html = marked.parse(content) as string;
  return DOMPurify.sanitize(html, {
    ADD_ATTR: ['target', 'rel', 'data-manual-link', 'data-chapter', 'data-hash'],
    ALLOW_DATA_ATTR: true,
  });
}
