import { create } from 'zustand';
import type { ChatMessage } from '../types';
import { streamChat, traceableChat } from '../api/client';

let counter = 0;
const genId = () => `msg_${Date.now()}_${++counter}`;

type ChatScope = 'paper' | 'library';

interface ChatState {
  messages: ChatMessage[];
  isLoading: boolean;
  selectedPaperId: number | null;
  scope: ChatScope;

  addMessage: (msg: ChatMessage) => void;
  updateLastMessage: (content: string) => void;
  setLoading: (loading: boolean) => void;
  setSelectedPaper: (paperId: number | null) => void;
  setScope: (scope: ChatScope) => void;
  clearMessages: () => void;
  sendMessage: (content: string) => Promise<void>;
}

export const useChatStore = create<ChatState>((set, get) => ({
  messages: [],
  isLoading: false,
  selectedPaperId: null,
  scope: 'paper',

  addMessage: (msg) => set((state) => ({ messages: [...state.messages, msg] })),

  updateLastMessage: (content) =>
    set((state) => {
      const messages = [...state.messages];
      const last = messages[messages.length - 1];
      if (last && last.role === 'assistant') {
        messages[messages.length - 1] = { ...last, content: last.content + content };
      }
      return { messages };
    }),

  setLoading: (loading) => set({ isLoading: loading }),
  setSelectedPaper: (paperId) => set({ selectedPaperId: paperId, scope: paperId ? 'paper' : get().scope }),
  setScope: (scope) => set({ scope }),
  clearMessages: () => set({ messages: [] }),

  sendMessage: async (content: string) => {
    const { selectedPaperId, scope, addMessage, updateLastMessage, setLoading } = get();
    const finishLastAssistant = () => {
      set((state) => {
        const messages = [...state.messages];
        const last = messages[messages.length - 1];
        if (last && last.role === 'assistant') {
          messages[messages.length - 1] = { ...last, isStreaming: false };
        }
        return { messages };
      });
    };

    addMessage({ id: genId(), role: 'user', content, timestamp: Date.now() });
    setLoading(true);

    const shouldUseTraceableChat = scope === 'library' || selectedPaperId !== null;

    if (shouldUseTraceableChat) {
      addMessage({ id: genId(), role: 'assistant', content: '', timestamp: Date.now(), isStreaming: true });
      try {
        const response = await traceableChat({
          message: content,
          paperId: selectedPaperId,
          scope,
        });
        set((state) => {
          const messages = [...state.messages];
          const last = messages[messages.length - 1];
          if (last && last.role === 'assistant') {
            messages[messages.length - 1] = {
              ...last,
              content: response.answer,
              evidence: response.evidence,
              isStreaming: false,
            };
          }
          return { messages };
        });
      } catch (err) {
        const message = err instanceof Error ? err.message : '请求失败';
        updateLastMessage(`请求失败：${message}`);
        finishLastAssistant();
      } finally {
        setLoading(false);
      }
      return;
    }

    addMessage({ id: genId(), role: 'assistant', content: '', timestamp: Date.now(), isStreaming: true });
    streamChat(
      { message: content, paperId: selectedPaperId },
      (chunk) => updateLastMessage(chunk),
      () => {
        setLoading(false);
        finishLastAssistant();
      },
      (err) => {
        console.error('Chat error:', err);
        setLoading(false);
        updateLastMessage(`请求失败：${err.message}`);
        finishLastAssistant();
      }
    );
  },
}));
