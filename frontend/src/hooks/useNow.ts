import { useEffect, useState } from 'react';

/** Current time in ms, re-rendering every {@code intervalMs} (for countdowns and "x min ago"). */
export function useNow(intervalMs = 30_000): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), intervalMs);
    return () => clearInterval(id);
  }, [intervalMs]);
  return now;
}
