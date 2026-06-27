import { useCallback, useEffect, useState } from 'react';
import { getPapers } from '../../api/client';
import type { PaperListItem } from '../../types';
import { AlertCircle, Check, Loader2, RefreshCw } from 'lucide-react';

interface Props {
  scope: 'library' | 'selected';
  selectedPaperIds: number[];
  onScopeChange: (scope: 'library' | 'selected') => void;
  onSelectionChange: (paperIds: number[]) => void;
}

const statusLabel: Record<string, string> = {
  UPLOADED: '上传中',
  PARSING: '解析中',
  PARSED: '已解析',
  EMBEDDING: '向量化',
  READY: '就绪',
  ERROR: '错误',
};

export function PaperPicker({ scope, selectedPaperIds, onScopeChange, onSelectionChange }: Props) {
  const [papers, setPapers] = useState<PaperListItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadPapers = useCallback(() => {
    setIsLoading(true);
    setError(null);
    getPapers()
      .then((items) => {
        setPapers(items);
      })
      .catch((err) => {
        setError(err instanceof Error ? err.message : '加载文献列表失败');
      })
      .finally(() => {
        setIsLoading(false);
      });
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(loadPapers, 0);
    return () => window.clearTimeout(timer);
  }, [loadPapers]);

  const readyPapers = papers.filter((p) => p.status === 'READY');
  const nonReadyPapers = papers.filter((p) => p.status !== 'READY');

  const togglePaper = (paperId: number) => {
    // Only allow toggling READY papers
    const paper = papers.find((p) => p.id === paperId);
    if (!paper || paper.status !== 'READY') return;

    const next = selectedPaperIds.includes(paperId)
      ? selectedPaperIds.filter((id) => id !== paperId)
      : [...selectedPaperIds, paperId];
    onSelectionChange(next);
    if (next.length > 0) onScopeChange('selected');
  };

  return (
    <section className="surface">
      <div className="mb-3 flex items-center justify-between gap-3">
        <div>
          <h2 className="text-sm font-bold text-[var(--color-ink)]">文献范围</h2>
          <p className="mt-1 text-xs leading-5 text-[var(--color-ink-mute)]">
            {readyPapers.length > 0
              ? `共 ${readyPapers.length} 篇就绪文献可供写作参考`
              : '选择写作时可使用的文献范围'}
          </p>
        </div>
        {isLoading && <Loader2 size={16} className="animate-spin text-[var(--color-ink-mute)]" />}
        {!isLoading && error && (
          <button onClick={loadPapers} className="icon-button h-7 w-7" aria-label="重试" title="重试加载">
            <RefreshCw size={14} />
          </button>
        )}
      </div>

      {/* Scope switcher */}
      <div className="mb-3 grid grid-cols-2 gap-2">
        <button
          type="button"
          onClick={() => onScopeChange('library')}
          className={`rounded-lg border px-3 py-2 text-sm font-semibold transition ${
            scope === 'library'
              ? 'border-[var(--color-primary)] bg-[var(--color-primary-soft)] text-[var(--color-primary)]'
              : 'border-[var(--color-border)] bg-[var(--surface)] text-[var(--color-ink-soft)]'
          }`}
        >
          全部文献
        </button>
        <button
          type="button"
          onClick={() => onScopeChange('selected')}
          className={`rounded-lg border px-3 py-2 text-sm font-semibold transition ${
            scope === 'selected'
              ? 'border-[var(--color-primary)] bg-[var(--color-primary-soft)] text-[var(--color-primary)]'
              : 'border-[var(--color-border)] bg-[var(--surface)] text-[var(--color-ink-soft)]'
          }`}
        >
          手动选择
        </button>
      </div>

      {/* Error state */}
      {error && (
        <div className="mb-3 flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 p-3 text-xs text-red-700">
          <AlertCircle size={14} className="mt-0.5 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      <div className="max-h-72 space-y-2 overflow-y-auto pr-1">
        {/* Empty state */}
        {!isLoading && !error && papers.length === 0 && (
          <div className="writing-empty-state rounded-lg border border-[var(--color-border)] bg-[var(--color-primary-soft)] p-4 text-center">
            <p className="text-sm font-semibold text-[var(--color-ink)]">暂无文献</p>
            <p className="mt-1 text-xs text-[var(--color-ink-mute)]">
              请先在文献库中上传论文，等待处理完成后即可用于写作。
            </p>
          </div>
        )}

        {/* Ready papers — selectable */}
        {readyPapers.map((paper) => {
          const selected = scope === 'library' || selectedPaperIds.includes(paper.id);
          return (
            <button
              key={paper.id}
              type="button"
              onClick={() => togglePaper(paper.id)}
              className="flex w-full items-start gap-3 rounded-lg border border-[var(--color-border)] bg-[var(--surface)] p-3 text-left transition hover:border-[var(--color-border-strong)] hover:bg-[var(--color-primary-soft)]"
            >
              <span
                className={`mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-md border ${
                  selected
                    ? 'border-[var(--color-primary)] bg-[var(--color-primary)] text-white'
                    : 'border-[var(--color-border-strong)]'
                }`}
              >
                {selected && <Check size={13} />}
              </span>
              <span className="min-w-0 flex-1">
                <span className="line-clamp-2 text-sm font-semibold leading-5 text-[var(--color-ink)]">
                  {paper.title}
                </span>
                <span className="mt-1 block line-clamp-1 text-xs text-[var(--color-ink-mute)]">
                  {paper.authors || paper.filename}
                </span>
              </span>
            </button>
          );
        })}

        {/* Non-ready papers — greyed out, not selectable */}
        {nonReadyPapers.map((paper) => {
          const st = statusLabel[paper.status] || paper.status;
          return (
            <div
              key={paper.id}
              className="flex w-full items-start gap-3 rounded-lg border border-[var(--color-border)] bg-[var(--surface)] p-3 text-left opacity-50 cursor-not-allowed"
              title={`状态：${st}，处理完成后即可用于写作`}
            >
              <span className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-md border border-[var(--color-border)]">
                <Loader2 size={12} className="animate-spin text-[var(--color-ink-mute)]" />
              </span>
              <span className="min-w-0 flex-1">
                <span className="line-clamp-2 text-sm font-semibold leading-5 text-[var(--color-ink)]">
                  {paper.title}
                </span>
                <span className="mt-1 flex items-center gap-2">
                  <span className="line-clamp-1 text-xs text-[var(--color-ink-mute)]">
                    {paper.authors || paper.filename}
                  </span>
                  <span className="shrink-0 rounded-full bg-[var(--color-primary-soft)] px-2 py-0.5 text-[10px] font-semibold text-[var(--color-ink-mute)]">
                    {st}
                  </span>
                </span>
              </span>
            </div>
          );
        })}
      </div>
    </section>
  );
}
