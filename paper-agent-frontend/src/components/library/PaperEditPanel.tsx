import { useMemo, useState } from 'react';
import type { PaperSummary, PaperUpdateRequest } from '../../types';
import { Loader2, Save, X } from 'lucide-react';

interface Props {
  paper: PaperSummary;
  isSaving: boolean;
  onClose: () => void;
  onSave: (payload: PaperUpdateRequest) => Promise<void>;
}

export function PaperEditPanel({ paper, isSaving, onClose, onSave }: Props) {
  const [title, setTitle] = useState(paper.title);
  const [authors, setAuthors] = useState(paper.authors ?? '');
  const [doi, setDoi] = useState(paper.doi ?? '');
  const [sourceUrl, setSourceUrl] = useState(paper.sourceUrl ?? '');
  const [publishedAt, setPublishedAt] = useState(paper.publishedAt ?? '');
  const [summary, setSummary] = useState(paper.summary ?? '');
  const [notes, setNotes] = useState(paper.notes ?? '');
  const [tagsText, setTagsText] = useState(paper.tags.join(', '));
  const [error, setError] = useState<string | null>(null);

  const tags = useMemo(
    () => tagsText.split(',').map((tag) => tag.trim()).filter(Boolean),
    [tagsText]
  );

  const handleSave = async () => {
    if (!title.trim()) {
      setError('标题不能为空');
      return;
    }
    setError(null);
    await onSave({
      title: title.trim(),
      authors: authors.trim() || null,
      doi: doi.trim() || null,
      sourceUrl: sourceUrl.trim() || null,
      publishedAt: publishedAt || null,
      summary: summary.trim() || null,
      notes: notes.trim() || null,
      tags,
    });
  };

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/24 backdrop-blur-sm" onClick={onClose}>
      <aside className="flex h-full w-full max-w-2xl flex-col border-l border-[var(--color-border)] bg-white/88 shadow-[var(--shadow-lg)] backdrop-blur-2xl" onClick={(event) => event.stopPropagation()}>
        <header className="flex items-center gap-3 border-b border-[var(--color-border)] px-6 py-5">
          <div>
            <p className="eyebrow">EDIT PAPER</p>
            <h2 className="mt-1 text-xl font-bold tracking-tight">编辑论文信息</h2>
            <p className="mt-1 line-clamp-1 text-xs text-[var(--color-ink-mute)]">{paper.filename}</p>
          </div>
          <button type="button" onClick={onClose} className="icon-button ml-auto" aria-label="关闭编辑面板">
            <X size={18} />
          </button>
        </header>

        <div className="flex-1 overflow-y-auto px-6 py-5">
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="sm:col-span-2">
              <span className="text-sm font-semibold">标题</span>
              <input value={title} onChange={(event) => setTitle(event.target.value)} className="control mt-1 px-3 text-sm" />
            </label>

            <label>
              <span className="text-sm font-semibold">作者</span>
              <input value={authors} onChange={(event) => setAuthors(event.target.value)} placeholder="Alice; Bob" className="control mt-1 px-3 text-sm" />
            </label>

            <label>
              <span className="text-sm font-semibold">发表日期</span>
              <input type="date" value={publishedAt} onChange={(event) => setPublishedAt(event.target.value)} className="control mt-1 px-3 text-sm" />
            </label>

            <label>
              <span className="text-sm font-semibold">DOI</span>
              <input value={doi} onChange={(event) => setDoi(event.target.value)} className="control mt-1 px-3 text-sm" />
            </label>

            <label>
              <span className="text-sm font-semibold">来源链接</span>
              <input value={sourceUrl} onChange={(event) => setSourceUrl(event.target.value)} className="control mt-1 px-3 text-sm" />
            </label>

            <label className="sm:col-span-2">
              <span className="text-sm font-semibold">标签</span>
              <input value={tagsText} onChange={(event) => setTagsText(event.target.value)} placeholder="RAG, Survey, LLM" className="control mt-1 px-3 text-sm" />
              <span className="mt-1 block text-xs text-[var(--color-ink-mute)]">多个标签用英文逗号分隔。</span>
            </label>

            <label className="sm:col-span-2">
              <span className="text-sm font-semibold">摘要</span>
              <textarea value={summary} onChange={(event) => setSummary(event.target.value)} rows={5} className="control mt-1 h-auto min-h-32 resize-y px-3 py-2 text-sm" />
            </label>

            <label className="sm:col-span-2">
              <span className="text-sm font-semibold">笔记</span>
              <textarea value={notes} onChange={(event) => setNotes(event.target.value)} rows={5} className="control mt-1 h-auto min-h-32 resize-y px-3 py-2 text-sm" />
            </label>
          </div>

          {error && <p className="mt-4 text-sm font-semibold text-red-600">{error}</p>}
        </div>

        <footer className="flex items-center justify-end gap-3 border-t border-[var(--color-border)] bg-white/62 px-6 py-4">
          <button type="button" onClick={onClose} className="secondary-button px-4">
            取消
          </button>
          <button type="button" onClick={handleSave} disabled={isSaving} className="primary-button px-4">
            {isSaving ? <Loader2 size={16} className="animate-spin" /> : <Save size={16} />}
            保存
          </button>
        </footer>
      </aside>
    </div>
  );
}
