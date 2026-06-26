export interface PaperListItem {
  id: number;
  title: string;
  authors: string | null;
  filename: string;
  status: string;
  summary: string | null;
  tags: string[];
  doi: string | null;
  sourceUrl: string | null;
  publishedAt: string | null;
  createdAt: string;
}

export interface PaperSummary extends PaperListItem {
  pageCount: number | null;
  notes: string | null;
}

export interface PaperUpdateRequest {
  title: string;
  authors: string | null;
  doi: string | null;
  sourceUrl: string | null;
  publishedAt: string | null;
  summary: string | null;
  notes: string | null;
  tags: string[];
}

export interface EvidenceChunk {
  chunkId: number;
  paperId: number;
  paperTitle: string;
  chunkIndex: number;
  content: string;
  similarity: number;
}

export interface SemanticSearchRequest {
  query: string;
  paperId?: number | null;
  limit?: number;
}

export interface SemanticSearchResponse {
  query: string;
  paperId: number | null;
  evidence: EvidenceChunk[];
}

export interface TraceableChatRequest {
  message: string;
  paperId?: number | null;
  scope: 'paper' | 'library';
  sessionId?: number | null;
}

export interface TraceableChatResponse {
  answer: string;
  evidence: EvidenceChunk[];
}

export interface TraceableChatStreamEvent {
  type: 'answer' | 'evidence' | 'done';
  content: string;
  evidence: EvidenceChunk[];
}

export interface DiscoveryResult {
  externalId: string;
  source: string;
  title: string;
  authors: string[];
  year: string | null;
  abstractText: string | null;
  landingUrl: string | null;
  pdfUrl: string | null;
  doi: string | null;
}

export interface ChatRequest {
  message: string;
  paperId?: number | null;
  sessionId?: number | null;
}

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: number;
  isStreaming?: boolean;
  evidence?: EvidenceChunk[];
}

export interface ChatSession {
  id: number;
  title: string;
  scope: 'paper' | 'library';
  paperId: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface ChatSessionCreateRequest {
  title?: string | null;
  scope: 'paper' | 'library';
  paperId?: number | null;
}

export interface ChatMessageResponse {
  id: number;
  role: 'user' | 'assistant';
  content: string;
  evidenceJson: string | null;
  messageOrder: number;
  createdAt: string;
}

export type CitationStyle = 'gbt7714' | 'apa' | 'ieee';

export type WritingLength = 'short' | 'standard' | 'detailed';

export type WritingType = 'literature-review' | 'research-background';

export interface WritingRequest {
  topic: string;
  paperIds?: number[];
  scope: 'library' | 'selected';
  writingType: WritingType;
  language: 'zh' | 'en';
  length: WritingLength;
  citationStyle: CitationStyle;
  outline?: string | null;
  draft?: string | null;
}

export interface WritingEvidence {
  index: number;
  chunkId: number;
  paperId: number;
  paperTitle: string;
  chunkIndex: number;
  content: string;
  similarity: number;
}

export interface ReferenceItem {
  index: number;
  paperId: number;
  title: string;
  authors: string;
  year: string;
  doi: string | null;
  sourceUrl: string | null;
  formatted: string;
}

export interface WritingResponse {
  topic: string;
  outline: string;
  draft: string;
  references: ReferenceItem[];
  evidence: WritingEvidence[];
  exportMarkdown: string;
  exportLatex: string;
}

export interface WritingVersionResponse {
  id: number;
  topic: string;
  versionNumber: number;
  createdAt: string;
}

export interface WritingVersionFull extends WritingVersionResponse {
  outline: string;
  draft: string;
  references: ReferenceItem[];
}

export interface PaperStats {
  totalPapers: number;
  readyPapers: number;
  processingPapers: number;
  errorPapers: number;
  totalTags: number;
  uniqueTags: number;
  totalChunks: number;
  totalChatSessions: number;
  totalChatMessages: number;
}
