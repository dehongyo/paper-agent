interface Props {
  outline: string;
  draft: string;
  onOutlineChange: (value: string) => void;
  onDraftChange: (value: string) => void;
}

export function WritingEditor({ outline, draft, onOutlineChange, onDraftChange }: Props) {
  return (
    <section className="surface p-4">
      <div className="mb-4 flex items-center justify-between gap-3">
        <div>
          <h2 className="text-sm font-bold text-[var(--color-ink)]">写作稿</h2>
          <p className="mt-1 text-xs leading-5 text-[var(--color-ink-mute)]">大纲和草稿都可以继续编辑，导出时会使用当前文本。</p>
        </div>
      </div>

      <div className="grid gap-4">
        <label>
          <span className="mb-1.5 block text-xs font-semibold text-[var(--color-ink-soft)]">大纲</span>
          <textarea
            value={outline}
            onChange={(event) => onOutlineChange(event.target.value)}
            placeholder="生成或手动输入综述大纲..."
            className="control min-h-40 resize-y px-3 py-3 text-sm leading-6"
          />
        </label>

        <label>
          <span className="mb-1.5 block text-xs font-semibold text-[var(--color-ink-soft)]">草稿</span>
          <textarea
            value={draft}
            onChange={(event) => onDraftChange(event.target.value)}
            placeholder="生成或手动输入综述草稿..."
            className="control min-h-[360px] resize-y px-3 py-3 text-sm leading-6"
          />
        </label>
      </div>
    </section>
  );
}
