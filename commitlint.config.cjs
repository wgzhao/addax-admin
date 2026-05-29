module.exports = {
  extends: ['@commitlint/config-conventional'],
  rules: {
    'type-enum': [
      2,
      'always',
      ['feat', 'fix', 'refactor', 'perf', 'docs', 'test', 'chore', 'ci'],
    ],
    'scope-enum': [
      2,
      'always',
      ['fe', 'be', 'api', 'db', 'infra', 'repo', 'deps'],
    ],
    'scope-empty': [2, 'never'],
    'subject-case': [1, 'always', ['lower-case']],
    'subject-full-stop': [2, 'never', '.'],
    'header-max-length': [2, 'always', 100],
  },
};