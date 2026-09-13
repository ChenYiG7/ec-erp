import { describe, expect, it } from 'vitest'

describe('Math Utils', () => {
  it('should add numbers correctly', () => {
    expect(1 + 2).toBe(3)
  })

  it('should run in node environment', () => {
    expect(typeof window).toBe('undefined')
  })
})
