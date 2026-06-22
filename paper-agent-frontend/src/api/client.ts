import type {
  ChatRequest,
  DiscoveryResult,
  PaperListItem,
  PaperSummary,
  PaperUpdateRequest,
  SemanticSearchRequest,
  SemanticSearchResponse,
  TraceableChatRequest,
  TraceableChatResponse,
  WritingRequest,
  WritingResponse,
} from '../types';

const BASE_URL = '/api';

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
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
  const res = await fetch(`${BASE_URL}/papers/${id}`, { method: 'DELETE' });
  if (!res.ok) throw new Error(`Delete failed: ${res.status}`);
}

export async function getTags(): Promise<string[]> {
  return request<string[]>('/papers/tags');
}

export async function uploadPaper(file: File): Promise<PaperSummary> {
  const formData = new FormData();
  formData.append('file', file);
  const res = await fetch(`${BASE_URL}/papers/upload`, { method: 'POST', body: formData });
  if (!res.ok) throw new Error(`Upload failed: ${res.status}`);
  return res.json();
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

export async function discoverPapers(params: {
  query: string;
  source: 'all' | 'arxiv' | 'semantic-scholar';
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

export function streamChat(
  req: ChatRequest,
  onChunk: (text: string) => void,
  onDone: () => void,
  onError: (err: Error) => void
): AbortController {
  const controller = new AbortController();
  fetch(`${BASE_URL}/chat/stream`, {
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
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        onChunk(decoder.decode(value, { stream: true }));
      }
      onDone();
    })
    .catch((err) => {
      if (err.name !== 'AbortError') onError(err);
    });
  return controller;
}
