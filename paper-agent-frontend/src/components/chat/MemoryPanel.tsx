import { useEffect, useState } from 'react';
import { Brain, Loader2, Trash2, X, Sparkles, Target, CheckCircle2, HelpCircle, Bookmark } from 'lucide-react';
import { getSessionMemory, deleteMemory } from '../../api/client';
import type { SessionMemoryResponse, MemoryEntry } from '../../types';

interface Props {
  sessionId: number;
  onClose: () => void;
}

const TYPE_LABELS: Record<string, string> = {
  user_preference: '偏好',
  confirmed_decision: '决策',
  project_goal: '目标',
  paper_fact: '论文事实',
  workflow_rule: '工作流',
  open_question: '问题',
  research_topic: '研究主题',
  methodology_choice: '方法选择',
};

const TYPE_ICONS: Record<string, typeof Brain> = {
  user_preference: Sparkles,
  confirmed_decision: CheckCircle2,
  project_goal: Target,
  open_question: HelpCircle,
  paper_fact: Bookmark,
};

export function MemoryPanel({ sessionId, onClose }: Props) {
  const [data, setData] = useState<SessionMemoryResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [stateExpanded, setStateExpanded] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    getSessionMemory(sessionId)
      .then((res) => { if (!cancelled) setData(res); })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Failed to load memory');
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [sessionId]);

  const parseState = (json: string | null) => {
    if (!json) return null;
    try { return JSON.parse(json); }
    catch { return null; }
  };

  const memoryIcon = (type: string) => {
    const Icon = TYPE_ICONS[type] || Bookmark;
    return <Icon size={12} />;
  };

  const memoryLabel = (type: string) => TYPE_LABELS[type] || type;

  const handleDeleteMemory = async (memoryId: number) => {
    try {
      await deleteMemory(sessionId, memoryId);
      // Refresh the panel
      setLoading(true);
      const res = await getSessionMemory(sessionId);
      setData(res);
    } catch (err) {
      console.error('Failed to delete memory:', err);
    } finally {
      setLoading(false);
    }
  };

  const percentBar = (value: number) => {
    const pct = Math.round(value * 100);
    const color = pct >= 80 ? 'bg-green-400' : pct >= 60 ? 'bg-yellow-400' : 'bg-gray-300';
    return (
      <div className="flex items-center gap-1.5">
        <div className="h-1.5 w-16 overflow-hidden rounded-full bg-gray-100">
          <div className={`h-full rounded-full ${color}`} style={{ width: `${pct}%` }} />
        </div>
        <span className="text-[10px] tabular-nums text-[var(--color-ink-mute)]">{pct}%</span>
      </div>
    );
  };

  return (
    <aside className="flex w-80 shrink-0 flex-col border-l border-[var(--color-border)] bg-[var(--surface)]">
      {/* Header */}
      <header className="flex items-center justify-between gap-3 border-b border-[var(--color-border)] px-4 py-3">
        <div className="flex items-center gap-2 min-w-0">
          <Brain size={17} className="text-[var(--color-primary)] shrink-0" />
          <div className="min-w-0">
            <p className="eyebrow">SESSION MEMORY</p>
            <h2 className="line-clamp-1 text-sm font-bold text-[var(--color-ink)]">
              Session #{sessionId}
            </h2>
          </div>
        </div>
        <button
          type="button"
          onClick={onClose}
          className="icon-button h-8 w-8"
          aria-label="Close memory panel"
        >
          <X size={15} />
        </button>
      </header>

      <div className="min-h-0 flex-1 overflow-y-auto p-3 space-y-4">
        {loading && (
          <div className="flex items-center justify-center gap-2 py-10 text-sm text-[var(--color-ink-mute)]">
            <Loader2 size={17} className="animate-spin" />
            Loading memory...
          </div>
        )}

        {!loading && error && (
          <div className="rounded-[var(--radius-sm)] border border-red-200 bg-red-50 px-3 py-4 text-sm font-semibold text-red-700">
            {error}
          </div>
        )}

        {!loading && !error && data && (
          <>
            {/* Stats row */}
            <div className="grid grid-cols-3 gap-2">
              <div className="rounded-[var(--radius-sm)] border border-[var(--color-border)] bg-[var(--color-bg-mute)] p-2 text-center">
                <div className="text-lg font-bold text-[var(--color-ink)]">{data.longTermMemories.length}</div>
                <div className="text-[10px] text-[var(--color-ink-mute)]">Memories</div>
              </div>
              <div className="rounded-[var(--radius-sm)] border border-[var(--color-border)] bg-[var(--color-bg-mute)] p-2 text-center">
                <div className="text-lg font-bold text-[var(--color-ink)]">{data.recentMessageCount}</div>
                <div className="text-[10px] text-[var(--color-ink-mute)]">Recent Msgs</div>
              </div>
              <div className="rounded-[var(--radius-sm)] border border-[var(--color-border)] bg-[var(--color-bg-mute)] p-2 text-center">
                <div className="text-lg font-bold text-[var(--color-ink)]">{data.rollingSummary ? 'Yes' : 'No'}</div>
                <div className="text-[10px] text-[var(--color-ink-mute)]">Summary</div>
              </div>
            </div>

            {/* Rolling Summary */}
            {data.rollingSummary && (
              <section>
                <h3 className="text-[11px] font-bold uppercase tracking-wide text-[var(--color-ink-mute)] mb-1.5">
                  Rolling Summary
                </h3>
                <div className="rounded-[var(--radius-sm)] border border-[var(--color-border)] bg-[var(--color-bg-mute)] p-2.5 text-xs leading-relaxed text-[var(--color-ink-soft)] max-h-32 overflow-y-auto whitespace-pre-wrap">
                  {data.rollingSummary}
                </div>
              </section>
            )}

            {/* State JSON */}
            {data.stateJson && (() => {
              const state = parseState(data.stateJson);
              return (
                <section>
                  <button
                    type="button"
                    onClick={() => setStateExpanded(!stateExpanded)}
                    className="flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wide text-[var(--color-ink-mute)] hover:text-[var(--color-ink)] transition"
                  >
                    <span className={`transition-transform ${stateExpanded ? 'rotate-90' : ''}`}>▸</span>
                    Session State
                  </button>
                  {stateExpanded && state && (
                    <div className="mt-1.5 rounded-[var(--radius-sm)] border border-[var(--color-border)] bg-[var(--color-bg-mute)] p-2.5 text-xs space-y-1.5">
                      {state.currentGoal && (
                        <div>
                          <span className="font-bold text-[var(--color-ink)]">Goal: </span>
                          <span className="text-[var(--color-ink-soft)]">{state.currentGoal}</span>
                        </div>
                      )}
                      {state.confirmedDecisions?.length > 0 && (
                        <div>
                          <span className="font-bold text-[var(--color-ink)]">Decisions: </span>
                          <ul className="list-disc list-inside text-[var(--color-ink-soft)]">
                            {state.confirmedDecisions.map((d: string, i: number) => <li key={i}>{d}</li>)}
                          </ul>
                        </div>
                      )}
                      {state.openQuestions?.length > 0 && (
                        <div>
                          <span className="font-bold text-[var(--color-ink)]">Questions: </span>
                          <ul className="list-disc list-inside text-[var(--color-ink-soft)]">
                            {state.openQuestions.map((q: string, i: number) => <li key={i}>{q}</li>)}
                          </ul>
                        </div>
                      )}
                      {state.userPreferences?.length > 0 && (
                        <div>
                          <span className="font-bold text-[var(--color-ink)]">Preferences: </span>
                          <ul className="list-disc list-inside text-[var(--color-ink-soft)]">
                            {state.userPreferences.map((p: string, i: number) => <li key={i}>{p}</li>)}
                          </ul>
                        </div>
                      )}
                      {state.activePaperIds?.length > 0 && (
                        <div>
                          <span className="font-bold text-[var(--color-ink)]">Active Papers: </span>
                          <span className="text-[var(--color-ink-soft)]">{state.activePaperIds.join(', ')}</span>
                        </div>
                      )}
                    </div>
                  )}
                </section>
              );
            })()}

            {/* Long-term Memories */}
            <section>
              <h3 className="text-[11px] font-bold uppercase tracking-wide text-[var(--color-ink-mute)] mb-1.5">
                Long-term Memories ({data.longTermMemories.length})
              </h3>
              {data.longTermMemories.length === 0 ? (
                <div className="rounded-[var(--radius-sm)] border border-[var(--color-border)] bg-[var(--color-primary-soft)] px-3 py-4 text-center text-xs text-[var(--color-ink-mute)]">
                  No long-term memories yet. They accumulate as you chat.
                </div>
              ) : (
                <div className="space-y-2">
                  {data.longTermMemories.map((m: MemoryEntry) => (
                    <div
                      key={m.id}
                      className="rounded-[var(--radius-sm)] border border-[var(--color-border)] bg-white p-2.5"
                    >
                      <div className="flex items-center justify-between mb-1">
                        <span className="flex items-center gap-1 text-[10px] font-semibold text-[var(--color-primary)]">
                          {memoryIcon(m.memoryType)}
                          {memoryLabel(m.memoryType)}
                        </span>
                        <div className="flex items-center gap-1.5">
                          <span className="text-[10px] text-[var(--color-ink-mute)] capitalize">{m.scope}</span>
                          <button
                            type="button"
                            onClick={() => handleDeleteMemory(m.id)}
                            className="rounded p-0.5 text-[var(--color-ink-mute)] hover:bg-red-50 hover:text-red-500 transition"
                            title="Delete this memory"
                          >
                            <Trash2 size={11} />
                          </button>
                        </div>
                      </div>
                      <p className="text-xs leading-relaxed text-[var(--color-ink-soft)]">{m.content}</p>
                      <div className="mt-2 flex items-center gap-3">
                        <div className="flex items-center gap-1 text-[10px] text-[var(--color-ink-mute)]">
                          <span>I</span>{percentBar(m.importance)}
                        </div>
                        <div className="flex items-center gap-1 text-[10px] text-[var(--color-ink-mute)]">
                          <span>C</span>{percentBar(m.confidence)}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </section>
          </>
        )}

        {!loading && !error && !data && (
          <div className="rounded-[var(--radius-sm)] border border-[var(--color-border)] bg-[var(--color-primary-soft)] px-3 py-8 text-center text-xs text-[var(--color-ink-mute)]">
            No session selected. Start a conversation to build memory.
          </div>
        )}
      </div>
    </aside>
  );
}
