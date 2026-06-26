import { useState } from 'react';
import { semanticSearch } from '../../api/client';
import type { EvidenceChunk } from '../../types';
import { BookOpen, Loader2, Percent, Search } from 'lucide-react';

interface Props {
  onSelectPaper?: (paperId: number) => void;
}

export function SearchPanel({ onSelectPaper }: Props) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<EvidenceChunk[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [hasSearched, setHasSearched] = useState(false);

  const handleSearch = async () => {
    const trimmed = query.trim();
    if (!trimmed || isLoading) return;

    setIsLoading(true);
    setError(null);
    setHasSearched(true);
    try {
      const response = await semanticSearch({ query: trimmed, limit: 8 });
      setResults(response.evidence);
    } catch (err) {
      setError(err instanceof Error ? err.message : '搜索失败');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="surface flex h-full flex-col">
      <div className="border-b border-[var(--color-border)] px-4 py-3">
        <div className="mb-2 flex items-center gap-2">
          <Search size={16} className="text-[var(--color-ink-mute)]" />
          <h2 className="text-sm font-bold text-[var(--color-ink)]">语义搜索</h2>
        </div>
        <p className="mb-3 text-xs text-[var(--color-ink-mute)]">
          在本地文献库中搜索相关内容片段。
        </p>
        <div className="flex gap-2">
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter') void handleSearch();
            }}
            placeholder="输入搜索内容..."
            className="control flex-1 text-sm"
          />
          <button
            type="button"
            onClick={() => void handleSearch()}
            disabled={!query.trim() || isLoading}
            className="primary-button px-3 py-2 text-sm"
          >
            {isLoading ? <Loader2 size={15} className="animate-spin" /> : <Search size={15} />}
          </button>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto p-4">
        {!hasSearched && !isLoading && (
          <div className="flex flex-col items-center gap-2 py-12 text-center text-[var(--color-ink-mute)]">
            <BookOpen size={32} strokeWidth={1.2} className="opacity-45" />
            <p className="text-sm">输入关键词搜索文献库中的相关片段。</p>
          </div>
        )}

        {isLoading && (
          <div className="flex justify-center py-12 text-[var(--color-ink-mute)]">
            <Loader2 size={20} className="animate-spin" />
          </div>
        )}

        {error && (
          <div className="rounded-[var(--radius-sm)] bg-red-50 p-3 text-xs font-semibold text-red-700">
            {error}
          </div>
        )}

        {!isLoading && hasSearched && results.length === 0 && !error && (
          <div className="rounded-[var(--radius-sm)] border border-dashed border-[var(--color-border)] bg-[var(--color-primary-soft)] p-4 text-center text-xs text-[var(--color-ink-mute)]">
            没有找到相关内容。
          </div>
        )}

        {!isLoading && results.length > 0 && (
          <div className="space-y-3">
            {results.map((item) => (
              <button
                key={item.chunkId}
                type="button"
                onClick={() => onSelectPaper?.(item.paperId)}
                className="w-full rounded-[var(--radius-sm)] border border-[var(--color-border)] bg-[var(--surface)] p-3 text-left transition hover:border-[var(--color-border-strong)] hover:bg-[var(--color-primary-soft)]"
              >
                <div className="mb-1.5 flex items-start justify-between gap-2">
                  <span className="line-clamp-1 text-xs font-bold text-[var(--color-ink)]">
                    {item.paperTitle}
                  </span>
                  <span className="inline-flex shrink-0 items-center gap-1 text-xs text-[var(--color-ink-mute)]">
                    <Percent size={11} />
                    {(item.similarity * 100).toFixed(0)}
                  </span>
                </div>
                <p className="line-clamp-4 text-xs leading-5 text-[var(--color-ink-soft)]">
                  {item.content}
                </p>
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
