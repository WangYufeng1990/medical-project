import js from '@eslint/js'
import tseslint from 'typescript-eslint'
import reactHooks from 'eslint-plugin-react-hooks'
import globals from 'globals'

// The rules below encode the review checklist in CLAUDE.md ("recurring bug
// patterns to flag"), which until now was re-checked by hand every round.
// Deliberately not type-aware (no parserOptions.project): it would need every
// file to be type-clean and roughly triples lint time, and `npm run typecheck`
// already covers the type side.
export default tseslint.config(
  { ignores: ['dist/**', 'node_modules/**'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  reactHooks.configs['recommended-latest'],
  {
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: 'module',
      globals: { ...globals.browser },
    },
    rules: {
      // `catch {}` with nothing surfaced — a failure the user never sees.
      'no-empty': ['error', { allowEmptyCatch: false }],
      '@typescript-eslint/no-unused-vars': ['error', {
        argsIgnorePattern: '^_',
        varsIgnorePattern: '^_',
        caughtErrors: 'all',
        caughtErrorsIgnorePattern: '^_',
      }],
      // `any` hides contract drift; warn rather than fail so it stays visible.
      '@typescript-eslint/no-explicit-any': 'warn',
      // A promise nobody awaits is a request whose failure nobody handles.
      '@typescript-eslint/no-floating-promises': 'off',
      eqeqeq: ['error', 'always', { null: 'ignore' }],
      'no-console': ['warn', { allow: ['warn', 'error'] }],
    },
  },
)
