import { useCallback, useEffect, useState } from 'react';
import { deleteWritingVersion, getWritingVersion, getWritingVersions, saveWritingVersion } from '../../api/client';
import type { ReferenceItem, WritingVersionResponse } from '../../types';
import { Download, Loader2, Save, Trash2 } from 'lucide-react';

interface Props {
  topic: string;
  outline: string;
  draft: string;
  references: ReferenceItem[];
  onLoad: (data: { outline: string; draft: string; references: ReferenceItem[] }) => void;
  onClose: () => void;
}

export function VersionManager({ topic, outline, draft, references, onLoad, onClose }: Props) {
  const [versions, setVersions] = useState<WritingVersionResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [savingVersion, setSavingVersion] = useState(false);
  const [loadingVersionId, setLoadingVersionId] = useState<number | null>(null);

  const loadVersions = useCallback(async () => {
    try {
      setIsLoading(true);
      setVersions(await getWritingVersions());
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载版本列表失败');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadVersions();
  }, [loadVersions]);

  const handleSave = async () => {
    if (!topic.trim() || !draft.trim()) return;
    setSavingVersion(true);
    setError(null);
    try {
      await saveWritingVersion({ topic, outline, draft, references });
      await loadVersions();
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存版本失败');
    } finally {
      setSavingVersion(false);
    }
  };

  const handleLoad = async (id: number) => {
    setLoadingVersionId(id);
    setError(null);
    try {
      const full = await getWritingVersion(id);
      onLoad({ outline: full.outline, draft: full.draft, references: full.references });
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载版本失败');
    } finally {
      setLoadingVersionId(null);
    }
  };

  const handleDelete = async (id: number) => {
    if (!window.confirm('确定删除这个版本吗？')) return;
    try {
      await deleteWritingVersion(id);
      await loadVersions();
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : '删除版本失败');
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center backdrop-overlay bg-black/24 p-4 backdrop-blur-sm"
      onClick={onClose}
    >
      <div
        className="surface-solid modal-dialog w-full max-w-lg p-5 shadow-[var(--shadow-lg)]"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-3 mb-5">
          <div>
            <h2 className="text-lg font-extrabold tracking-tight">版本历史</h2>
            <p className="text-xs text-[var(--color-ink-mute)] mt-1">保存和恢复写作版本。</p>
          </div>
          <button
            type="button"
            onClick={() => void handleSave()}
            disabled={savingVersion || !topic.trim() || !draft.trim()}
            className="secondary-button min-h-[36px] text-xs"
          >
            {savingVersion ? <Loader2 size={14} className="animate-spin" /> : <Save size={14} />}
            保存版本
          </button>
        </div>

        {error && (
          <div className="mb-4 rounded-[var(--radius-sm)] bg-red-50 px-3 py-2 text-xs font-semibold text-red-600">
            {error}
          </div>
        )}

        {isLoading ? (
          <div className="flex items-center justify-center gap-2 py-10 text-sm text-[var(--color-ink-mute)]">
            <Loader2 size={18} className="animate-spin" />
            加载版本列表...
          </div>
        ) : versions.length === 0 ? (
          <div className="rounded-[var(--radius-sm)] border border-dashed border-[var(--color-border)] bg-[var(--color-primary-soft)] px-4 py-8 text-center text-sm text-[var(--color-ink-mute)]">
            暂无保存的版本。
          </div>
        ) : (
          <div className="space-y-2 max-h-80 overflow-y-auto">
            {versions.map((v) => (
              <div
                key={v.id}
                className="flex items-center justify-between gap-3 rounded-[var(--radius-sm)] border border-[var(--color-border)] bg-[var(--surface)] px-4 py-3"
              >
                <div className="min-w-0">
                  <p className="truncate text-sm font-bold text-[var(--color-ink)]">{v.topic}</p>
                  <p className="mt-0.5 text-xs text-[var(--color-ink-mute)]">
                    v{v.versionNumber} · {new Date(v.createdAt).toLocaleString('zh-CN')}
                  </p>
                </div>
                <div className="flex shrink-0 gap-1.5">
                  <button
                    type="button"
                    onClick={() => void handleLoad(v.id)}
                    disabled={loadingVersionId === v.id}
                    className="icon-button h-8 w-8"
                    aria-label="加载版本"
                  >
                    {loadingVersionId === v.id ? <Loader2 size={14} className="animate-spin" /> : <Download size={14} />}
                  </button>
                  <button
                    type="button"
                    onClick={() => void handleDelete(v.id)}
                    className="icon-button h-8 w-8 text-red-600"
                    aria-label="删除版本"
                  >
                    <Trash2 size={14} />
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
