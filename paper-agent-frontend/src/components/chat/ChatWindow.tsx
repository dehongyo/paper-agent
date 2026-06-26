import { useEffect, useRef, useState } from 'react';
import { ChatMessage } from './ChatMessage';
import { ChatInput } from './ChatInput';
import { SearchPanel } from './SearchPanel';
import { useChatStore } from '../../store/chatStore';
import { ArrowRight, BookOpen, Library, MessageCircle, PanelRightClose, PanelRightOpen, Search, X } from 'lucide-react';

const quickPrompts = [
  { icon: BookOpen, label: '总结核心观点', prompt: '请总结这篇论文的核心观点和主要贡献。' },
  { icon: Search, label: '梳理研究方法', prompt: '这篇论文使用了哪些研究方法？请列举并简要说明。' },
  { icon: Library, label: '对比相关研究', prompt: '请对比本文与相关领域其他研究的异同。' },
  { icon: MessageCircle, label: '追问来源证据', prompt: '请详细说明论文中关键结论的来源证据和支撑数据。' },
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
    activeSessionTitle,
  } = useChatStore();
  const bottomRef = useRef<HTMLDivElement>(null);
  const [showSearchPanel, setShowSearchPanel] = useState(false);

  useEffect(() => {
    const el = bottomRef.current;
    if (el) {
      const container = el.closest('.overflow-y-auto');
      if (container) container.scrollTop = container.scrollHeight;
    }
  }, [messages]);

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
              当前论文
            </button>
            <button
              type="button"
              onClick={() => setScope('library')}
              className={`scope-button ${scope === 'library' ? 'scope-button-active' : ''}`}
            >
              <Library size={15} />
              全部文献库
            </button>
          </div>

          {selectedPaperId && (
            <div className="flex items-center gap-2 rounded-full border border-[var(--color-border)] bg-[var(--surface)] px-3 py-2 text-sm text-[var(--color-ink-soft)] shadow-sm">
              <span className="font-bold text-[var(--color-ink)]">论文 #{selectedPaperId}</span>
              <button
                type="button"
                onClick={() => setSelectedPaper(null)}
                className="rounded-full p-0.5 text-[var(--color-ink-mute)] hover:bg-[var(--color-primary-soft)] hover:text-[var(--color-ink)]"
                aria-label="取消当前论文"
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
            onClick={() => setShowSearchPanel((prev) => !prev)}
            className={`icon-button ml-1 ${showSearchPanel ? 'bg-[var(--color-primary-soft)] text-[var(--color-primary)]' : ''}`}
            aria-label={showSearchPanel ? '关闭搜索面板' : '打开搜索面板'}
            title="语义搜索文献库"
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
                <h2 className="mt-2 font-[var(--font-display)] text-[30px] font-bold leading-tight tracking-[-0.01em] text-[var(--color-ink)]">
                  和论文自然对话
                </h2>
                <p className="mx-auto mt-3 max-w-[440px] text-[14px] leading-6 text-[var(--color-ink-mute)]">
                  围绕当前论文或全部文献库提问，答案附带可追溯来源证据。
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

        {showSearchPanel && (
          <div className="w-80 shrink-0 border-l border-[var(--color-border)]">
            <SearchPanel onSelectPaper={setSelectedPaper} />
          </div>
        )}
      </div>
    </div>
  );
}
