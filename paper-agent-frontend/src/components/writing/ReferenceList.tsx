import type { ReferenceItem } from '../../types';

interface Props {
  references: ReferenceItem[];
}

export function ReferenceList({ references }: Props) {
  return (
    <section className="surface">
      <div className="writing-section-header mb-4">
        <h2 className="text-sm font-bold text-[var(--color-ink)]">参考文献</h2>
      </div>
      {references.length === 0 ? (
        <p className="text-sm text-[var(--color-ink-mute)]">生成后会自动列出参考文献。</p>
      ) : (
        <ol className="space-y-2">
          {references.map((reference) => (
            <li key={`${reference.index}-${reference.paperId}`} className="rounded-lg border border-[var(--color-border)] bg-[var(--surface)] p-3 text-sm leading-6 text-[var(--color-ink-soft)]">
              {reference.formatted}
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
