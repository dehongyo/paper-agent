import { useEffect, useRef } from 'react';
import { ChatMessage } from './ChatMessage';
import { ChatInput } from './ChatInput';
import { useChatStore } from '../../store/chatStore';
import { BookOpen, Library, MessageCircle, X } from 'lucide-react';

export function ChatWindow() {
  const {
    messages,
    isLoading,
    sendMessage,
    selectedPaperId,
    setSelectedPaper,
    scope,
    setScope,
  } = useChatStore();
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  return (
    <div className="flex h-full flex-col">
      <header className="border-b border-[var(--color-border)] bg-white/58 px-5 py-3 backdrop-blur-2xl">
        <div className="mx-auto flex max-w-5xl flex-wrap items-center gap-3">
          <div className="flex rounded-lg border border-[var(--color-border)] bg-black/[0.035] p-1">
            <button
              type="button"
              onClick={() => setScope('paper')}
              className={`flex items-center gap-2 rounded-md px-3 py-1.5 text-sm font-semibold transition-all ${
                scope === 'paper' ? 'bg-white text-[var(--color-primary)] shadow-sm' : 'text-[var(--color-ink-mute)]'
              }`}
            >
              <BookOpen size={15} />
              当前论文
            </button>
            <button
              type="button"
              onClick={() => setScope('library')}
              className={`flex items-center gap-2 rounded-md px-3 py-1.5 text-sm font-semibold transition-all ${
                scope === 'library' ? 'bg-white text-[var(--color-primary)] shadow-sm' : 'text-[var(--color-ink-mute)]'
              }`}
            >
              <Library size={15} />
              全部文献库
            </button>
          </div>

          {selectedPaperId && (
            <div className="flex items-center gap-2 rounded-lg border border-[var(--color-border)] bg-white/70 px-3 py-1.5 text-sm text-[var(--color-ink-soft)] shadow-sm">
              <span className="font-semibold text-[var(--color-primary)]">论文 #{selectedPaperId}</span>
              <button
                type="button"
                onClick={() => setSelectedPaper(null)}
                className="rounded-md p-0.5 text-[var(--color-ink-mute)] hover:bg-black/5 hover:text-[var(--color-ink)]"
                aria-label="取消当前论文"
              >
                <X size={14} />
              </button>
            </div>
          )}
        </div>
      </header>

      <div className="min-h-0 flex-1 overflow-y-auto px-5">
        <div className="mx-auto h-full max-w-5xl">
          {messages.length === 0 ? (
            <div className="empty-hero">
              <div className="empty-hero-panel">
                <div className="mx-auto mb-6 flex h-16 w-16 items-center justify-center rounded-[18px] border border-[var(--color-border)] bg-white/74 text-[var(--color-primary)] shadow-[var(--shadow-md)] backdrop-blur-xl">
                  <MessageCircle size={28} />
                </div>
                <p className="eyebrow">RESEARCH COPILOT</p>
                <h2 className="mt-2 text-[34px] font-bold leading-tight tracking-tight text-[var(--color-ink)]">和论文自然对话</h2>
                <p className="mx-auto mt-3 max-w-md text-sm leading-6 text-[var(--color-ink-mute)]">
                  围绕当前论文或全部文献库提问，答案会附带可追溯来源证据。
                </p>
              </div>
            </div>
          ) : (
            <div className="py-4">
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
  );
}
