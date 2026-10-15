import { ApiError } from '../services/api';

/** Human-readable message for anything thrown by an API call or a mutation. */
export function errorMessage(e: unknown): string {
  if (e instanceof ApiError) return e.message;
  if (e instanceof Error) return e.message;
  return 'Something went wrong';
}
