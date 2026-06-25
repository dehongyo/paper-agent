import { useEffect, useState } from 'react';
import { getPapers } from '../../api/client';
import type { PaperListItem } from '../../types';
import { Check, Loader2 } from 'lucide-react';

interface Props {
  scope: 'library' | 'selected';
  selectedPaperIds: number[];
  onScopeChange: (scope: 'library' | 'selected') => void;
  onSelectionChange: (paperIds: number[]) => void;
}

export function PaperPicker({ scope, selectedPaperIds, onScopeChange, onSelectionChange }: Props) {
  const [papers, setPapers] = useState<PaperListItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    getPapers({ status: 'READY' })
      .then((items) => {
        if (!cancelled) setPapers(items);
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const togglePaper = (paperId: number) => {
    const next = selectedPaperIds.includes(paperId)
      ? selectedPaperIds.filter((id) => id !== paperId)
      : [...selectedPaperIds, paperId];
    onSelectionChange(next);
    if (next.length > 0) onScopeChange('selected');
  };

  return (
    <section className="surface p-4">
      <div className="mb-3 flex items-center justify-between gap-3">
        <div>
          <h2 className="text-sm font-bold text-[var(--color-ink)]">文献范围</h2>
          <p className="mt-1 text-xs leading-5 text-[var(--color-ink-mute)]">选择写作时可使用的本地 READY 文献。</p>
        </div>
        {isLoading && <Loader2 size={16} className="animate-spin text-[var(--color-ink-mute)]" />}
      </div>

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

      <div className="max-h-72 space-y-2 overflow-y-auto pr-1">
        {!isLoading && papers.length === 0 && (
          <p className="rounded-lg border border-dashed border-[var(--color-border)] p-4 text-sm text-[var(--color-ink-mute)]">
            还没有可用于写作的 READY 文献，请先上传并完成解析。
          </p>
        )}
        {papers.map((paper) => {
          const selected = scope === 'library' || selectedPaperIds.includes(paper.id);
          return (
            <button
              key={paper.id}
              type="button"
              onClick={() => togglePaper(paper.id)}
              className="flex w-full items-start gap-3 rounded-lg border border-[var(--color-border)] bg-[var(--surface)] p-3 text-left transition hover:bg-[var(--color-primary-soft)]"
            >
              <span
                className={`mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-md border ${
                  selected ? 'border-[var(--color-primary)] bg-[var(--color-primary)] text-white' : 'border-[var(--color-border-strong)]'
                }`}
              >
                {selected && <Check size={13} />}
              </span>
              <span className="min-w-0">
                <span className="line-clamp-2 text-sm font-semibold leading-5 text-[var(--color-ink)]">{paper.title}</span>
                <span className="mt-1 block line-clamp-1 text-xs text-[var(--color-ink-mute)]">
                  {paper.authors || paper.filename}
                </span>
              </span>
            </button>
          );
        })}
      </div>
    </section>
  );
}
