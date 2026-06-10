/**
 * Compatibility barrel for API clients.
 *
 * New code can import domain clients from `@/lib/api/<domain>`, while existing
 * callers can keep using `@/lib/api`.
 */
export * from './api/index';
