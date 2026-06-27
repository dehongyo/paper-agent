import type {
  ChatRequest,
  ChatMessageResponse,
  ChatSession,
  ChatSessionCreateRequest,
  DiscoveryResult,
  PaperListItem,
  PaperStats,
  PaperSummary,
  PaperUpdateRequest,
  ReferenceItem,
  SemanticSearchRequest,
  SemanticSearchResponse,
  TraceableChatRequest,
  TraceableChatResponse,
  TraceableChatStreamEvent,
  WritingRequest,
  WritingResponse,
  WritingVersionFull,
  WritingVersionResponse,
} from '../types';
import { apiUrl } from './base';

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(apiUrl(path), {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!res.ok) throw new Error(`API Error: ${res.status}`);
  return res.json();
}

export async function getPapers(filters?: {
  query?: string;
  status?: string;
  tag?: string;
}): Promise<PaperListItem[]> {
  const params = new URLSearchParams();
  if (filters?.query) params.set('query', filters.query);
  if (filters?.status) params.set('status', filters.status);
  if (filters?.tag) params.set('tag', filters.tag);
  const query = params.toString();
  return request<PaperListItem[]>(`/papers${query ? `?${query}` : ''}`);
}

export async function getPaper(id: number): Promise<PaperSummary> {
  return request<PaperSummary>(`/papers/${id}`);
}

