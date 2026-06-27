interface Props {
  outline: string;
  draft: string;
  onOutlineChange: (value: string) => void;
  onDraftChange: (value: string) => void;
}

export function WritingEditor({ outline, draft, onOutlineChange, onDraftChange }: Props) {
  const isEmpty = !outline.trim() && !draft.trim();

  return (
    <section className="surface">
      <div className="mb-4 flex items-center justify-between gap-3">
        <div>
          <h2 className="text-sm font-bold text-[var(--color-ink)]">写作稿</h2>
          <p className="mt-1 text-xs leading-5 text-[var(--color-ink-mute)]">大纲和草稿都可以继续编辑，导出时会使用当前文本。</p>
        </div>
      </div>

      {isEmpty && (
        <div className="mb-4 rounded-[var(--radius-sm)] border border-[var(--color-border)] bg-[var(--color-primary-soft)] px-5 py-3.5 text-center">
          <p className="text-sm leading-6 text-[var(--color-ink-soft)]">
            在左侧填写综述主题和格式，然后点击「生成大纲」<span className="font-semibold text-[var(--color-ink)]">开始写作</span>。大纲生成后可继续生成草稿和参考文献。
          </p>
        </div>
      )}

      <div className="grid gap-4">
        <label className={outline.trim() ? 'draft-content-enter' : ''}>
          <span className="mb-1.5 block text-xs font-semibold text-[var(--color-ink-soft)]">大纲</span>
          <textarea
            value={outline}
            onChange={(event) => onOutlineChange(event.target.value)}
            placeholder="生成或手动输入综述大纲…"
            className="control writing-textarea writing-textarea-outline resize-y px-3 py-3 text-sm leading-6"
          />
        </label>

        <label className={draft.trim() ? 'draft-content-enter' : ''}>
          <span className="mb-1.5 block text-xs font-semibold text-[var(--color-ink-soft)]">草稿</span>
          <textarea
            value={draft}
            onChange={(event) => onDraftChange(event.target.value)}
            placeholder="生成或手动输入综述草稿…"
            className="control writing-textarea writing-textarea-draft resize-y px-3 py-3 text-sm leading-6"
          />
        </label>
      </div>
    </section>
  );
}
