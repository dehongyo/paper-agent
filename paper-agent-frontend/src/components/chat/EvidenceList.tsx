import type { EvidenceChunk } from '../../types';
import { BookOpen, Percent } from 'lucide-react';

interface Props {
  evidence?: EvidenceChunk[];
  onSelectPaper?: (paperId: number) => void;
}

export function EvidenceList({ evidence, onSelectPaper }: Props) {
  if (!evidence || evidence.length === 0) return null;

  return (
    <div className="mt-4 space-y-2 border-t border-[var(--color-border)] pt-3">
      <div className="flex items-center gap-2 text-xs font-semibold text-[var(--color-ink-mute)]">
        <BookOpen size={14} />
        来源证据
      </div>
      <div className="grid gap-2">
        {evidence.map((item) => (
          <button
            key={item.chunkId}
            type="button"
            onClick={() => onSelectPaper?.(item.paperId)}
            className="rounded-lg border border-[var(--color-border)] bg-black/[0.025] p-3 text-left transition hover:border-[var(--color-primary)] hover:bg-white"
          >
            <div className="flex items-start justify-between gap-3">
              <span className="line-clamp-1 text-xs font-semibold text-[var(--color-ink)]">
                {item.paperTitle} · 片段 {item.chunkIndex + 1}
              </span>
              <span className="inline-flex shrink-0 items-center gap-1 text-xs text-[var(--color-ink-mute)]">
                <Percent size={12} />
                {(item.similarity * 100).toFixed(1)}
              </span>
            </div>
            <p className="mt-1 line-clamp-3 text-xs leading-5 text-[var(--color-ink-soft)]">
              {item.content}
            </p>
          </button>
        ))}
      </div>
    </div>
  );
}
