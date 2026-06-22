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
    <section className="surface p-4">
      <div className="mb-3 flex items-center justify-between gap-3">
        <div>
          <h2 className="text-sm font-bold text-[var(--color-ink)]">导出</h2>
          <p className="mt-1 text-xs leading-5 text-[var(--color-ink-mute)]">复制 Markdown 或 LaTeX 后可继续整理。</p>
        </div>
        <FileCode2 size={18} className="text-[var(--color-primary)]" />
      </div>

      <div className="grid gap-3 lg:grid-cols-2">
        <div className="rounded-lg border border-[var(--color-border)] bg-white/58 p-3">
          <div className="mb-2 flex items-center justify-between">
            <span className="text-xs font-bold text-[var(--color-ink)]">Markdown</span>
            <button type="button" onClick={() => void copy(markdown)} className="icon-button" aria-label="复制 Markdown">
              <Clipboard size={15} />
            </button>
          </div>
          <pre className="max-h-52 overflow-auto whitespace-pre-wrap text-xs leading-5 text-[var(--color-ink-soft)]">{markdown || '暂无 Markdown 导出内容。'}</pre>
        </div>

        <div className="rounded-lg border border-[var(--color-border)] bg-white/58 p-3">
          <div className="mb-2 flex items-center justify-between">
            <span className="text-xs font-bold text-[var(--color-ink)]">LaTeX</span>
            <button type="button" onClick={() => void copy(latex)} className="icon-button" aria-label="复制 LaTeX">
              <Clipboard size={15} />
            </button>
          </div>
          <pre className="max-h-52 overflow-auto whitespace-pre-wrap text-xs leading-5 text-[var(--color-ink-soft)]">{latex || '暂无 LaTeX 导出内容。'}</pre>
        </div>
      </div>
    </section>
  );
}
