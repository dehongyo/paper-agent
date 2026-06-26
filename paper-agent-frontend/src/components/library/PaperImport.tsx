import { useState } from 'react';
import { batchImport, importPaper } from '../../api/client';
import type { PaperSummary } from '../../types';
import { Download, Link2, Loader2, X } from 'lucide-react';

interface Props {
  onImported: (paper: PaperSummary) => void;
}

export function PaperImport({ onImported }: Props) {
  const [open, setOpen] = useState(false);
  const [urlOrDoi, setUrlOrDoi] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [batchMode, setBatchMode] = useState(false);
  const [batchUrls, setBatchUrls] = useState('');
  const [batchProgress, setBatchProgress] = useState(0);
  const [batchTotal, setBatchTotal] = useState(0);

  const handleImport = async () => {
    const trimmed = urlOrDoi.trim();
    if (!trimmed) {
      setError('请输入 URL 或 DOI');
      return;
    }

    if (trimmed.startsWith('10.') && !trimmed.includes('/')) {
      setError('DOI 格式不正确');
      return;
    }

    setIsLoading(true);
    setError(null);
    try {
      const result = await importPaper(trimmed);
      onImported(result);
      setOpen(false);
      setUrlOrDoi('');
    } catch (err) {
      setError(err instanceof Error ? err.message : '导入失败，请检查链接是否有效。');
    } finally {
      setIsLoading(false);
    }
  };

  const handleBatchImport = async () => {
    const lines = batchUrls
      .split('\n')
      .map((line) => line.trim())
      .filter((line) => line.length > 0);

    if (lines.length === 0) {
      setError('请输入至少一条 URL 或 DOI');
      return;
    }

    setIsLoading(true);
    setError(null);
    setBatchTotal(lines.length);
    setBatchProgress(0);
    try {
      const results = await batchImport(lines);
      setBatchProgress(results.length);
      for (const paper of results) {
        onImported(paper);
      }
      setOpen(false);
      setBatchUrls('');
      setBatchMode(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : '批量导入失败，请检查链接是否有效。');
    } finally {
      setIsLoading(false);
      setBatchTotal(0);
      setBatchProgress(0);
    }
  };

  if (!open) {
    return (
      <button onClick={() => setOpen(true)} className="secondary-button">
        <Download size={16} />
        导入论文
      </button>
    );
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center backdrop-overlay bg-black/24 p-4 backdrop-blur-sm"
      onClick={() => setOpen(false)}
    >
      <div
        className="surface-solid modal-dialog w-full max-w-md p-5 shadow-[var(--shadow-lg)]"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="mb-4 flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-[var(--radius-sm)] border border-[var(--color-border)] bg-[var(--color-primary-soft)] text-[var(--color-ink)]">
            <Link2 size={18} />
          </div>
          <div>
            <h2 className="text-lg font-extrabold tracking-tight">从链接导入论文</h2>
            <p className="text-xs text-[var(--color-ink-mute)]">支持 arXiv 链接和 DOI。</p>
          </div>
          <button type="button" onClick={() => setOpen(false)} className="icon-button ml-auto" aria-label="关闭导入窗口">
            <X size={16} />
          </button>
        </div>

        <div className="space-y-4">
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => {
                setBatchMode(false);
                setError(null);
              }}
              className={`text-xs font-semibold px-3 py-1.5 rounded-full transition ${!batchMode ? 'bg-[var(--fg)] text-white' : 'bg-[var(--color-primary-soft)] text-[var(--color-ink-soft)]'}`}
            >
              单个导入
            </button>
            <button
              type="button"
              onClick={() => {
                setBatchMode(true);
                setError(null);
              }}
              className={`text-xs font-semibold px-3 py-1.5 rounded-full transition ${batchMode ? 'bg-[var(--fg)] text-white' : 'bg-[var(--color-primary-soft)] text-[var(--color-ink-soft)]'}`}
            >
              批量导入
            </button>
          </div>

          {!batchMode ? (
            <label>
              <span className="mb-1.5 block text-xs font-semibold text-[var(--color-ink-soft)]">arXiv 链接或 DOI</span>
              <input
                value={urlOrDoi}
                onChange={(event) => setUrlOrDoi(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') void handleImport();
                }}
                placeholder="https://arxiv.org/abs/2503.21476 或 10.48550/arXiv.2503.21476"
                className="control text-sm"
              />
              <span className="mt-1.5 block text-xs text-[var(--color-ink-mute)]">
                示例：arXiv 摘要页链接、arXiv PDF 链接、DOI 号
              </span>
            </label>
          ) : (
            <label>
              <span className="mb-1.5 block text-xs font-semibold text-[var(--color-ink-soft)]">每行一条 URL 或 DOI</span>
              <textarea
                value={batchUrls}
                onChange={(event) => setBatchUrls(event.target.value)}
                placeholder={
                  'https://arxiv.org/abs/2503.21476\nhttps://arxiv.org/abs/2305.12345\n10.48550/arXiv.2503.21476'
                }
                className="control text-sm"
                rows={8}
              />
              <span className="mt-1.5 block text-xs text-[var(--color-ink-mute)]">
                每行一个 arXiv 链接或 DOI 号，支持批量导入。
              </span>
            </label>
          )}

          {error && <p className="text-sm font-semibold text-red-600">{error}</p>}

          {batchTotal > 0 && (
            <p className="text-xs text-[var(--color-ink-soft)]">
              进度：{batchProgress} / {batchTotal}
            </p>
          )}

          <button
            onClick={() => void (batchMode ? handleBatchImport() : handleImport())}
            disabled={(batchMode ? !batchUrls.trim() : !urlOrDoi.trim()) || isLoading}
            className="primary-button w-full"
          >
            {isLoading ? (
              <span className="flex items-center justify-center gap-2">
                <Loader2 size={16} className="animate-spin" />
                导入中...
              </span>
            ) : (
              <>
                <Download size={16} />
                {batchMode ? '批量导入' : '开始导入'}
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}
