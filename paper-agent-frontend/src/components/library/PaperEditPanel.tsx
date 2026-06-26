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
    try {
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
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存失败，请稍后重试');
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex justify-end backdrop-overlay bg-black/24 backdrop-blur-sm" onClick={onClose}>
      <aside className="slide-panel edit-panel-shell flex h-full w-full max-w-4xl flex-col overflow-hidden rounded-l-2xl border border-[var(--color-border)] bg-[var(--surface)] shadow-[var(--shadow-lg)]" onClick={(event) => event.stopPropagation()}>
        <header className="edit-panel-section edit-panel-header flex items-center gap-3 border-b border-[var(--color-border)] py-5">
          <div className="min-w-0">
            <p className="eyebrow">EDIT PAPER</p>
            <h2 className="mt-1 text-xl font-bold tracking-tight">编辑论文信息</h2>
            <p className="mt-1 line-clamp-1 text-xs text-[var(--color-ink-mute)]">{paper.filename}</p>
          </div>
          <button type="button" onClick={onClose} className="icon-button edit-panel-close" aria-label="关闭编辑面板">
            <X size={18} />
          </button>
        </header>

        <div className="edit-panel-section flex-1 overflow-y-auto py-6">
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="sm:col-span-2">
              <span className="edit-panel-label">标题</span>
              <input value={title} onChange={(event) => setTitle(event.target.value)} className="control" style={{ fontSize: '14px', paddingInline: '13px' }} />
            </label>

            <label>
              <span className="edit-panel-label">作者</span>
              <input value={authors} onChange={(event) => setAuthors(event.target.value)} placeholder="Alice; Bob" className="control" style={{ fontSize: '14px', paddingInline: '13px' }} />
            </label>

            <label>
              <span className="edit-panel-label">发表日期</span>
              <input type="date" value={publishedAt} onChange={(event) => setPublishedAt(event.target.value)} className="control" style={{ fontSize: '14px', paddingInline: '13px' }} />
            </label>

            <label>
              <span className="edit-panel-label">DOI</span>
              <input value={doi} onChange={(event) => setDoi(event.target.value)} className="control" style={{ fontSize: '14px', paddingInline: '13px' }} />
            </label>

            <label>
              <span className="edit-panel-label">来源链接</span>
              <input value={sourceUrl} onChange={(event) => setSourceUrl(event.target.value)} className="control" style={{ fontSize: '14px', paddingInline: '13px' }} />
            </label>

            <label className="sm:col-span-2">
              <span className="edit-panel-label">标签</span>
              <input value={tagsText} onChange={(event) => setTagsText(event.target.value)} placeholder="RAG, Survey, LLM" className="control" style={{ fontSize: '14px', paddingInline: '13px' }} />
              <span className="field-hint">多个标签用英文逗号分隔。</span>
            </label>

            <label className="sm:col-span-2">
              <span className="edit-panel-label">摘要</span>
              <textarea value={summary} onChange={(event) => setSummary(event.target.value)} rows={5} className="control h-auto min-h-32 resize-y" style={{ fontSize: '14px', padding: '11px 13px', lineHeight: 1.6 }} />
            </label>

            <label className="sm:col-span-2">
              <span className="edit-panel-label">笔记</span>
              <textarea value={notes} onChange={(event) => setNotes(event.target.value)} rows={5} className="control h-auto min-h-32 resize-y" style={{ fontSize: '14px', padding: '11px 13px', lineHeight: 1.6 }} />
            </label>
          </div>

          {error && <p className="mt-4 text-sm font-semibold text-red-600">{error}</p>}
        </div>

        <footer className="edit-panel-section flex items-center justify-end gap-3 border-t border-[var(--color-border)] bg-[var(--surface-warm)] py-4">
          <button type="button" onClick={onClose} className="secondary-button">
            取消
          </button>
          <button type="button" onClick={handleSave} disabled={isSaving} className="primary-button">
            {isSaving ? <Loader2 size={16} className="animate-spin" /> : <Save size={16} />}
            保存
          </button>
        </footer>
      </aside>
    </div>
  );
}
