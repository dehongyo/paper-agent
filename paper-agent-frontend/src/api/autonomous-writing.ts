import type { AutonomousWritingPhaseEvent, AutonomousWritingSessionResponse } from '../types';

const BASE_URL = window.location.port === '5173'
  ? '/api'
  : 'http://localhost:5173/api';

export function streamAutonomousWriting(
  topic: string,
  onEvent: (event: AutonomousWritingPhaseEvent) => void,
  onError: (err: Error) => void
): AbortController {
  const controller = new AbortController();
  fetch(`${BASE_URL}/writing/autonomous/start`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ topic }),
    signal: controller.signal,
  })
    .then(async (res) => {
      if (!res.ok) {
        const msg = await res.text().catch(() => 'Unknown');
        throw new Error(`${res.status}: ${msg}`);
      }
      const reader = res.body?.getReader();
      if (!reader) throw new Error('No response body');
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });

        const lines = buffer.split('\n');
        buffer = '';
        for (const line of lines) {
          if (line.startsWith('data:')) {
            const data = line.slice(5).trim();
            if (data) {
              try {
                const event = JSON.parse(data) as AutonomousWritingPhaseEvent;
                onEvent(event);
              } catch {
                buffer = line + '\n';
              }
            }
          } else if (line.trim()) {
            buffer += line + '\n';
          }
        }
      }
    })
    .catch((err) => {
      if (err.name !== 'AbortError') onError(err);
    });
  return controller;
}

export async function confirmPhase(
  sessionId: number,
  phase: string,
  extra?: Record<string, any>
): Promise<{ status: string }> {
  const res = await fetch(`${BASE_URL}/writing/autonomous/confirm`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sessionId, phase, ...extra }),
  });
  if (!res.ok) throw new Error(`Confirm failed: ${res.status}`);
  return res.json();
}

export async function listAutonomousSessions(): Promise<AutonomousWritingSessionResponse[]> {
  const res = await fetch(`${BASE_URL}/writing/autonomous/sessions`);
  if (!res.ok) throw new Error(`List sessions failed: ${res.status}`);
  return res.json();
}

export async function cancelAutonomousSession(id: number): Promise<void> {
  await fetch(`${BASE_URL}/writing/autonomous/sessions/${id}/cancel`, { method: 'POST' });
}
