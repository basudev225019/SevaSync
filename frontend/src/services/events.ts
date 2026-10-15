import type { LiveEvent } from '../types';

export type ConnectionState = 'connecting' | 'live' | 'reconnecting' | 'offline';

interface Options {
  token: string;
  onEvent: (event: LiveEvent) => void;
  onState: (state: ConnectionState) => void;
}

/**
 * Server-Sent Events over fetch (not EventSource), so the bearer token travels in a header instead of the URL.
 * Reconnects with exponential back-off and resumes from the last event id, so nothing is missed.
 * Returns a function that closes the stream.
 */
export function connectEvents({ token, onEvent, onState }: Options): () => void {
  let closed = false;
  let lastId = 0;
  let attempt = 0;
  let controller: AbortController | null = null;
  let timer: ReturnType<typeof setTimeout> | null = null;

  const run = async () => {
    if (closed) return;
    controller = new AbortController();
    onState(attempt === 0 ? 'connecting' : 'reconnecting');
    try {
      const headers: Record<string, string> = { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' };
      if (lastId > 0) headers['Last-Event-ID'] = String(lastId);
      const res = await fetch('/api/events', { headers, signal: controller.signal });
      if (res.status === 401) {
        onState('offline');
        return; // the session ended; the auth layer handles signing out
      }
      if (!res.ok || !res.body) throw new Error(`stream refused (${res.status})`);
      attempt = 0;
      onState('live');

      const reader = res.body.pipeThrough(new TextDecoderStream()).getReader();
      let buffer = '';
      for (;;) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += value;
        let sep: number;
        while ((sep = buffer.indexOf('\n\n')) >= 0) {
          const block = buffer.slice(0, sep);
          buffer = buffer.slice(sep + 2);
          handleBlock(block);
        }
      }
      throw new Error('stream ended');
    } catch {
      if (closed) return;
      attempt++;
      onState('reconnecting');
      const delay = Math.min(15_000, 600 * 2 ** Math.min(attempt, 5)) + Math.random() * 400;
      timer = setTimeout(run, delay);
    }
  };

  const handleBlock = (block: string) => {
    let type = 'message';
    let id = 0;
    const data: string[] = [];
    for (const line of block.split('\n')) {
      if (line.startsWith(':')) continue; // heartbeat comment
      const colon = line.indexOf(':');
      const field = colon < 0 ? line : line.slice(0, colon);
      const value = colon < 0 ? '' : line.slice(colon + 1).replace(/^ /, '');
      if (field === 'event') type = value;
      else if (field === 'id') id = Number(value) || 0;
      else if (field === 'data') data.push(value);
    }
    if (id > lastId) lastId = id;
    if (type === 'ready' || data.length === 0) return;
    try {
      const parsed = JSON.parse(data.join('\n')) as LiveEvent;
      onEvent(parsed);
    } catch {
      // ignore malformed frames
    }
  };

  run();

  return () => {
    closed = true;
    if (timer) clearTimeout(timer);
    controller?.abort();
    onState('offline');
  };
}
