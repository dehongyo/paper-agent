import { useEffect, useRef, useState } from 'react';
import { ChatMessage } from './ChatMessage';
import { ChatInput } from './ChatInput';
import { SearchPanel } from './SearchPanel';
import { useChatStore } from '../../store/chatStore';
import { getChatSessions } from '../../api/client';
import type { ChatSession } from '../../types';
import {
  ArrowRight,
  BookOpen,
  Brain,
  History,
  Library,
  Loader2,
  MessageCircle,
  PanelRightClose,
  PanelRightOpen,
  Search,
  X,
} from 'lucide-react';
import { MemoryPanel } from './MemoryPanel';

const quickPrompts = [
  { icon: BookOpen, label: 'Summarize', prompt: 'Please summarize the core ideas and main contributions of this paper.' },
  { icon: Search, label: 'Methods', prompt: 'What research methods does this paper use? Please list and briefly explain them.' },
  { icon: Library, label: 'Compare', prompt: 'Compare this paper with related work in the same area.' },
  { icon: MessageCircle, label: 'Evidence', prompt: 'Explain the source evidence supporting the key conclusions in detail.' },
];

export function ChatWindow() {
  const {
    messages,
    isLoading,
    sendMessage,
    selectedPaperId,
    setSelectedPaper,
    scope,
    setScope,
    openSession,
    activeSessionId,
    activeSessionTitle,
  } = useChatStore();
  const bottomRef = useRef<HTMLDivElement>(null);
  const [showSearchPanel, setShowSearchPanel] = useState(false);
  const [showHistoryPanel, setShowHistoryPanel] = useState(false);
  const [showMemoryPanel, setShowMemoryPanel] = useState(false);
  const [historySessions, setHistorySessions] = useState<ChatSession[]>([]);
  const [isLoadingHistory, setIsLoadingHistory] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);

  useEffect(() => {
    const el = bottomRef.current;
    if (el) {
      const container = el.closest('.overflow-y-auto');
      if (container) container.scrollTop = container.scrollHeight;
    }
  }, [messages]);

  useEffect(() => {
    if (!showHistoryPanel) return;

    let cancelled = false;
    setIsLoadingHistory(true);
    setHistoryError(null);
    getChatSessions({
      scope,
      paperId: scope === 'paper' ? selectedPaperId : null,
    })
      .then((sessions) => {
        if (!cancelled) setHistorySessions(sessions);
      })
      .catch((err) => {
        if (!cancelled) setHistoryError(err instanceof Error ? err.message : 'Failed to load chat history');
      })
      .finally(() => {
        if (!cancelled) setIsLoadingHistory(false);
      });

    return () => {
      cancelled = true;
    };
  }, [showHistoryPanel, scope, selectedPaperId, activeSessionId]);

  const toggleHistoryPanel = () => {
    setShowHistoryPanel((prev) => !prev);
    setShowSearchPanel(false);
    setShowMemoryPanel(false);
  };

  const toggleSearchPanel = () => {
    setShowSearchPanel((prev) => !prev);
    setShowHistoryPanel(false);
    setShowMemoryPanel(false);
  };

  const toggleMemoryPanel = () => {
    setShowMemoryPanel((prev) => !prev);
    setShowHistoryPanel(false);
    setShowSearchPanel(false);
  };

  const openHistorySession = async (session: ChatSession) => {
    await openSession(session);
    setShowHistoryPanel(false);
  };

  const historyTitle = scope === 'library'
    ? 'Library history'
    : selectedPaperId
      ? `Paper #${selectedPaperId} history`
      : 'All paper history';

  return (
    <div className="flex h-full flex-col">
      <header className="chat-header">
        <div className="chat-header-inner">
          <div className="scope-switcher">
            <button
              type="button"
              onClick={() => setScope('paper')}
              className={`scope-button ${scope === 'paper' ? 'scope-button-active' : ''}`}
            >
              <BookOpen size={15} />
              Current paper
            </button>
            <button
              type="button"
              onClick={() => setScope('library')}
              className={`scope-button ${scope === 'library' ? 'scope-button-active' : ''}`}
            >
              <Library size={15} />
              All papers
            </button>
          </div>

          {selectedPaperId && (
            <div className="flex items-center gap-2 rounded-full border border-[var(--color-border)] bg-[var(--surface)] px-3 py-2 text-sm text-[var(--color-ink-soft)] shadow-sm">
              <span className="font-bold text-[var(--color-ink)]">Paper #{selectedPaperId}</span>
              <button
                type="button"
                onClick={() => setSelectedPaper(null)}
                className="rounded-full p-0.5 text-[var(--color-ink-mute)] hover:bg-[var(--color-primary-soft)] hover:text-[var(--color-ink)]"
                aria-label="Clear current paper"
              >
                <X size={14} />
              </button>
            </div>
          )}

          {activeSessionTitle && (
            <div className="min-w-0 flex-1 text-right text-sm font-bold text-[var(--color-ink-soft)]">
              <span className="line-clamp-1">{activeSessionTitle}</span>
            </div>
          )}

          <button
            type="button"
            onClick={toggleHistoryPanel}
            className={`icon-button ml-1 ${showHistoryPanel ? 'bg-[var(--color-primary-soft)] text-[var(--color-primary)]' : ''}`}
            aria-label={showHistoryPanel ? 'Close chat history' : 'Open chat history'}
            title="Chat history"
          >
            <History size={17} />
          </button>

          {activeSessionId && (
            <button
              type="button"
              onClick={toggleMemoryPanel}
              className={`icon-button ml-1 ${showMemoryPanel ? 'bg-[var(--color-primary-soft)] text-[var(--color-primary)]' : ''}`}
              aria-label={showMemoryPanel ? 'Close session memory' : 'Open session memory'}
              title="Session memory"
            >
              <Brain size={17} />
            </button>
          )}

          <button
            type="button"
            onClick={toggleSearchPanel}
            className={`icon-button ml-1 ${showSearchPanel ? 'bg-[var(--color-primary-soft)] text-[var(--color-primary)]' : ''}`}
            aria-label={showSearchPanel ? 'Close search panel' : 'Open search panel'}
            title="Semantic search"
          >
            {showSearchPanel ? <PanelRightClose size={17} /> : <PanelRightOpen size={17} />}
          </button>
        </div>
      </header>

      <div className="min-h-0 flex flex-1">
        <div className="flex min-h-0 flex-1 flex-col">
          <div className="min-h-0 flex-1 overflow-y-auto px-7">
            <div className="mx-auto h-full max-w-5xl">
              {messages.length === 0 ? (
                <div className="empty-hero">
                  <div className="empty-hero-panel">
                    <div className="mx-auto mb-5 flex h-12 w-12 items-center justify-center rounded-[var(--radius-sm)] border border-[var(--glass-border-strong)] bg-[var(--surface)] text-[var(--color-ink)] shadow-sm">
                      <MessageCircle size={22} />
                    </div>
                    <p className="eyebrow">RESEARCH COPILOT</p>
                    <h2 className="mt-2 font-[var(--font-display)] text-[30px] font-bold leading-tight text-[var(--color-ink)]">
                      Chat with papers
                    </h2>
                    <p className="mx-auto mt-3 max-w-[440px] text-[14px] leading-6 text-[var(--color-ink-mute)]">
                      Ask about the current paper or the full library. Answers include traceable evidence.
                    </p>

                    <div className="quick-chips">
                      {quickPrompts.map((item) => {
                        const Icon = item.icon;
                        return (
                          <button
                            key={item.label}
                            type="button"
                            onClick={() => sendMessage(item.prompt)}
                            className="quick-chip"
                          >
                            <Icon size={14} />
                            {item.label}
                            <ArrowRight size={12} />
                          </button>
                        );
                      })}
                    </div>
                  </div>
                </div>
              ) : (
                <div className="py-6">
                  {messages.map((msg) => (
                    <ChatMessage key={msg.id} message={msg} onSelectPaper={setSelectedPaper} />
                  ))}
                </div>
              )}
              <div ref={bottomRef} />
            </div>
          </div>

          <ChatInput onSend={sendMessage} isLoading={isLoading} />
        </div>

        {showHistoryPanel && (
          <aside className="flex w-80 shrink-0 flex-col border-l border-[var(--color-border)] bg-[var(--surface)]">
            <header className="flex items-center justify-between gap-3 border-b border-[var(--color-border)] px-4 py-3">
              <div className="min-w-0">
                <p className="eyebrow">CHAT HISTORY</p>
                <h2 className="line-clamp-1 text-sm font-bold text-[var(--color-ink)]">{historyTitle}</h2>
              </div>
              <button
                type="button"
                onClick={() => setShowHistoryPanel(false)}
                className="icon-button h-8 w-8"
                aria-label="Close chat history"
              >
                <X size={15} />
              </button>
            </header>

            <div className="min-h-0 flex-1 overflow-y-auto p-3">
              {isLoadingHistory && (
                <div className="flex items-center justify-center gap-2 py-10 text-sm text-[var(--color-ink-mute)]">
                  <Loader2 size={17} className="animate-spin" />
                  Loading history...
                </div>
              )}

              {!isLoadingHistory && historyError && (
                <div className="rounded-[var(--radius-sm)] border border-red-200 bg-red-50 px-3 py-4 text-sm font-semibold text-red-700">
                  {historyError}
                </div>
              )}

              {!isLoadingHistory && !historyError && historySessions.length === 0 && (
                <div className="rounded-[var(--radius-sm)] border border-[var(--color-border)] bg-[var(--color-primary-soft)] px-3 py-8 text-center text-sm text-[var(--color-ink-mute)]">
                  No chat history yet.
                </div>
              )}

              {!isLoadingHistory && !historyError && historySessions.length > 0 && (
                <div className="space-y-2">
                  {historySessions.map((session) => {
                    const active = session.id === activeSessionId;
                    return (
                      <button
                        key={session.id}
                        type="button"
                        onClick={() => void openHistorySession(session)}
                        className={`w-full rounded-[var(--radius-sm)] border px-3 py-3 text-left transition ${
                          active
                            ? 'border-[var(--color-border-strong)] bg-[var(--color-primary-soft)]'
                            : 'border-[var(--color-border)] bg-[var(--surface)] hover:border-[var(--color-border-strong)] hover:bg-[var(--color-primary-soft)]'
                        }`}
                      >
                        <span className="line-clamp-2 text-sm font-bold text-[var(--color-ink)]">{session.title}</span>
                        <span className="mt-1 block text-xs text-[var(--color-ink-mute)]">
                          {session.scope === 'paper'
                            ? session.paperId
                              ? `Paper #${session.paperId}`
                              : 'No paper selected'
                            : 'All papers'}
                        </span>
                        <span className="mt-1 block text-[11px] text-[var(--color-ink-mute)]">
                          {new Date(session.updatedAt).toLocaleString('zh-CN')}
                        </span>
                      </button>
                    );
                  })}
                </div>
              )}
            </div>
          </aside>
        )}

        {showSearchPanel && (
          <div className="w-80 shrink-0 border-l border-[var(--color-border)]">
            <SearchPanel onSelectPaper={setSelectedPaper} />
          </div>
        )}

        {showMemoryPanel && activeSessionId && (
          <MemoryPanel sessionId={activeSessionId} onClose={() => setShowMemoryPanel(false)} />
        )}
      </div>
    </div>
  );
}