export async function updatePaper(id: number, payload: PaperUpdateRequest): Promise<PaperSummary> {
  return request<PaperSummary>(`/papers/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  });
}

export async function deletePaper(id: number): Promise<void> {
  const res = await fetch(apiUrl(`/papers/${id}`), { method: 'DELETE' });
  if (!res.ok) throw new Error(`Delete failed: ${res.status}`);
}

export async function getFullText(id: number): Promise<string> {
  const res = await fetch(apiUrl(`/papers/${id}/fulltext`));
  if (!res.ok) throw new Error(`Get fulltext failed: ${res.status}`);
  return res.text();
}

export async function getPaperStatus(id: number): Promise<{ status: string }> {
  const res = await fetch(apiUrl(`/papers/${id}/status`));
  if (!res.ok) throw new Error(`Status fetch failed: ${res.status}`);
  return res.json();
}

export async function getTags(): Promise<string[]> {
  return request<string[]>('/papers/tags');
}

export async function uploadPaper(file: File): Promise<PaperSummary> {
  const formData = new FormData();
  formData.append('file', file);
  const res = await fetch(apiUrl('/papers/upload'), { method: 'POST', body: formData });
  if (!res.ok) throw new Error(`Upload failed: ${res.status}`);
  return res.json();
}

export async function importPaper(urlOrDoi: string): Promise<PaperSummary> {
  return request<PaperSummary>('/papers/import', {
    method: 'POST',
    body: JSON.stringify({ urlOrDoi }),
  });
}

export async function batchImport(urlsOrDois: string[]): Promise<PaperSummary[]> {
  return request<PaperSummary[]>('/papers/import/batch', {
    method: 'POST',
    body: JSON.stringify({ urlsOrDois }),
  });
}

export async function getPaperStats(): Promise<PaperStats> {
  return request<PaperStats>('/papers/stats');
}

export async function semanticSearch(req: SemanticSearchRequest): Promise<SemanticSearchResponse> {
  return request<SemanticSearchResponse>('/search/semantic', {
    method: 'POST',
    body: JSON.stringify(req),
  });
}

export async function traceableChat(req: TraceableChatRequest): Promise<TraceableChatResponse> {
  return request<TraceableChatResponse>('/chat/rag', {
    method: 'POST',
    body: JSON.stringify(req),
  });
}

export async function getChatSessions(params: {
  scope: 'paper' | 'library';
  paperId?: number | null;
}): Promise<ChatSession[]> {
  const search = new URLSearchParams();
  search.set('scope', params.scope);
  if (params.paperId != null) search.set('paperId', String(params.paperId));
  return request<ChatSession[]>(`/chat/sessions?${search.toString()}`);
}

export async function createChatSession(req: ChatSessionCreateRequest): Promise<ChatSession> {
  return request<ChatSession>('/chat/sessions', {
    method: 'POST',
    body: JSON.stringify(req),
  });
}

export async function getChatMessages(sessionId: number): Promise<ChatMessageResponse[]> {
  return request<ChatMessageResponse[]>(`/chat/sessions/${sessionId}/messages`);
}

export async function deleteChatSession(sessionId: number): Promise<void> {
  const res = await fetch(apiUrl(`/chat/sessions/${sessionId}`), { method: 'DELETE' });
  if (!res.ok) throw new Error(`Delete session failed: ${res.status}`);
}

export async function discoverPapers(params: {
  query: string;
  source: 'all' | 'arxiv' | 'semantic-scholar' | 'pubmed' | 'dblp';
  limit?: number;
}): Promise<DiscoveryResult[]> {
  const search = new URLSearchParams();
  search.set('query', params.query);
  search.set('source', params.source);
  search.set('limit', String(params.limit ?? 10));
  return request<DiscoveryResult[]>(`/discovery/search?${search.toString()}`);
}

export async function generateOutline(req: WritingRequest): Promise<WritingResponse> {
  return request<WritingResponse>('/writing/outline', {
    method: 'POST',
    body: JSON.stringify(req),
  });
}

export async function generateDraft(req: WritingRequest): Promise<WritingResponse> {
  return request<WritingResponse>('/writing/draft', {
    method: 'POST',
    body: JSON.stringify(req),
  });
}

export async function exportWriting(req: WritingRequest): Promise<WritingResponse> {
  return request<WritingResponse>('/writing/export', {
    method: 'POST',
    body: JSON.stringify(req),
  });
}

export async function saveWritingVersion(data: { topic: string; outline: string; draft: string; references: ReferenceItem[] }): Promise<WritingVersionResponse> {
  return request<WritingVersionResponse>('/writing/versions', { method: 'POST', body: JSON.stringify(data) });
}

export async function getWritingVersions(): Promise<WritingVersionResponse[]> {
  return request<WritingVersionResponse[]>('/writing/versions');
}

export async function getWritingVersion(id: number): Promise<WritingVersionFull> {
  return request<WritingVersionFull>(`/writing/versions/${id}`);
}

export async function deleteWritingVersion(id: number): Promise<void> {
  await fetch(apiUrl(`/writing/versions/${id}`), { method: 'DELETE' });
}

export function streamChat(
  req: ChatRequest,
  onChunk: (text: string) => void,
  onDone: () => void,
  onError: (err: Error) => void
): AbortController {
  const controller = new AbortController();
  fetch(apiUrl('/chat/stream'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
    signal: controller.signal,
  })
    .then(async (res) => {
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const reader = res.body?.getReader();
      if (!reader) throw new Error('No response body');
      const decoder = new TextDecoder();
      let eventBuffer = '';
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        const text = decoder.decode(value, { stream: true });
        const shouldParseAsSse =
          eventBuffer.length > 0 || text.includes('data:') || text.includes('\n\n') || text.includes('\r\n\r\n');
        if (shouldParseAsSse) {
          eventBuffer = readSseEvents(eventBuffer + text, onChunk);
        } else {
          onChunk(text);
        }
      }
      const trailing = decoder.decode();
      if (trailing) eventBuffer = readSseEvents(eventBuffer + trailing, onChunk);
      flushSseEvents(eventBuffer, onChunk);
      onDone();
    })
    .catch((err) => {
      if (err.name !== 'AbortError') onError(err);
    });
  return controller;
}

export function streamTraceableChat(
  req: TraceableChatRequest,
  onChunk: (text: string) => void,
  onEvidence: (evidence: TraceableChatStreamEvent['evidence']) => void,
  onDone: () => void,
  onError: (err: Error) => void
): AbortController {
  const controller = new AbortController();
  fetch(apiUrl('/chat/rag/stream'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
    signal: controller.signal,
  })
    .then(async (res) => {
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const reader = res.body?.getReader();
      if (!reader) throw new Error('No response body');
      const decoder = new TextDecoder();
      let eventBuffer = '';
      let completed = false;
      const finish = () => {
        if (completed) return;
        completed = true;
        onDone();
      };
      const handleEvent = (data: string) => {
        if (!data || data === '[DONE]') return;
        const event = JSON.parse(data) as TraceableChatStreamEvent;
        if (event.type === 'answer') onChunk(event.content);
        if (event.type === 'evidence') onEvidence(event.evidence);
        if (event.type === 'done') finish();
      };

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        eventBuffer = readSseEvents(eventBuffer + decoder.decode(value, { stream: true }), handleEvent);
      }
      const trailing = decoder.decode();
      if (trailing) eventBuffer = readSseEvents(eventBuffer + trailing, handleEvent);
      flushSseEvents(eventBuffer, handleEvent);
      finish();
    })
    .catch((err) => {
      if (err.name !== 'AbortError') onError(err);
    });
  return controller;
}

function readSseEvents(buffer: string, onEvent: (data: string) => void): string {
  let remaining = buffer;
  let boundary = findSseBoundary(remaining);
  while (boundary) {
    const rawEvent = remaining.slice(0, boundary.index);
    remaining = remaining.slice(boundary.index + boundary.length);
    emitSseEvent(rawEvent, onEvent);
    boundary = findSseBoundary(remaining);
  }
  return remaining;
}

function flushSseEvents(buffer: string, onEvent: (data: string) => void) {
  if (buffer.trim()) emitSseEvent(buffer, onEvent);
}

function emitSseEvent(rawEvent: string, onEvent: (data: string) => void) {
  const lines = rawEvent.split(/\r?\n/);
  const dataLines = lines.filter((line) => line.startsWith('data:'));
  if (dataLines.length === 0) {
    const text = rawEvent.trim();
    if (text) onEvent(text);
    return;
  }

  const data = dataLines
    .map((line) => line.slice(5).replace(/^ /, ''))
    .join('\n');
  if (data) onEvent(data);
}

function findSseBoundary(buffer: string): { index: number; length: number } | null {
  const lf = buffer.indexOf('\n\n');
  const crlf = buffer.indexOf('\r\n\r\n');
  if (lf === -1 && crlf === -1) return null;
  if (lf === -1) return { index: crlf, length: 4 };
  if (crlf === -1) return { index: lf, length: 2 };
  return lf < crlf ? { index: lf, length: 2 } : { index: crlf, length: 4 };
}
