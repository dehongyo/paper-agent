import { useCallback, useEffect, useRef, useState } from 'react';
import { Document, Page, pdfjs } from 'react-pdf';
import type { PaperListItem } from '../../types';
import { apiUrl } from '../../api/base';
import {
  ChevronLeft,
  ChevronRight,
  FileText,
  Highlighter,
  Loader2,
  Maximize2,
  Minimize2,
  Minus,
  Plus,
  RotateCw,
  X,
} from 'lucide-react';

// Use CDN worker matching react-pdf@10's bundled pdfjs-dist v5
pdfjs.GlobalWorkerOptions.workerSrc =
  'https://unpkg.com/pdfjs-dist@5.4.296/build/pdf.worker.min.mjs';

interface Props {
  paper: PaperListItem;
  onClose: () => void;
}

interface Highlight {
  id: string;
  pageNumber: number;
  x: number;
  y: number;
  width: number;
  height: number;
  color: string;
  text: string;
}

const HIGHLIGHT_COLORS = ['#ffeb3b', '#a5d6a7', '#90caf9', '#f48fb1', '#ce93d8'];

export function PaperViewer({ paper, onClose }: Props) {
  const [numPages, setNumPages] = useState<number>(0);
  const [pageNumber, setPageNumber] = useState<number>(1);
  const [scale, setScale] = useState<number>(1.2);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [retryKey, setRetryKey] = useState(0);
  const [rotation, setRotation] = useState<number>(0);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [showHighlights, setShowHighlights] = useState(true);
  const [highlights, setHighlights] = useState<Highlight[]>(() => {
    try {
      const saved = localStorage.getItem(`pa-highlights-${paper.id}`);
      return saved ? JSON.parse(saved) : [];
    } catch {
      return [];
    }
  });
  const [activeHighlightColor, setActiveHighlightColor] = useState(HIGHLIGHT_COLORS[0]);
  const [isHighlightMode, setIsHighlightMode] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const pageRef = useRef<HTMLDivElement>(null);

  const pdfUrl = apiUrl(`/papers/${paper.id}/file`);

  // Reset state when paper changes
  useEffect(() => {
    const timer = window.setTimeout(() => {
      setNumPages(0);
      setPageNumber(1);
      setScale(1.2);
      setIsLoading(true);
      setError(null);
      setRetryKey(0);
      setRotation(0);
      setShowHighlights(true);
      setIsHighlightMode(false);
    }, 0);
    return () => window.clearTimeout(timer);
  }, [paper.id]);

  // Persist highlights
  useEffect(() => {
    localStorage.setItem(`pa-highlights-${paper.id}`, JSON.stringify(highlights));
  }, [highlights, paper.id]);

  const onDocumentLoadSuccess = useCallback(({ numPages }: { numPages: number }) => {
    setNumPages(numPages);
    setPageNumber(1);
    setIsLoading(false);
    setError(null);
  }, []);

  const onDocumentLoadError = useCallback((err: Error) => {
    const msg = err.message || '';
    if (msg.includes('404') || msg.includes('Not Found')) {
      setError('PDF 文件未找到，文件可能已被移动或尚未完成上传处理。');
    } else if (msg.includes('Failed to fetch') || msg.includes('NetworkError')) {
      setError('无法连接到服务器，请检查后端服务是否运行。');
    } else {
      setError(`PDF 加载失败：${msg}`);
    }
    setIsLoading(false);
  }, []);

  const handleRetry = () => {
    setIsLoading(true);
    setError(null);
    setRetryKey((k) => k + 1);
  };

  const goToPrevPage = () => setPageNumber((prev) => Math.max(prev - 1, 1));
  const goToNextPage = () => setPageNumber((prev) => Math.min(prev + 1, numPages));

  const zoomIn = () => setScale((prev) => Math.min(prev + 0.2, 3.0));
  const zoomOut = () => setScale((prev) => Math.max(prev - 0.2, 0.4));
  const zoomReset = () => setScale(1.2);
  const rotate = () => setRotation((prev) => (prev + 90) % 360);

  const toggleFullscreen = () => {
    if (!document.fullscreenElement) {
      containerRef.current?.requestFullscreen().catch(() => {});
      setIsFullscreen(true);
    } else {
      document.exitFullscreen().catch(() => {});
      setIsFullscreen(false);
    }
  };

  // Listen for fullscreen changes
  useEffect(() => {
    const handler = () => setIsFullscreen(!!document.fullscreenElement);
    document.addEventListener('fullscreenchange', handler);
    return () => document.removeEventListener('fullscreenchange', handler);
  }, []);

  const handleTextSelection = () => {
    if (!isHighlightMode) return;
    const selection = window.getSelection();
    if (!selection || selection.isCollapsed || !selection.toString().trim()) return;

    const range = selection.getRangeAt(0);
    const rects = range.getClientRects();
    if (rects.length === 0) return;

    const containerRect = pageRef.current?.getBoundingClientRect();
    if (!containerRect) return;

    const color = activeHighlightColor;
    const newHighlights: Highlight[] = [];

    for (let i = 0; i < rects.length; i++) {
      const rect = rects[i];
      newHighlights.push({
        id: `hl-${Date.now()}-${i}`,
        pageNumber,
        x: rect.left - containerRect.left,
        y: rect.top - containerRect.top,
        width: rect.width,
        height: rect.height,
        color,
        text: selection.toString().trim(),
      });
    }

    setHighlights((prev) => [...prev, ...newHighlights]);
    selection.removeAllRanges();
  };

  const removeHighlight = (id: string) => {
    setHighlights((prev) => prev.filter((h) => h.id !== id));
  };

  const clearAllHighlights = () => {
    if (highlights.length === 0) return;
    if (window.confirm('确定清除当前论文的所有标注吗？')) {
      setHighlights([]);
    }
  };

  // Keyboard shortcuts
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement) return;
      switch (e.key) {
        case 'ArrowLeft': e.preventDefault(); goToPrevPage(); break;
        case 'ArrowRight': e.preventDefault(); goToNextPage(); break;
        case '+':
        case '=':
          if (e.ctrlKey || e.metaKey) { e.preventDefault(); zoomIn(); }
          break;
        case '-':
          if (e.ctrlKey || e.metaKey) { e.preventDefault(); zoomOut(); }
          break;
        case '0':
          if (e.ctrlKey || e.metaKey) { e.preventDefault(); zoomReset(); }
          break;
        case 'Escape':
          if (isFullscreen) { document.exitFullscreen().catch(() => {}); }
          else if (isHighlightMode) { setIsHighlightMode(false); }
          break;
        case 'h':
          if (e.ctrlKey && e.shiftKey) { e.preventDefault(); setIsHighlightMode((p) => !p); }
          break;
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [numPages, pageNumber, scale, isFullscreen, isHighlightMode]);

  const pageHighlights = highlights.filter((h) => h.pageNumber === pageNumber);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center backdrop-overlay bg-black/24 p-2 backdrop-blur-sm"
      onClick={onClose}
    >
      <div
        ref={containerRef}
        className={`surface-solid modal-dialog flex h-full flex-col overflow-hidden rounded-2xl shadow-[var(--shadow-lg)] ${
          isFullscreen ? 'max-h-full max-w-full' : 'max-h-[95vh] w-full max-w-6xl'
        }`}
        onClick={(event) => event.stopPropagation()}
      >
        {/* ── Toolbar ── */}
        <header className="flex flex-wrap items-center gap-2 border-b border-[var(--color-border)] px-4 py-2.5">
          {/* Paper info */}
          <div className="mr-2 flex min-w-0 items-center gap-2">
            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-[var(--color-border)] bg-[var(--color-primary-soft)] text-[var(--color-ink)]">
              <FileText size={15} />
            </div>
            <div className="min-w-0 hidden sm:block">
              <h2 className="line-clamp-1 text-sm font-bold text-[var(--color-ink)]">{paper.title}</h2>
            </div>
          </div>

          <div className="h-6 w-px bg-[var(--color-border)]" />

          {/* Page navigation */}
          <div className="flex items-center gap-1">
            <button onClick={goToPrevPage} disabled={pageNumber <= 1} className="icon-button h-8 w-8" aria-label="上一页">
              <ChevronLeft size={15} />
            </button>
            <span className="min-w-[80px] text-center text-xs font-semibold text-[var(--color-ink-soft)]">
              {pageNumber} / {numPages || '—'}
            </span>
            <button onClick={goToNextPage} disabled={pageNumber >= numPages} className="icon-button h-8 w-8" aria-label="下一页">
              <ChevronRight size={15} />
            </button>
          </div>

          <div className="h-6 w-px bg-[var(--color-border)]" />

          {/* Zoom controls */}
          <div className="flex items-center gap-1">
            <button onClick={zoomOut} disabled={scale <= 0.4} className="icon-button h-8 w-8" aria-label="缩小" title="缩小 (Ctrl+-)">
              <Minus size={14} />
            </button>
            <button onClick={zoomReset} className="px-2 py-1 text-xs font-bold text-[var(--color-ink-soft)] min-w-[48px] text-center tabular-nums" title="重置缩放 (Ctrl+0)">
              {Math.round(scale * 100)}%
            </button>
            <button onClick={zoomIn} disabled={scale >= 3.0} className="icon-button h-8 w-8" aria-label="放大" title="放大 (Ctrl++)">
              <Plus size={14} />
            </button>
          </div>

          <div className="h-6 w-px bg-[var(--color-border)]" />

          {/* Highlight tools */}
          <div className="flex items-center gap-1">
            <button
              onClick={() => setIsHighlightMode((p) => !p)}
              className={`icon-button h-8 w-8 ${isHighlightMode ? 'bg-[var(--color-primary)] text-white border-[var(--color-primary)]' : ''}`}
              aria-label="标注模式"
              title="标注模式 (Ctrl+Shift+H)"
            >
              <Highlighter size={15} />
            </button>
            {isHighlightMode && (
              <div className="flex items-center gap-1">
                {HIGHLIGHT_COLORS.map((color) => (
                  <button
                    key={color}
                    onClick={() => setActiveHighlightColor(color)}
                    className={`h-6 w-6 rounded-full border-2 transition ${
                      activeHighlightColor === color ? 'border-[var(--color-ink)] scale-110' : 'border-transparent'
                    }`}
                    style={{ backgroundColor: color }}
                    aria-label={`高亮颜色 ${color}`}
                  />
                ))}
              </div>
            )}
            <button
              onClick={() => setShowHighlights((p) => !p)}
              className={`icon-button h-8 w-8 text-xs font-bold ${!showHighlights ? 'opacity-50' : ''}`}
              aria-label="显示/隐藏标注"
              title="显示/隐藏标注"
            >
              <span className="text-[10px]">{highlights.length}</span>
            </button>
            {highlights.length > 0 && (
              <button onClick={clearAllHighlights} className="icon-button h-8 w-8 text-red-500" aria-label="清除所有标注" title="清除所有标注">
                <X size={14} />
              </button>
            )}
          </div>

          <div className="h-6 w-px bg-[var(--color-border)]" />

          {/* Other tools */}
          <div className="flex items-center gap-1">
            <button onClick={rotate} className="icon-button h-8 w-8" aria-label="旋转" title="旋转 90°">
              <RotateCw size={15} />
            </button>
            <button onClick={toggleFullscreen} className="icon-button h-8 w-8" aria-label="全屏">
              {isFullscreen ? <Minimize2 size={15} /> : <Maximize2 size={15} />}
            </button>
          </div>

          {/* Spacer */}
          <div className="flex-1" />

          {/* Close */}
          <button onClick={onClose} className="icon-button h-8 w-8" aria-label="关闭">
            <X size={16} />
          </button>
        </header>

        {/* ── PDF Content ── */}
        <div
          className="flex-1 overflow-auto bg-[#525659]"
          onMouseUp={handleTextSelection}
        >
          {isLoading && (
            <div className="flex items-center justify-center gap-3 py-32 text-white/70">
              <Loader2 size={28} className="animate-spin" />
              <span className="text-sm">加载 PDF...</span>
            </div>
          )}

          {error && (
            <div className="flex flex-col items-center gap-4 py-24 text-center px-8">
              <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-red-500/20 text-red-400">
                <FileText size={24} />
              </div>
              <p className="text-sm font-semibold text-red-300 max-w-md">{error}</p>
              <p className="text-xs text-white/40">
                论文状态：{paper.status === 'READY' ? '就绪' : paper.status}
                {paper.status !== 'READY' && ' — 请等待论文处理完成后再试'}
              </p>
              <div className="flex gap-3 mt-2">
                <button onClick={handleRetry} className="secondary-button px-4 py-2 text-xs">
                  <RotateCw size={14} />
                  重试
                </button>
                <button onClick={onClose} className="danger-button px-4 py-2 text-xs">
                  关闭
                </button>
              </div>
            </div>
          )}

          {!error && (
            <div className="flex justify-center py-4">
              <div ref={pageRef} className="relative inline-block" style={{ transform: `rotate(${rotation}deg)` }}>
                <Document
                  key={retryKey}
                  file={pdfUrl}
                  onLoadSuccess={onDocumentLoadSuccess}
                  onLoadError={onDocumentLoadError}
                  loading={
                    <div className="flex items-center justify-center gap-3 py-20 text-white/60">
                      <Loader2 size={24} className="animate-spin" />
                    </div>
                  }
                >
                  <Page
                    pageNumber={pageNumber}
                    scale={scale}
                    renderTextLayer={true}
                    renderAnnotationLayer={true}
                    className="shadow-2xl"
                  />
                </Document>

                {/* Highlight overlay */}
                {showHighlights &&
                  pageHighlights.map((hl) => (
                    <div
                      key={hl.id}
                      className="absolute pointer-events-none group"
                      style={{
                        left: hl.x,
                        top: hl.y,
                        width: hl.width,
                        height: hl.height,
                        backgroundColor: hl.color,
                        opacity: 0.45,
                        mixBlendMode: 'multiply',
                      }}
                      title={hl.text}
                    >
                      {/* Delete button on hover */}
                      <button
                        className="pointer-events-auto absolute -right-1.5 -top-1.5 flex h-4 w-4 items-center justify-center rounded-full bg-red-500 text-white opacity-0 transition group-hover:opacity-100"
                        onClick={(e) => {
                          e.stopPropagation();
                          removeHighlight(hl.id);
                        }}
                        aria-label="删除标注"
                      >
                        <X size={10} />
                      </button>
                    </div>
                  ))}
              </div>
            </div>
          )}
        </div>

        {/* ── Footer status bar ── */}
        <footer className="flex items-center justify-between border-t border-[var(--color-border)] px-4 py-1.5 text-[11px] text-[var(--color-ink-mute)]">
          <span>{paper.filename}</span>
          <span>
            {isHighlightMode && '标注模式已开启 — 选中文字即可高亮 · '}
            {highlights.length > 0 && `${highlights.length} 处标注 · `}
            快捷键: ← → 翻页 · Ctrl+/- 缩放 · Ctrl+Shift+H 标注
          </span>
        </footer>
      </div>
    </div>
  );
}
