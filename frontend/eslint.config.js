import js from '@eslint/js'
import jsxA11y from 'eslint-plugin-jsx-a11y'
import reactHooks from 'eslint-plugin-react-hooks'
import globals from 'globals'
import tseslint from 'typescript-eslint'

export default tseslint.config(
  {
    ignores: [
      'dist',
      'public',
      'coverage',
      'playwright-report',
      'test-results',
      'src/lib/api/schema.d.ts',
      'test-results',
    ],
  },
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
      // The shadcn CLI adds these; we use our own cn() and our own theme. See docs/frontend/components.md.
      'no-restricted-imports': ['error', { paths: ['cn', 'next-themes', 'shadcn'] }],
      // The content security policy forbids anything that is not our own origin, so the code must not name one.
      'no-restricted-syntax': [
        'error',
        {
          selector: 'Literal[value=/^(https?:)?\\/\\/(?!localhost)[a-z0-9]/]',
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
    // Every use of <Label> must name its field (htmlFor) or wrap it; the definition itself is generic.
    files: ['src/**/*.{ts,tsx}'],
    rules: {
      'jsx-a11y/label-has-associated-control': [
        'error',
        { labelComponents: ['Label'], controlComponents: ['Input', 'Textarea'], assert: 'either' },
      ],
    },
  },
  {
    files: ['src/components/ui/label.tsx', 'src/components/ui/field.tsx'],
    rules: { 'jsx-a11y/label-has-associated-control': 'off' },
  },
  {
    files: ['scripts/**/*.mjs', 'vite.config.ts', 'playwright.config.ts', 'e2e/**'],
    languageOptions: { globals: globals.node },
  },
  {
    files: ['**/*.test.{ts,tsx}', 'src/test/**'],
    languageOptions: { globals: { ...globals.browser, ...globals.node } },
  },
)
