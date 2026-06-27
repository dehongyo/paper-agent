import type {
  ReviewTemplate,
  ReviewStartRequest,
  ReviewContinueRequest,
  ReviewSessionResponse,
} from '../types';

const BASE_URL = window.location.port === '5173'
  ? '/api'
  : 'http://localhost:5173/api';

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!res.ok) {
    const msg = await res.text().catch(() => 'Unknown error');
    throw new Error(`${res.status}: ${msg}`);
  }
  return res.json();
}

// ── Templates ──

export async function listTemplates(): Promise<ReviewTemplate[]> {
  return request<ReviewTemplate[]>('/review/templates');
}

export async function getTemplate(id: number): Promise<ReviewTemplate> {
  return request<ReviewTemplate>(`/review/templates/${id}`);
}

export async function uploadTemplate(file: File): Promise<ReviewTemplate> {
  const formData = new FormData();
  formData.append('file', file);
  const res = await fetch(`${BASE_URL}/review/templates`, { method: 'POST', body: formData });
  if (!res.ok) {
    const msg = await res.text().catch(() => 'Unknown error');
    throw new Error(`${res.status}: ${msg}`);
  }
  return res.json();
}

export async function deleteTemplate(id: number): Promise<void> {
  const res = await fetch(`${BASE_URL}/review/templates/${id}`, { method: 'DELETE' });
  if (!res.ok) throw new Error(`Delete template failed: ${res.status}`);
}

// ── Review Sessions ──

export function streamReview(
  req: ReviewStartRequest,
  onChunk: (text: string) => void,
  onDone: () => void,
  onError: (err: Error) => void
): AbortController {
  const controller = new AbortController();
  fetch(`${BASE_URL}/review/start`, {
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

        // Parse SSE events
        const lines = buffer.split('\n');
        buffer = '';
        for (const line of lines) {
          if (line.startsWith('data:')) {
            const data = line.slice(5).trim();
            if (data) {
              try {
                const event = JSON.parse(data);
                if (event.type === 'text') {
                  onChunk(event.content);
                } else if (event.type === 'done') {
                  onDone();
                  return;
                } else if (event.type === 'error') {
                  onError(new Error(event.message || 'Review failed'));
                  return;
                }
              } catch {
                // Partial chunk — put back in buffer
                buffer = line + '\n';
              }
            }
          } else if (line.trim()) {
            buffer += line + '\n';
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
  const res = await fetch(`${BASE_URL}/review/continue`, {
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
  const res = await fetch(`${BASE_URL}/review/sessions/${id}`, { method: 'DELETE' });
  if (!res.ok) throw new Error(`Delete session failed: ${res.status}`);
}
