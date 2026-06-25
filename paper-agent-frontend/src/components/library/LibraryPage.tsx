import { useCallback, useEffect, useState } from 'react';
import { PaperCard } from './PaperCard';
import { PaperEditPanel } from './PaperEditPanel';
import { PaperUpload } from './PaperUpload';
import { deletePaper, getChatSessions, getPaper, getPapers, getTags, updatePaper } from '../../api/client';
import { useChatStore } from '../../store/chatStore';
import type { ChatSession, PaperListItem, PaperSummary, PaperUpdateRequest } from '../../types';
import type { View } from '../layout/Layout';
import { Filter, Loader2, Library, MessageCircle, Plus, RotateCcw, Search, X } from 'lucide-react';

interface Props {
  onNavigate: (view: View) => void;
}

export function LibraryPage({ onNavigate }: Props) {
  const [papers, setPapers] = useState<PaperListItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('');
  const [tag, setTag] = useState('');
  const [tags, setTags] = useState<string[]>([]);
  const [editingPaper, setEditingPaper] = useState<PaperSummary | null>(null);
  const [chatPaper, setChatPaper] = useState<PaperListItem | null>(null);
  const [chatSessions, setChatSessions] = useState<ChatSession[]>([]);
  const [isLoadingSessions, setIsLoadingSessions] = useState(false);
  const openSession = useChatStore((state) => state.openSession);
  const createAndOpenSession = useChatStore((state) => state.createAndOpenSession);

  const loadPapers = useCallback(async (filters = { query, status, tag }) => {
    try {
      setIsLoading(true);
      setPapers(await getPapers({
        query: filters.query.trim() || undefined,
        status: filters.status || undefined,
        tag: filters.tag || undefined,
      }));
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载失败');
    } finally {
      setIsLoading(false);
    }
  }, [query, status, tag]);

  const loadTags = useCallback(async () => {
    try {
      setTags(await getTags());
    } catch (err) {
      console.error('加载标签失败:', err);
    }
  }, []);

  useEffect(() => {
    window.setTimeout(() => {
      void loadPapers({ query: '', status: '', tag: '' });
      void loadTags();
    }, 0);
  }, [loadPapers, loadTags]);

  const handleChat = async (paperId: number) => {
    const paper = papers.find((item) => item.id === paperId);
    if (!paper) return;
    setChatPaper(paper);
    setIsLoadingSessions(true);
    try {
      setChatSessions(await getChatSessions({ scope: 'paper', paperId }));
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载历史对话失败');
    } finally {
      setIsLoadingSessions(false);
    }
  };

  const enterSession = async (session: ChatSession) => {
    await openSession(session);
    setChatPaper(null);
    onNavigate('chat');
  };

  const createSession = async () => {
    if (!chatPaper) return;
    await createAndOpenSession({
      title: `${chatPaper.title} 对话`,
      scope: 'paper',
      paperId: chatPaper.id,
    });
    setChatPaper(null);
    onNavigate('chat');
  };

  const handleEdit = async (paperId: number) => {
    try {
      setEditingPaper(await getPaper(paperId));
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : '打开编辑面板失败');
    }
  };

  const handleSave = async (payload: PaperUpdateRequest) => {
    if (!editingPaper) return;
    setIsSaving(true);
    try {
      await updatePaper(editingPaper.id, payload);
      setEditingPaper(null);
      await loadPapers();
      await loadTags();
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存失败');
    } finally {
      setIsSaving(false);
    }
  };

  const handleDelete = async (paperId: number) => {
    if (!window.confirm('确定删除这篇论文吗？')) return;
    try {
      await deletePaper(paperId);
      await loadPapers();
      await loadTags();
    } catch (err) {
      setError(err instanceof Error ? err.message : '删除失败');
    }
  };

  const applyFilters = () => void loadPapers();

  const resetFilters = () => {
    setQuery('');
    setStatus('');
    setTag('');
    void loadPapers({ query: '', status: '', tag: '' });
  };

  const handleUploaded = (paper: PaperSummary) => {
    setPapers((prev) => [{
      id: paper.id,
      title: paper.title,
      authors: paper.authors,
      filename: paper.filename,
      status: paper.status,
      summary: paper.summary,
      tags: paper.tags,
      doi: paper.doi,
      sourceUrl: paper.sourceUrl,
      publishedAt: paper.publishedAt,
      createdAt: paper.createdAt,
    }, ...prev]);
    void loadTags();
  };

  return (
    <div className="page-shell">
      <div className="page-content">
        <header className="page-header">
          <div>
            <p className="eyebrow">LOCAL LIBRARY</p>
            <h1 className="page-title">文献库</h1>
            <p className="page-subtitle">管理已上传论文、标签、笔记和可追溯问答上下文。</p>
          </div>
          <PaperUpload onUploaded={handleUploaded} />
        </header>

        <div className="surface mb-6 grid gap-3 p-3 lg:grid-cols-[1fr_160px_160px_auto]">
          <label className="relative">
            <Search size={16} className="search-icon" />
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') applyFilters();
              }}
              placeholder="搜索标题、作者或摘要"
              className="control search-control text-sm"
            />
          </label>
          <select value={status} onChange={(event) => setStatus(event.target.value)} className="control px-3 text-sm">
            <option value="">全部状态</option>
            <option value="UPLOADED">已上传</option>
            <option value="PARSING">解析中</option>
            <option value="PARSED">已解析</option>
            <option value="EMBEDDING">向量化中</option>
            <option value="READY">就绪</option>
            <option value="ERROR">错误</option>
          </select>
          <select value={tag} onChange={(event) => setTag(event.target.value)} className="control px-3 text-sm">
            <option value="">全部标签</option>
            {tags.map((item) => (
              <option key={item} value={item}>{item}</option>
            ))}
          </select>
          <div className="flex gap-2">
            <button type="button" onClick={applyFilters} className="primary-button">
              <Filter size={16} />
              筛选
            </button>
            <button type="button" onClick={resetFilters} className="icon-button" aria-label="重置筛选">
              <RotateCcw size={16} />
            </button>
          </div>
        </div>

        {isLoading && (
          <div className="flex justify-center py-16 text-[var(--color-ink-mute)]">
            <Loader2 size={24} className="animate-spin" />
          </div>
        )}

        {error && (
          <div className="surface-solid py-12 text-center">
            <p className="text-sm font-semibold text-red-600">{error}</p>
            <button onClick={() => void loadPapers()} className="mt-3 text-sm font-bold text-[var(--color-ink)]">
              重试
            </button>
          </div>
        )}

        {!isLoading && !error && papers.length === 0 && (
          <div className="surface flex flex-col items-center gap-3 py-16 text-[var(--color-ink-mute)]">
            <Library size={48} strokeWidth={1.2} />
            <p className="font-bold text-[var(--color-ink)]">还没有符合条件的论文</p>
            <p className="text-sm">上传论文或调整筛选条件后再试。</p>
          </div>
        )}

        {!isLoading && papers.length > 0 && (
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {papers.map((paper) => (
              <PaperCard
                key={paper.id}
                paper={paper}
                onChat={handleChat}
                onEdit={handleEdit}
                onDelete={handleDelete}
              />
            ))}
          </div>
        )}

        {editingPaper && (
          <PaperEditPanel
            paper={editingPaper}
            isSaving={isSaving}
            onClose={() => setEditingPaper(null)}
            onSave={handleSave}
          />
        )}

        {chatPaper && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/20 px-4 backdrop-blur-sm">
            <div className="surface-solid w-full max-w-xl p-5 shadow-[var(--shadow-lg)]">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="eyebrow">PAPER CHAT HISTORY</p>
                  <h2 className="mt-1 line-clamp-2 text-xl font-extrabold text-[var(--color-ink)]">{chatPaper.title}</h2>
                  <p className="mt-2 text-sm text-[var(--color-ink-mute)]">选择一个历史会话继续，或新建会话。</p>
                </div>
                <button
                  type="button"
                  onClick={() => setChatPaper(null)}
                  className="icon-button"
                  aria-label="关闭历史对话"
                >
                  <X size={16} />
                </button>
              </div>

              <div className="mt-5 space-y-2">
                {isLoadingSessions && (
                  <div className="flex items-center justify-center gap-2 py-8 text-sm text-[var(--color-ink-mute)]">
                    <Loader2 size={18} className="animate-spin" />
                    加载历史对话...
                  </div>
                )}

                {!isLoadingSessions && chatSessions.length === 0 && (
                  <div className="rounded-[var(--radius-sm)] border border-dashed border-[var(--color-border)] bg-[var(--color-primary-soft)] px-4 py-8 text-center text-sm text-[var(--color-ink-mute)]">
                    这篇论文还没有历史对话。
                  </div>
                )}

                {!isLoadingSessions && chatSessions.map((session) => (
                  <button
                    key={session.id}
                    type="button"
                    onClick={() => void enterSession(session)}
                    className="flex w-full items-center justify-between gap-3 rounded-[var(--radius-sm)] border border-[var(--color-border)] bg-[var(--surface)] px-4 py-3 text-left transition hover:border-[var(--color-border-strong)] hover:bg-[var(--color-primary-soft)]"
                  >
                    <span>
                      <span className="block text-sm font-bold text-[var(--color-ink)]">{session.title}</span>
                      <span className="mt-1 block text-xs text-[var(--color-ink-mute)]">
                        最近更新 {new Date(session.updatedAt).toLocaleString('zh-CN')}
                      </span>
                    </span>
                    <MessageCircle size={17} className="text-[var(--color-ink)]" />
                  </button>
                ))}
              </div>

              <button type="button" onClick={() => void createSession()} className="primary-button mt-5 w-full">
                <Plus size={16} />
                新建会话
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
