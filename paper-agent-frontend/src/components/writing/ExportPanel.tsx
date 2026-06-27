import { Clipboard, FileCode2 } from 'lucide-react';

interface Props {
  markdown: string;
  latex: string;
}

export function ExportPanel({ markdown, latex }: Props) {
  const copy = async (text: string) => {
    if (!text.trim()) return;
    await navigator.clipboard.writeText(text);
  };

  return (
    <section className="surface">
      <div className="mb-3 flex items-center justify-between gap-3">
        <div>
          <h2 className="text-sm font-bold text-[var(--color-ink)]">导出</h2>
          <p className="mt-1 text-xs leading-5 text-[var(--color-ink-mute)]">复制 Markdown 或 LaTeX 后可继续整理。</p>
        </div>
        <FileCode2 size={18} className="text-[var(--color-primary)]" />
      </div>

      <div className="grid gap-3 lg:grid-cols-2">
        <div className="overflow-hidden rounded-[var(--radius-sm)] border border-[var(--color-border)] bg-[var(--surface-warm)] p-3">
          <div className="mb-2 flex items-center justify-between">
            <span className="text-xs font-semibold tracking-[0.02em] text-[var(--color-ink-mute)]">MARKDOWN</span>
            <button type="button" onClick={() => void copy(markdown)} className="rounded-[var(--radius-sm)] p-1.5 text-[var(--color-ink-mute)] transition hover:bg-[var(--color-primary-soft)] hover:text-[var(--color-ink)]" aria-label="复制 Markdown">
              <Clipboard size={14} />
            </button>
          </div>
          <pre className="max-h-44 overflow-auto whitespace-pre-wrap font-mono text-xs leading-relaxed text-[var(--color-ink-soft)]">{markdown || '暂无 Markdown 导出内容。'}</pre>
        </div>

        <div className="overflow-hidden rounded-[var(--radius-sm)] border border-[var(--color-border)] bg-[var(--surface-warm)] p-3">
          <div className="mb-2 flex items-center justify-between">
            <span className="text-xs font-semibold tracking-[0.02em] text-[var(--color-ink-mute)]">LATEX</span>
            <button type="button" onClick={() => void copy(latex)} className="rounded-[var(--radius-sm)] p-1.5 text-[var(--color-ink-mute)] transition hover:bg-[var(--color-primary-soft)] hover:text-[var(--color-ink)]" aria-label="复制 LaTeX">
              <Clipboard size={14} />
            </button>
          </div>
          <pre className="max-h-44 overflow-auto whitespace-pre-wrap font-mono text-xs leading-relaxed text-[var(--color-ink-soft)]">{latex || '暂无 LaTeX 导出内容。'}</pre>
        </div>
      </div>
    </section>
  );
}
