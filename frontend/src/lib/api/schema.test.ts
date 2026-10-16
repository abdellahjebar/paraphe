// @vitest-environment node
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import openapiTS, { astToString } from 'openapi-typescript'
import { ERROR_MESSAGES, FIELD_MESSAGES, PASSWORD_REASON_MESSAGES } from '@/lib/messages.fr'

const CONTRACT = new URL('../../../../docs/api/openapi.json', import.meta.url)
const GENERATED = new URL('./schema.d.ts', import.meta.url)

/** The generated file starts with a banner that the library's own function does not produce. */
function withoutBanner(text: string): string {
  return text.replace(/^\/\*\*[\s\S]*?\*\/\s*/, '').trim()
}

describe('API contract', () => {
  it('has a client that is up to date (run "npm run api:generate" if this fails)', async () => {
    const fresh = astToString(await openapiTS(CONTRACT))
    const committed = readFileSync(fileURLToPath(GENERATED), 'utf8')
    expect(withoutBanner(committed)).toBe(fresh.trim())
  })

  interface EnumSchema {
    enum: string[]
  }
  interface Contract {
    components: {
      schemas: {
        Problem: {
          properties: {
            code: EnumSchema
            reason: EnumSchema
            errors: { items: { properties: { code: EnumSchema } } }
          }
        }
      }
    }
  }
  const contract = JSON.parse(readFileSync(fileURLToPath(CONTRACT), 'utf8')) as Contract
  const problem = contract.components.schemas.Problem.properties

  it('has a French message for every error code of the contract, and none left over', () => {
    const codes: string[] = problem.code.enum
    const messaged = Object.keys(ERROR_MESSAGES).filter(
      (c) => c !== 'NETWORK_ERROR' && c !== 'UNEXPECTED_RESPONSE',
    )
    expect(messaged.sort()).toEqual([...codes].sort())
  })

  it('has a French message for every field error code of the contract, and none left over', () => {
    const codes: string[] = problem.errors.items.properties.code.enum
    expect(Object.keys(FIELD_MESSAGES).sort()).toEqual([...codes].sort())
  })

  it('has a French message for every password reason of the contract, and none left over', () => {
    const reasons: string[] = problem.reason.enum
    expect(Object.keys(PASSWORD_REASON_MESSAGES).sort()).toEqual([...reasons].sort())
  })
})
