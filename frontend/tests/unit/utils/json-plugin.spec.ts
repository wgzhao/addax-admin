import { describe, expect, it } from 'vitest';
import { jsonObjectOrEmpty, parseJsonObjectOrNull, toJsonText } from '@/utils/json-plugin';

describe('toJsonText', () => {
  it('returns empty string for empty values', () => {
    expect(toJsonText(null)).toBe('');
    expect(toJsonText(undefined)).toBe('');
    expect(toJsonText('')).toBe('');
  });

  it('pretty-prints stored JSON strings and objects', () => {
    expect(toJsonText('{"a":1}')).toBe(JSON.stringify({ a: 1 }, null, 2));
    expect(toJsonText({ a: 1 })).toBe(JSON.stringify({ a: 1 }, null, 2));
  });

  it('passes through unparseable strings unchanged', () => {
    expect(toJsonText('not json')).toBe('not json');
  });
});

describe('parseJsonObjectOrNull', () => {
  it('returns null for blank input', () => {
    expect(parseJsonObjectOrNull('  ', 'x')).toBeNull();
  });

  it('parses a JSON object', () => {
    expect(parseJsonObjectOrNull('{"a":1}', 'x')).toEqual({ a: 1 });
  });

  it('rejects invalid JSON', () => {
    expect(() => parseJsonObjectOrNull('nope', '读取插件配置')).toThrow('读取插件配置 不是合法 JSON');
  });

  it('rejects arrays and primitives', () => {
    expect(() => parseJsonObjectOrNull('[1]', 'x')).toThrow('x 必须为 JSON 对象');
    expect(() => parseJsonObjectOrNull('"s"', 'x')).toThrow('x 必须为 JSON 对象');
  });
});

describe('jsonObjectOrEmpty', () => {
  it('accepts empty values', () => {
    expect(jsonObjectOrEmpty('')).toBe(true);
    expect(jsonObjectOrEmpty(null)).toBe(true);
  });

  it('accepts JSON objects and rejects everything else', () => {
    expect(jsonObjectOrEmpty('{"a":1}')).toBe(true);
    expect(jsonObjectOrEmpty('nope')).toBe('请输入合法 JSON');
    expect(jsonObjectOrEmpty('[1]')).toBe('必须为 JSON 对象');
    expect(jsonObjectOrEmpty('42')).toBe('必须为 JSON 对象');
  });
});
