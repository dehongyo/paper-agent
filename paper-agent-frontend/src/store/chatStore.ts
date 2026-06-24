import { create } from 'zustand';
import type { ChatMessage, ChatMessageResponse, ChatSession, ChatSessionCreateRequest, EvidenceChunk } from '../types';
import { createChatSession, getChatMessages, streamChat, streamTraceableChat } from '../api/client';

let counter = 0;
const genId = () => `msg_${Date.now()}_${++counter}`;

type ChatScope = 'paper' | 'library';

interface ChatState {
  messages: ChatMessage[];
  isLoading: boolean;
  selectedPaperId: number | null;
  scope: ChatScope;
  activeSessionId: number | null;
  activeSessionTitle: string | null;

  addMessage: (msg: ChatMessage) => void;
  updateLastMessage: (content: string) => void;
  setLoading: (loading: boolean) => void;
  setSelectedPaper: (paperId: number | null) => void;
  setScope: (scope: ChatScope) => void;
  clearMessages: () => void;
  openSession: (session: ChatSession) => Promise<void>;
  createAndOpenSession: (request: ChatSessionCreateRequest) => Promise<ChatSession>;
  sendMessage: (content: string) => Promise<void>;
}

export const useChatStore = create<ChatState>((set, get) => ({
  messages: [],
  isLoading: false,
  selectedPaperId: null,
  scope: 'paper',
  activeSessionId: null,
  activeSessionTitle: null,

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
  setSelectedPaper: (paperId) =>
    set((state) => ({
      selectedPaperId: paperId,
      scope: paperId ? 'paper' : state.scope,
      activeSessionId: paperId === state.selectedPaperId ? state.activeSessionId : null,
      activeSessionTitle: paperId === state.selectedPaperId ? state.activeSessionTitle : null,
      messages: paperId === state.selectedPaperId ? state.messages : [],
    })),
  setScope: (scope) => set({ scope, activeSessionId: null, activeSessionTitle: null, messages: [] }),
  clearMessages: () => set({ messages: [], activeSessionId: null, activeSessionTitle: null }),

  openSession: async (session) => {
    const messages = await getChatMessages(session.id);
    set({
      activeSessionId: session.id,
      activeSessionTitle: session.title,
      selectedPaperId: session.paperId,
      scope: session.scope,
      messages: messages.map(toChatMessage),
    });
  },

  createAndOpenSession: async (request) => {
    const session = await createChatSession(request);
    await get().openSession(session);
    return session;
  },

  sendMessage: async (content: string) => {
    let { selectedPaperId, scope, activeSessionId } = get();
    const { addMessage, updateLastMessage, setLoading } = get();
    const setLastAssistantEvidence = (evidence: ChatMessage['evidence']) => {
      set((state) => {
        const messages = [...state.messages];
        const last = messages[messages.length - 1];
        if (last && last.role === 'assistant') {
          messages[messages.length - 1] = { ...last, evidence };
        }
        return { messages };
      });
    };
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

    if (!activeSessionId) {
      const session = await createChatSession({
        scope,
        paperId: scope === 'paper' ? selectedPaperId : null,
      });
      activeSessionId = session.id;
      selectedPaperId = session.paperId;
      scope = session.scope;
      set({
        activeSessionId: session.id,
        activeSessionTitle: session.title,
        selectedPaperId: session.paperId,
        scope: session.scope,
      });
    }

    addMessage({ id: genId(), role: 'user', content, timestamp: Date.now() });
    setLoading(true);

    const shouldUseTraceableChat = scope === 'library' || selectedPaperId !== null;

    addMessage({ id: genId(), role: 'assistant', content: '', timestamp: Date.now(), isStreaming: true });

    if (shouldUseTraceableChat) {
      streamTraceableChat(
        {
          message: content,
          paperId: selectedPaperId,
          scope,
          sessionId: activeSessionId,
        },
        (chunk) => updateLastMessage(chunk),
        (evidence) => setLastAssistantEvidence(evidence),
        () => {
          setLoading(false);
          finishLastAssistant();
        },
        (err) => {
          console.error('Traceable chat error:', err);
          setLoading(false);
          updateLastMessage(`请求失败：${err.message}`);
          finishLastAssistant();
        }
      );
      return;
    }

    streamChat(
      { message: content, paperId: selectedPaperId, sessionId: activeSessionId },
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

function toChatMessage(message: ChatMessageResponse): ChatMessage {
  return {
    id: `db_${message.id}`,
    role: message.role,
    content: message.content,
    timestamp: new Date(message.createdAt).getTime(),
    evidence: parseEvidence(message.evidenceJson),
  };
}

function parseEvidence(value: string | null): EvidenceChunk[] | undefined {
  if (!value) return undefined;
  try {
    const parsed = JSON.parse(value) as EvidenceChunk[];
    return Array.isArray(parsed) ? parsed : undefined;
  } catch {
    return undefined;
  }
}
