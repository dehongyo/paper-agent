import type { WritingEvidence } from '../../types';

interface Props {
  evidence: WritingEvidence[];
}

export function WritingEvidencePanel({ evidence }: Props) {
  return (
    <section className="surface p-4">
      <div className="mb-3">
        <h2 className="text-sm font-bold text-[var(--color-ink)]">证据来源</h2>
        <p className="mt-1 text-xs leading-5 text-[var(--color-ink-mute)]">草稿中的来源编号对应这些本地文献片段。</p>
      </div>

      {evidence.length === 0 ? (
        <p className="rounded-lg border border-dashed border-[var(--color-border)] p-4 text-sm leading-6 text-[var(--color-ink-mute)]">
          生成大纲或草稿后，这里会显示证据片段。
        </p>
      ) : (
        <div className="max-h-[520px] space-y-3 overflow-y-auto pr-1">
          {evidence.map((item) => (
            <article key={item.chunkId} className="rounded-lg border border-[var(--color-border)] bg-[var(--surface)] p-3">
              <div className="mb-2 flex items-center justify-between gap-2">
                <span className="pill bg-[var(--color-primary-soft)] text-[var(--color-primary)]">[{item.index}]</span>
                <span className="text-xs text-[var(--color-ink-mute)]">{Math.round(item.similarity * 100)}%</span>
              </div>
              <h3 className="line-clamp-2 text-sm font-semibold leading-5 text-[var(--color-ink)]">{item.paperTitle}</h3>
              <p className="mt-1 text-xs text-[var(--color-ink-mute)]">chunk #{item.chunkIndex}</p>
              <p className="mt-3 line-clamp-5 text-xs leading-5 text-[var(--color-ink-soft)]">{item.content}</p>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}
