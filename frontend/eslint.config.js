import js from '@eslint/js'
import jsxA11y from 'eslint-plugin-jsx-a11y'
import reactHooks from 'eslint-plugin-react-hooks'
import globals from 'globals'
import tseslint from 'typescript-eslint'

export default tseslint.config(
  { ignores: ['dist', 'public', 'coverage', 'playwright-report', 'test-results', 'src/lib/api/schema.d.ts'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  jsxA11y.flatConfigs.recommended,
  {
    files: ['**/*.{ts,tsx}'],
    languageOptions: { ecmaVersion: 2023, globals: globals.browser },
    plugins: { 'react-hooks': reactHooks },
    rules: {
      ...reactHooks.configs.recommended.rules,
      '@typescript-eslint/consistent-type-imports': 'error',
      // The content security policy forbids anything that is not our own origin, so the code must not name one.
      'no-restricted-syntax': [
        'error',
        {
          selector: 'Literal[value=/^(https?:)?\\/\\/(?!localhost)/]',
          message: 'No external URL in the code: the content security policy only allows our own origin.',
        },
      ],
    },
  },
  {
    // Layering, same spirit as the backend: components only draw, they never call the API.
    files: ['src/components/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['@/lib/api', '@/lib/api/*', '@/features/*', '@/routes/*'],
              message: 'A component must not reach the API, a feature or a route.',
            },
          ],
        },
      ],
    },
  },
  {
    files: ['src/lib/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            { group: ['@/routes/*', '@/features/*', '@/components/*'], message: 'lib is the lowest layer.' },
          ],
        },
      ],
    },
  },
  {
    files: ['**/*.test.{ts,tsx}', 'src/test/**'],
    languageOptions: { globals: { ...globals.browser, ...globals.node } },
  },
)
