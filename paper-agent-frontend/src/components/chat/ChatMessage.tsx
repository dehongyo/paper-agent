import type { ChatMessage as ChatMessageType } from '../../types';
import ReactMarkdown from 'react-markdown';
import { EvidenceList } from './EvidenceList';

interface Props {
  message: ChatMessageType;
  onSelectPaper?: (paperId: number) => void;
}

export function ChatMessage({ message, onSelectPaper }: Props) {
  const isUser = message.role === 'user';

  return (
    <div className={`flex gap-3 py-3 ${isUser ? 'flex-row-reverse' : ''}`}>
      <div className={`avatar-chip ${isUser ? 'avatar-user' : 'avatar-assistant'}`}>
        {isUser ? '我' : 'AI'}
      </div>

      <div
        className={`message-bubble max-w-[82%] px-4 py-3 text-sm leading-7 text-[var(--color-ink)] ${
          isUser ? 'message-bubble-user' : 'message-bubble-assistant'
        }`}
      >
        <div className="prose prose-sm max-w-none prose-p:my-1 prose-li:my-0">
          {message.content ? (
            <ReactMarkdown>{message.content}</ReactMarkdown>
          ) : message.isStreaming ? (
            <span className="inline-block animate-pulse text-[var(--color-primary)]">生成中...</span>
          ) : (
            <span className="italic text-[var(--color-ink-mute)]">暂无内容</span>
          )}
        </div>

        {!isUser && <EvidenceList evidence={message.evidence} onSelectPaper={onSelectPaper} />}
      </div>
    </div>
  );
}
