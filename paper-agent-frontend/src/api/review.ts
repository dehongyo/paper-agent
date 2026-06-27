import type {
  ReviewTemplate,
  ReviewStartRequest,
  ReviewContinueRequest,
  ReviewSessionResponse,
  ReviewStartEvent,
} from '../types';
import { apiUrl } from './base';

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(apiUrl(path), {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!res.ok) {
    const msg = await res.text().catch(() => 'Unknown error');
    throw new Error(`${res.status}: ${msg}`);
  }
  return res.json();
}

export async function listTemplates(): Promise<ReviewTemplate[]> {
  return request<ReviewTemplate[]>('/review/templates');
}

export async function getTemplate(id: number): Promise<ReviewTemplate> {
  return request<ReviewTemplate>(`/review/templates/${id}`);
}

export async function uploadTemplate(file: File): Promise<ReviewTemplate> {
  const formData = new FormData();
  formData.append('file', file);
  const res = await fetch(apiUrl('/review/templates'), { method: 'POST', body: formData });
  if (!res.ok) {
    const msg = await res.text().catch(() => 'Unknown error');
    throw new Error(`${res.status}: ${msg}`);
  }
  return res.json();
}

export async function deleteTemplate(id: number): Promise<void> {
  const res = await fetch(apiUrl(`/review/templates/${id}`), { method: 'DELETE' });
  if (!res.ok) throw new Error(`Delete template failed: ${res.status}`);
}

export function streamReview(
  req: ReviewStartRequest,
  onChunk: (text: string) => void,
  onStatus: (event: ReviewStartEvent) => void,
  onDone: () => void,
  onError: (err: Error) => void
): AbortController {
  const controller = new AbortController();
  fetch(apiUrl('/review/start'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
    signal: controller.signal,
  })
    .then(async (res) => {
      if (!res.ok) {
        const msg = await res.text().catch(() => 'Unknown error');
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

        const events = buffer.split(/\r?\n\r?\n/);
        buffer = events.pop() ?? '';
        for (const rawEvent of events) {
          const dataLines = rawEvent
            .split(/\r?\n/)
            .filter((line) => line.startsWith('data:'))
            .map((line) => line.slice(5).trim());
          if (dataLines.length === 0) continue;

          const data = dataLines.join('\n');
          if (!data) continue;
          const event = JSON.parse(data) as ReviewStartEvent;
          if (event.type === 'status') {
            onStatus(event);
          } else if (event.type === 'text') {
            onChunk(event.content ?? '');
          } else if (event.type === 'done') {
            onDone();
            return;
          } else if (event.type === 'error') {
            onError(new Error(event.message || 'Review failed'));
            return;
          }
        }
      }
      onDone();
    })
    .catch((err) => {
      if (err.name !== 'AbortError') onError(err);
    });
  return controller;
}

export async function continueReview(req: ReviewContinueRequest): Promise<string> {
  const res = await fetch(apiUrl('/review/continue'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  });
  if (!res.ok) throw new Error(`Continue review failed: ${res.status}`);
  return res.text();
}

export async function listReviewSessions(): Promise<ReviewSessionResponse[]> {
  return request<ReviewSessionResponse[]>('/review/sessions');
}

export async function getReviewSession(id: number): Promise<ReviewSessionResponse> {
  return request<ReviewSessionResponse>(`/review/sessions/${id}`);
}

export async function deleteReviewSession(id: number): Promise<void> {
  const res = await fetch(apiUrl(`/review/sessions/${id}`), { method: 'DELETE' });
  if (!res.ok) throw new Error(`Delete session failed: ${res.status}`);
}
