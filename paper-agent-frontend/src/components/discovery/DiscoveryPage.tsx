import { useState } from 'react';
import { discoverPapers } from '../../api/client';
import type { DiscoveryResult } from '../../types';
import { ArrowRight, ExternalLink, FileText, Loader2, Search } from 'lucide-react';

type DiscoverySource = 'all' | 'arxiv' | 'semantic-scholar';

const sourceLabel: Record<string, string> = {
  arxiv: 'arXiv',
  'semantic-scholar': 'Semantic Scholar',
};

const quickSearches = [
  { label: 'Retrieval Augmented Generation', query: 'retrieval augmented generation survey' },
  { label: 'Large Language Model', query: 'large language model reasoning' },
  { label: 'Knowledge Graph', query: 'knowledge graph embedding' },
  { label: 'Multi-Agent Systems', query: 'multi-agent reinforcement learning' },
];

export function DiscoveryPage() {
  const [query, setQuery] = useState('');
  const [source, setSource] = useState<DiscoverySource>('all');
  const [results, setResults] = useState<DiscoveryResult[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [hasSearched, setHasSearched] = useState(false);

  const search = async (searchQuery?: string) => {
    const trimmed = (searchQuery ?? query).trim();
    if (!trimmed || isLoading) return;

    if (searchQuery) setQuery(searchQuery);
    setIsLoading(true);
    setError(null);
    setHasSearched(true);
    try {
      setResults(await discoverPapers({ query: trimmed, source, limit: 10 }));
    } catch (err) {
      setError(err instanceof Error ? err.message : '检索失败');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="page-shell">
      <div className="page-content-narrow">
        <header className="mb-7 text-center">
          <div>
            <p className="eyebrow">DISCOVERY</p>
            <h1 className="page-title">论文检索</h1>
            <p className="page-subtitle mx-auto">
              检索 arXiv 和 Semantic Scholar 的外部论文，获取标题、摘要和直达链接。
            </p>
          </div>
        </header>

        <div className="surface mb-6 grid gap-3 p-3 lg:grid-cols-[1fr_200px_auto]">
          <label className="relative">
            <Search size={16} className="search-icon" />
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') void search();
              }}
              placeholder="输入关键词搜索论文…"
              className="control search-control text-sm"
            />
          </label>
          <select
            value={source}
            onChange={(event) => setSource(event.target.value as DiscoverySource)}
            className="control px-3 text-sm"
          >
            <option value="all">全部来源</option>
            <option value="arxiv">arXiv</option>
            <option value="semantic-scholar">Semantic Scholar</option>
          </select>
          <button type="button" onClick={() => void search()} disabled={!query.trim() || isLoading} className="primary-button">
            {isLoading ? <Loader2 size={16} className="animate-spin" /> : <Search size={16} />}
            检索
          </button>
        </div>

        {!hasSearched && !isLoading && (
          <div className="surface flex flex-col items-center py-14 pb-[72px] text-center px-8">
            <Search size={40} strokeWidth={1.2} className="mb-4 text-[var(--color-ink-mute)] opacity-45" />
            <p className="text-lg font-bold text-[var(--color-ink)]">探索外部论文</p>
            <p className="mt-2 max-w-md text-sm leading-6 text-[var(--color-ink-mute)]">
              输入关键词检索 arXiv 和 Semantic Scholar，或从热门方向开始：
            </p>
            <div className="mt-6 grid w-full max-w-lg gap-2.5 sm:grid-cols-2">
              {quickSearches.map((item) => (
                <button
                  key={item.label}
                  type="button"
                  onClick={() => void search(item.query)}
                  className="quick-chip justify-between"
                >
                  <span className="truncate">{item.label}</span>
                  <ArrowRight size={13} className="shrink-0" />
                </button>
              ))}
            </div>
          </div>
        )}

        {error && (
          <div className="surface-solid mb-5 p-4 text-sm font-semibold text-red-600">
            {error}
            <button onClick={() => void search()} className="ml-3 underline">
              重试
            </button>
          </div>
        )}

        {isLoading && (
          <div className="flex justify-center py-16 text-[var(--color-ink-mute)]">
            <Loader2 size={24} className="animate-spin" />
          </div>
        )}

        {!isLoading && hasSearched && results.length === 0 && !error && (
          <div className="surface flex flex-col items-center gap-3 py-16 text-[var(--color-ink-mute)]">
            <FileText size={48} strokeWidth={1.2} />
            <p className="font-bold text-[var(--color-ink)]">没有检索到结果</p>
            <p className="text-sm">换一个关键词或来源再试。</p>
          </div>
        )}

        {!isLoading && results.length > 0 && (
          <div className="grid gap-4 lg:grid-cols-2">
            {results.map((paper) => (
              <article key={`${paper.source}-${paper.externalId}`} className="surface flex min-h-[240px] flex-col p-5 transition hover:-translate-y-0.5 hover:shadow-[var(--shadow-md)]">
                <div className="mb-3 flex items-start justify-between gap-3">
                  <h2 className="line-clamp-2 text-[16px] font-extrabold leading-snug tracking-tight text-[var(--color-ink)]">{paper.title}</h2>
                  <span className="pill shrink-0 bg-[var(--color-primary-soft)] text-[var(--color-ink-soft)]">
                    {sourceLabel[paper.source] ?? paper.source}
                  </span>
                </div>

                <p className="line-clamp-2 text-xs text-[var(--color-ink-mute)]">
                  {paper.authors.length > 0 ? paper.authors.join(', ') : '作者未记录'}
                  {paper.year ? ` · ${paper.year}` : ''}
                </p>

                <p className="mt-3 line-clamp-4 text-sm leading-6 text-[var(--color-ink-soft)]">
                  {paper.abstractText || '暂无摘要。'}
                </p>

                {(paper.doi || paper.externalId) && (
                  <p className="mt-2 line-clamp-1 text-xs text-[var(--color-ink-mute)] font-mono">
                    {paper.doi ? `DOI: ${paper.doi}` : `ID: ${paper.externalId}`}
                  </p>
                )}

                <div className="mt-auto flex flex-wrap gap-2 pt-4">
                  {paper.landingUrl && (
                    <a href={paper.landingUrl} target="_blank" rel="noreferrer" className="secondary-button min-h-[36px] px-3 text-xs">
                      <ExternalLink size={13} />
                      打开页面
                    </a>
                  )}
                  {paper.pdfUrl && (
                    <a href={paper.pdfUrl} target="_blank" rel="noreferrer" className="secondary-button min-h-[36px] px-3 text-xs">
                      <FileText size={13} />
                      PDF 链接
                    </a>
                  )}
                </div>
              </article>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
