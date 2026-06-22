import { useRef, useState } from 'react';
import { FileText, Loader2, Upload, X } from 'lucide-react';
import { uploadPaper } from '../../api/client';
import type { PaperSummary } from '../../types';

interface Props {
  onUploaded: (paper: PaperSummary) => void;
}

export function PaperUpload({ onUploaded }: Props) {
  const [open, setOpen] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const handleFile = (file: File) => {
    if (file.type === 'application/pdf') {
      setSelectedFile(file);
      setError(null);
    } else {
      setError('仅支持 PDF 文件');
    }
  };

  const handleUpload = async () => {
    if (!selectedFile) return;
    setIsUploading(true);
    setError(null);
    try {
      const result = await uploadPaper(selectedFile);
      onUploaded(result);
      setOpen(false);
      setSelectedFile(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : '上传失败');
    } finally {
      setIsUploading(false);
    }
  };

  const handleDrop = (event: React.DragEvent) => {
    event.preventDefault();
    const file = event.dataTransfer.files[0];
    if (file) handleFile(file);
  };

  if (!open) {
    return (
      <button onClick={() => setOpen(true)} className="primary-button">
        <Upload size={16} />
        上传论文
      </button>
    );
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/24 p-4 backdrop-blur-sm" onClick={() => setOpen(false)}>
      <div
        className="surface-solid w-full max-w-md p-5 shadow-[var(--shadow-lg)]"
        onClick={(event) => event.stopPropagation()}
        onDrop={handleDrop}
        onDragOver={(event) => event.preventDefault()}
      >
        <div className="mb-4 flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-[var(--color-primary-soft)] text-[var(--color-primary)]">
            <Upload size={18} />
          </div>
          <div>
            <h2 className="text-lg font-bold tracking-tight">上传 PDF 论文</h2>
            <p className="text-xs text-[var(--color-ink-mute)]">上传后会自动解析、向量化并生成摘要。</p>
          </div>
          <button type="button" onClick={() => setOpen(false)} className="icon-button ml-auto" aria-label="关闭上传窗口">
            <X size={16} />
          </button>
        </div>

        <div className="flex flex-col items-center gap-4 rounded-lg border border-dashed border-[var(--color-border-strong)] bg-black/[0.025] p-8">
          {selectedFile ? (
            <div className="flex max-w-full items-center gap-2 text-sm">
              <FileText size={22} className="shrink-0 text-[var(--color-primary)]" />
              <span className="truncate font-semibold">{selectedFile.name}</span>
              <span className="shrink-0 text-[var(--color-ink-mute)]">({(selectedFile.size / 1024 / 1024).toFixed(1)} MB)</span>
            </div>
          ) : (
            <>
              <Upload size={40} className="text-[var(--color-ink-mute)] opacity-45" />
              <p className="text-center text-sm text-[var(--color-ink-mute)]">拖拽 PDF 文件到此处，或从本地选择。</p>
            </>
          )}

          <button onClick={() => inputRef.current?.click()} className="secondary-button px-4">
            选择文件
          </button>
          <input
            ref={inputRef}
            type="file"
            accept="application/pdf"
            className="hidden"
            onChange={(event) => {
              const file = event.target.files?.[0];
              if (file) handleFile(file);
            }}
          />

          {error && <p className="text-sm font-semibold text-red-600">{error}</p>}

          <button onClick={handleUpload} disabled={!selectedFile || isUploading} className="primary-button w-full">
            {isUploading ? (
              <span className="flex items-center justify-center gap-2">
                <Loader2 size={16} className="animate-spin" />
                上传中...
              </span>
            ) : (
              '开始上传'
            )}
          </button>
        </div>
      </div>
    </div>
  );
}
