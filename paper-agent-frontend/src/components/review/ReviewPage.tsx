import { useState, useEffect, useRef, useCallback } from 'react';
import { Upload, FileText, Play, Loader2 } from 'lucide-react';
import { listTemplates, uploadTemplate, streamReview, listReviewSessions, continueReview } from '../../api/review';
import { getPapers } from '../../api/client';
import type { ReviewTemplate, ReviewSessionResponse, PaperListItem, ReviewStartEvent } from '../../types';

type Tab = 'run' | 'history';

const REVIEW_SUBMITTING = '\u6b63\u5728\u63d0\u4ea4\u8bc4\u5ba1\u4efb\u52a1...';
const REVIEW_PREPARING = '\u6b63\u5728\u51c6\u5907\u8bc4\u5ba1...';
const REVIEW_DONE = '\u8bc4\u5ba1\u5b8c\u6210';
const REVIEW_CANCELED = '\u5df2\u53d6\u6d88\u8bc4\u5ba1';
const CURRENT_SECTION_LABEL = '\u5f53\u524d\u7ae0\u8282\uff1a';

export function ReviewPage() {
  const [tab, setTab] = useState<Tab>('run');

  // Paper selection
  const [papers, setPapers] = useState<PaperListItem[]>([]);
  const [selectedPaperId, setSelectedPaperId] = useState<number | null>(null);

  // Template selection
  const [templates, setTemplates] = useState<ReviewTemplate[]>([]);
  const [selectedTemplateId, setSelectedTemplateId] = useState<number | null>(null);

  // Review state
  const [reviewing, setReviewing] = useState(false);
  const [resultText, setResultText] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [progressMessage, setProgressMessage] = useState('');
  const [progressSection, setProgressSection] = useState<string | null>(null);
  const [progressCurrent, setProgressCurrent] = useState<number | null>(null);
  const [progressTotal, setProgressTotal] = useState<number | null>(null);

  // Follow-up
  const [followUp, setFollowUp] = useState('');
  const [followUpLoading, setFollowUpLoading] = useState(false);
  const [followUpHistory, setFollowUpHistory] = useState<Array<{ role: 'user' | 'assistant'; content: string }>>([]);

  // History
  const [sessions, setSessions] = useState<ReviewSessionResponse[]>([]);

  const resultRef = useRef<HTMLDivElement>(null);
  const abortRef = useRef<AbortController | null>(null);
  const progressPercent = progressTotal ? Math.min(100, Math.round(((progressCurrent ?? 0) / progressTotal) * 100)) : (reviewing ? 5 : 0);

  const loadPapers = useCallback(async () => {
    try { setPapers(await getPapers()); } catch { /* ignore */ }
  }, []);

  const loadTemplates = useCallback(async () => {
    try { setTemplates(await listTemplates()); } catch { /* ignore */ }
  }, []);

  const loadSessions = useCallback(async () => {
    try { setSessions(await listReviewSessions()); } catch { /* ignore */ }
  }, []);

  useEffect(() => { loadPapers(); loadTemplates(); }, [loadPapers, loadTemplates]);

  const handleUploadTemplate = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      await uploadTemplate(file);
      await loadTemplates();
      e.target.value = '';
    } catch (err: any) {
      setError(err.message);
    }
  };

  const startReview = () => {
    if (!selectedPaperId || !selectedTemplateId) return;
    setError(null);
    setResultText('');
    setFollowUpHistory([]);
    setProgressMessage(REVIEW_SUBMITTING);
    setProgressSection(null);
    setProgressCurrent(null);
    setProgressTotal(null);
    setReviewing(true);

    abortRef.current = streamReview(
      { paperId: selectedPaperId, templateId: selectedTemplateId },
      (text) => { setResultText((prev) => prev + text); },
      (event: ReviewStartEvent) => {
        setProgressMessage(event.message || '');
        setProgressSection(event.sectionTitle);
        setProgressCurrent(event.current);
        setProgressTotal(event.total);
      },
      () => {
        setProgressMessage(REVIEW_DONE);
        setReviewing(false);
        loadSessions();
      },
      (err) => {
        setError(err.message);
        setReviewing(false);
      }
    );
  };

  const cancelReview = () => {
    abortRef.current?.abort();
    setProgressMessage(REVIEW_CANCELED);
    setReviewing(false);
  };

  const sendFollowUp = async () => {
    if (!followUp.trim()) return;
    const sessions = await listReviewSessions();
    const latestSession = sessions.find(s => s.status === 'completed');
    if (!latestSession) { setError('No completed review session found'); return; }
    setFollowUpLoading(true);
    setFollowUpHistory((prev) => [...prev, { role: 'user' as const, content: followUp }]);
    const q = followUp;
    setFollowUp('');
    try {
      const answer = await continueReview({ sessionId: latestSession.id, message: q });
      setFollowUpHistory((prev) => [...prev, { role: 'assistant' as const, content: answer }]);
    } catch (err: any) {
      setError(err.message);
    }
    setFollowUpLoading(false);
  };

  // Auto-scroll review result
  useEffect(() => {
    if (resultRef.current) resultRef.current.scrollTop = resultRef.current.scrollHeight;
  }, [resultText]);

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="flex items-center justify-between px-6 py-4 border-b border-[var(--color-border)]">
        <div>
          <h2 className="text-lg font-bold text-[var(--color-ink)]">论文评审</h2>
          <p className="text-xs text-[var(--color-ink-mute)]">按评审模板对论文进行深度审读，出具带原文引用的评审意见</p>
        </div>
        <div className="flex gap-1.5 rounded-lg bg-[var(--color-bg-mute)] p-1">
          <button onClick={() => setTab('run')} className={`px-3 py-1.5 text-xs font-semibold rounded-md transition ${tab === 'run' ? 'bg-white text-[var(--color-ink)] shadow-sm' : 'text-[var(--color-ink-mute)]'}`}>执行评审</button>
          <button onClick={() => { setTab('history'); loadSessions(); }} className={`px-3 py-1.5 text-xs font-semibold rounded-md transition ${tab === 'history' ? 'bg-white text-[var(--color-ink)] shadow-sm' : 'text-[var(--color-ink-mute)]'}`}>评审记录</button>
        </div>
      </div>

      {tab === 'run' ? (
        <div className="flex flex-1 overflow-hidden">
          {/* Left panel: config */}
          <div className="w-80 border-r border-[var(--color-border)] p-4 space-y-5 overflow-y-auto">
            {/* Paper selector */}
            <div>
              <label className="text-xs font-semibold text-[var(--color-ink-mute)] uppercase tracking-wide">选择论文</label>
              <select
                className="mt-1.5 w-full rounded-lg border border-[var(--color-border)] bg-white px-3 py-2 text-sm text-[var(--color-ink)]"
                value={selectedPaperId ?? ''}
                onChange={(e) => setSelectedPaperId(e.target.value ? Number(e.target.value) : null)}
                disabled={reviewing}
              >
                <option value="">-- 选择论文 --</option>
                {papers.filter(p => p.status === 'READY').map((p) => (
                  <option key={p.id} value={p.id}>{p.title}</option>
                ))}
              </select>
            </div>

            {/* Template selector */}
            <div>
              <label className="text-xs font-semibold text-[var(--color-ink-mute)] uppercase tracking-wide">评审模板</label>
              <select
                className="mt-1.5 w-full rounded-lg border border-[var(--color-border)] bg-white px-3 py-2 text-sm text-[var(--color-ink)]"
                value={selectedTemplateId ?? ''}
                onChange={(e) => setSelectedTemplateId(e.target.value ? Number(e.target.value) : null)}
                disabled={reviewing}
              >
                <option value="">-- 选择模板 --</option>
                {templates.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.type === 'builtin' ? '📋 ' : '📄 '}{t.name}
                  </option>
                ))}
              </select>
            </div>

            {/* Upload template */}
            <div>
              <label className="text-xs font-semibold text-[var(--color-ink-mute)] uppercase tracking-wide">上传自定义模板</label>
              <label className="mt-1.5 flex items-center justify-center gap-2 w-full rounded-lg border-2 border-dashed border-[var(--color-border)] px-3 py-3 text-xs text-[var(--color-ink-mute)] cursor-pointer hover:border-[var(--color-primary)] hover:text-[var(--color-primary)] transition">
                <Upload size={14} />
                上传 PDF / DOC / DOCX
                <input type="file" accept=".pdf,.doc,.docx" className="hidden" onChange={handleUploadTemplate} />
              </label>
            </div>

            {/* Start button */}
            <button
              onClick={reviewing ? cancelReview : startReview}
              disabled={!selectedPaperId || !selectedTemplateId}
              className={`w-full flex items-center justify-center gap-2 rounded-lg px-4 py-2.5 text-sm font-semibold transition ${
                reviewing
                  ? 'bg-red-50 text-red-600 border border-red-200 hover:bg-red-100'
                  : 'bg-[var(--color-primary)] text-white hover:opacity-90 disabled:opacity-40 disabled:cursor-not-allowed'
              }`}
            >
              {reviewing ? (
                <><Loader2 size={16} className="animate-spin" /> 取消评审</>
              ) : (
                <><Play size={16} /> 开始评审</>
              )}
            </button>

            {reviewing && (
              <div className="rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-mute)] p-3">
                <div className="flex items-center gap-2 text-xs font-semibold text-[var(--color-ink)]">
                  <Loader2 size={14} className="animate-spin text-[var(--color-primary)]" />
                  <span>{progressMessage || REVIEW_PREPARING}</span>
                </div>
                {progressSection && (
                  <div className="mt-2 text-xs text-[var(--color-ink-mute)]">{CURRENT_SECTION_LABEL}{progressSection}</div>
                )}
                {progressTotal && (
                  <div className="mt-3">
                    <div className="mb-1 flex justify-between text-[11px] text-[var(--color-ink-mute)]">
                      <span>{progressCurrent ?? 0}/{progressTotal}</span>
                      <span>{progressPercent}%</span>
                    </div>
                    <div className="h-1.5 overflow-hidden rounded-full bg-white">
                      <div className="h-full rounded-full bg-[var(--color-primary)] transition-all" style={{ width: `${progressPercent}%` }} />
                    </div>
                  </div>
                )}
              </div>
            )}

            {error && (
              <div className="rounded-lg bg-red-50 border border-red-200 p-3 text-xs text-red-600">{error}</div>
            )}
          </div>

          {/* Right panel: result */}
          <div className="flex-1 flex flex-col min-w-0">
            <div ref={resultRef} className="flex-1 overflow-y-auto p-5">
              {resultText ? (
                <div className="prose prose-sm max-w-none text-sm leading-relaxed text-[var(--color-ink)] whitespace-pre-wrap">
                  {resultText}
                </div>
              ) : (
                <div className="flex flex-col items-center justify-center h-full text-[var(--color-ink-mute)]">
                  <FileText size={40} className="mb-3 opacity-30" />
                  <p className="text-sm">选择论文和评审模板后，点击"开始评审"</p>
                </div>
              )}
              {reviewing && !resultText && (
                <div className="flex items-center justify-center gap-2 py-8 text-[var(--color-ink-mute)]">
                  <Loader2 size={18} className="animate-spin" />
                  <span className="text-sm">{progressMessage || REVIEW_PREPARING}</span>
                </div>
              )}
            </div>

            {/* Follow-up chat */}
            {resultText && !reviewing && (
              <div className="border-t border-[var(--color-border)] p-4">
                {followUpHistory.map((msg, i) => (
                  <div key={i} className={`mb-3 ${msg.role === 'user' ? 'text-right' : ''}`}>
                    <div className={`inline-block max-w-[80%] rounded-xl px-4 py-2 text-sm ${
                      msg.role === 'user'
                        ? 'bg-[var(--color-primary)] text-white'
                        : 'bg-[var(--color-bg-mute)] text-[var(--color-ink)]'
                    }`}>
                      <div className="whitespace-pre-wrap">{msg.content}</div>
                    </div>
                  </div>
                ))}
                <div className="flex gap-2">
                  <input
                    className="flex-1 rounded-lg border border-[var(--color-border)] px-3 py-2 text-sm bg-white text-[var(--color-ink)]"
                    placeholder="对评审意见进行追问..."
                    value={followUp}
                    onChange={(e) => setFollowUp(e.target.value)}
                    onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendFollowUp(); } }}
                  />
                  <button
                    onClick={sendFollowUp}
                    disabled={!followUp.trim() || followUpLoading}
                    className="rounded-lg bg-[var(--color-primary)] px-4 py-2 text-sm font-semibold text-white disabled:opacity-40"
                  >
                    {followUpLoading ? <Loader2 size={16} className="animate-spin" /> : '发送'}
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      ) : (
        /* History tab */
        <div className="flex-1 overflow-y-auto p-4">
          {sessions.length === 0 ? (
            <div className="flex flex-col items-center justify-center h-full text-[var(--color-ink-mute)]">
              <p className="text-sm">暂无评审记录</p>
            </div>
          ) : (
            <div className="space-y-2">
              {sessions.map((s) => (
                <div key={s.id} className="rounded-xl border border-[var(--color-border)] bg-white p-4">
                  <div className="flex items-center justify-between mb-1">
                    <span className="text-sm font-semibold text-[var(--color-ink)]">{s.paperTitle}</span>
                    <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${
                      s.status === 'completed' ? 'bg-green-50 text-green-600' :
                      s.status === 'error' ? 'bg-red-50 text-red-600' :
                      'bg-yellow-50 text-yellow-600'
                    }`}>{s.status}</span>
                  </div>
                  <div className="text-xs text-[var(--color-ink-mute)]">
                    模板：{s.templateName} · {new Date(s.createdAt).toLocaleString('zh-CN')}
                  </div>
                  {s.resultText && (
                    <details className="mt-2">
                      <summary className="text-xs text-[var(--color-primary)] cursor-pointer">查看评审详情</summary>
                      <div className="mt-2 p-3 rounded-lg bg-[var(--color-bg-mute)] text-xs leading-relaxed whitespace-pre-wrap max-h-96 overflow-y-auto">
                        {s.resultText}
                      </div>
                    </details>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
