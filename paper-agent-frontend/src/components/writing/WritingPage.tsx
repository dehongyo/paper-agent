import { useState, useRef, useCallback, useEffect } from 'react';
import { exportWriting, generateDraft, generateOutline, saveWritingVersion } from '../../api/client';
import type { CitationStyle, ReferenceItem, WritingLength, WritingRequest, WritingResponse, WritingType } from '../../types';
import { Clock, Save, Loader2, Check, ChevronRight, Zap } from 'lucide-react';
import { streamAutonomousWriting, confirmPhase } from '../../api/autonomous-writing';
import type { AutonomousWritingPhaseEvent } from '../../types';
import { ExportPanel } from './ExportPanel';
import { PaperPicker } from './PaperPicker';
import { ReferenceList } from './ReferenceList';
import { VersionManager } from './VersionManager';
import { WritingConfigPanel } from './WritingConfigPanel';
import { WritingEditor } from './WritingEditor';
import { WritingEvidencePanel } from './WritingEvidencePanel';

const emptyResponse: WritingResponse = {
  topic: '',
  outline: '',
  draft: '',
  references: [],
  evidence: [],
  exportMarkdown: '',
  exportLatex: '',
};

export function WritingPage() {
  const [topic, setTopic] = useState('');
  const [scope, setScope] = useState<'library' | 'selected'>('library');
  const [selectedPaperIds, setSelectedPaperIds] = useState<number[]>([]);
  const [writingType, setWritingType] = useState<WritingType>('literature-review');
  const [language, setLanguage] = useState<'zh' | 'en'>('zh');
  const [length, setLength] = useState<WritingLength>('standard');
  const [citationStyle, setCitationStyle] = useState<CitationStyle>('gbt7714');
  const [result, setResult] = useState<WritingResponse>(emptyResponse);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showVersions, setShowVersions] = useState(false);
  const [versionSaved, setVersionSaved] = useState(false);

  // Autonomous writing state
  const [autoMode, setAutoMode] = useState(false);
  const [autoSessionId, setAutoSessionId] = useState<number | null>(null);
  const [autoPhase, setAutoPhase] = useState<string>('');
  const [autoPhaseStatus, setAutoPhaseStatus] = useState<string>('');
  const [autoData, setAutoData] = useState<any>(null);
  const [autoTopic, setAutoTopic] = useState('');
  const [autoLoading, setAutoLoading] = useState(false);
  const abortRef = useRef<AbortController | null>(null);

  const buildRequest = (): WritingRequest => ({
    topic,
    scope,
    paperIds: scope === 'selected' ? selectedPaperIds : [],
    writingType,
    language,
    length,
    citationStyle,
    outline: result.outline,
    draft: result.draft,
  });

  const PHASES = [
    { key: 'topic_analysis', label: '主题分析' },
    { key: 'literature_search', label: '文献检索' },
    { key: 'import_papers', label: '导入论文' },
    { key: 'outline', label: '大纲' },
    { key: 'draft', label: '草稿' },
    { key: 'review', label: '审校' },
    { key: 'done', label: '完成' },
  ];

  const getPhaseIndex = (phase: string) => {
    const clean = phase.replace('_completed', '');
    return PHASES.findIndex(p => p.key === clean);
  };

  const startAutonomous = () => {
    if (!autoTopic.trim()) return;
    setAutoLoading(true);
    setAutoPhase('');
    setAutoData(null);
    abortRef.current = streamAutonomousWriting(
      autoTopic,
      (event: AutonomousWritingPhaseEvent) => {
        setAutoPhase(event.phase);
        setAutoPhaseStatus(event.status);
        if (event.data) {
          setAutoData(event.data);
          if (event.data.sessionId) setAutoSessionId(event.data.sessionId);
        }
        if (event.phase === 'topic_analysis' && event.status === 'completed') {
          setAutoLoading(false);
        }
      },
      (err: Error) => {
        console.error('Autonomous writing error:', err);
        setAutoLoading(false);
      }
    );
  };

  const handleConfirmPhase = async (extra?: Record<string, any>) => {
    const phase = autoPhase.replace('_completed', '');
    setAutoLoading(true);
    await confirmPhase(autoSessionId || 0, phase, extra);
    // The SSE stream will update autoPhase/autoData
    setAutoLoading(false);
  };

  // Cleanup abort on unmount
  useEffect(() => {
    return () => {
      abortRef.current?.abort();
    };
  }, []);

  const run = async (action: 'outline' | 'draft' | 'export') => {
    setIsLoading(true);
    setError(null);
    try {
      const request = buildRequest();
      const response =
        action === 'outline'
          ? await generateOutline(request)
          : action === 'draft'
            ? await generateDraft(request)
            : await exportWriting(request);
      setResult(response);
    } catch (err) {
      setError(err instanceof Error ? err.message : '生成失败，请稍后重试。');
    } finally {
      setIsLoading(false);
    }
  };

  const handleSaveVersion = async () => {
    if (!topic.trim() || !result.draft.trim()) return;
    setError(null);
    try {
      await saveWritingVersion({
        topic,
        outline: result.outline,
        draft: result.draft,
        references: result.references,
      });
      setVersionSaved(true);
      setTimeout(() => setVersionSaved(false), 2000);
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存版本失败');
    }
  };

  const handleLoadVersion = (data: { outline: string; draft: string; references: ReferenceItem[] }) => {
    setResult((prev) => ({
      ...prev,
      outline: data.outline,
      draft: data.draft,
      references: data.references,
    }));
  };

  return (
    <div className="page-shell">
      <div className="page-content writing-page-content">
        <header className="page-header">
          <div>
            <p className="eyebrow">WRITING STUDIO</p>
            <h1 className="page-title">写作工作台</h1>
            <p className="page-subtitle">基于本地文献证据生成综述大纲、草稿、参考文献和 Markdown/LaTeX 导出。</p>
          </div>
          <div className="writing-page-actions">
            <button
              type="button"
              onClick={() => void handleSaveVersion()}
              disabled={isLoading || !topic.trim() || !result.draft.trim()}
              className="secondary-button"
            >
              <Save size={16} />
              保存版本
            </button>
            <button
              type="button"
              onClick={() => setShowVersions(true)}
              className="secondary-button"
            >
              <Clock size={16} />
              版本历史
            </button>
          </div>
        </header>

        {/* Mode toggle */}
        <div className="flex gap-1.5 rounded-lg bg-[var(--color-bg-mute)] p-1 mb-5 w-fit">
          <button
            onClick={() => setAutoMode(false)}
            className={`px-3 py-1.5 rounded-md text-xs font-semibold transition-colors ${
              !autoMode ? 'bg-white text-[var(--color-ink)] shadow-sm' : 'text-[var(--color-ink-mute)]'
            }`}
          >
            手动写作
          </button>
          <button
            onClick={() => setAutoMode(true)}
            className={`flex items-center gap-1 px-3 py-1.5 rounded-md text-xs font-semibold transition-colors ${
              autoMode ? 'bg-white text-[var(--color-ink)] shadow-sm' : 'text-[var(--color-ink-mute)]'
            }`}
          >
            <Zap size={14} />
            自主综述
          </button>
        </div>

        {!autoMode ? (<>
          {error && (
          <div className="surface-solid mb-5 p-4 text-sm font-semibold text-red-600">
            {error}
          </div>
        )}
        {versionSaved && (
          <div className="surface-solid mb-5 p-4 text-sm font-semibold text-green-700">
            版本已保存。
          </div>
        )}

        <div className="writing-workspace-grid">
          <aside className="writing-config">
            <WritingConfigPanel
              topic={topic}
              writingType={writingType}
              language={language}
              length={length}
              citationStyle={citationStyle}
              isLoading={isLoading}
              onTopicChange={setTopic}
              onWritingTypeChange={setWritingType}
              onLanguageChange={setLanguage}
              onLengthChange={setLength}
              onCitationStyleChange={setCitationStyle}
              onGenerateOutline={() => void run('outline')}
              onGenerateDraft={() => void run('draft')}
            />
            <PaperPicker
              scope={scope}
              selectedPaperIds={selectedPaperIds}
              onScopeChange={setScope}
              onSelectionChange={setSelectedPaperIds}
            />
          </aside>

          <main className="writing-editor">
            {isLoading && !result.outline && !result.draft ? (
              <div className="surface p-5">
                <div className="skeleton skeleton-line-lg" style={{ height: 16, marginBottom: 20 }} />
                <div className="skeleton" style={{ height: 160, borderRadius: 'var(--radius-sm)', marginBottom: 20 }} />
                <div className="skeleton skeleton-line-md" style={{ height: 14, marginBottom: 16 }} />
                <div className="skeleton" style={{ height: 360, borderRadius: 'var(--radius-sm)' }} />
              </div>
            ) : (
              <WritingEditor
                outline={result.outline}
                draft={result.draft}
                onOutlineChange={(outline) => setResult((prev) => ({ ...prev, outline }))}
                onDraftChange={(draft) => setResult((prev) => ({ ...prev, draft }))}
              />
            )}
            <div className="writing-export-action">
              <button type="button" onClick={() => void run('export')} disabled={isLoading || !result.draft.trim()} className="secondary-button">
                重新生成导出
              </button>
            </div>
            <ReferenceList references={result.references} />
            <ExportPanel markdown={result.exportMarkdown} latex={result.exportLatex} />
          </main>

          <aside className="writing-evidence">
            <WritingEvidencePanel evidence={result.evidence} />
          </aside>
        </div>

        {showVersions && (
          <VersionManager
            topic={topic}
            outline={result.outline}
            draft={result.draft}
            references={result.references}
            onLoad={handleLoadVersion}
            onClose={() => setShowVersions(false)}
          />
        )}
        </>) : (
          <div className="flex flex-col h-full">
            {/* Phase progress bar */}
            <div className="flex items-center gap-1 px-6 py-3 border-b border-[var(--color-border)] overflow-x-auto">
              {PHASES.map((p, i) => {
                const currentIdx = getPhaseIndex(autoPhase);
                const isCompleted = i < currentIdx || (i === currentIdx && autoPhaseStatus === 'completed');
                const isCurrent = i === currentIdx && autoPhaseStatus === 'in_progress';
                return (
                  <div key={p.key} className="flex items-center gap-1 shrink-0">
                    {i > 0 && <ChevronRight size={12} className="text-[var(--color-ink-mute)]" />}
                    <div className={`flex items-center gap-1 px-2 py-1 rounded-full text-xs font-medium ${
                      isCompleted ? 'bg-green-50 text-green-600' :
                      isCurrent ? 'bg-[var(--color-primary-soft)] text-[var(--color-primary)]' :
                      'text-[var(--color-ink-mute)]'
                    }`}>
                      {isCompleted ? <Check size={12} /> : isCurrent ? <Loader2 size={12} className="animate-spin" /> : null}
                      {p.label}
                    </div>
                  </div>
                );
              })}
            </div>

            {/* Phase content area */}
            <div className="flex-1 overflow-y-auto p-6">
              {autoPhase === '' && !autoLoading && (
                /* Topic input */
                <div className="max-w-xl mx-auto mt-20">
                  <h3 className="text-lg font-bold">自主综述</h3>
                  <p className="text-sm text-[var(--color-ink-mute)] mt-1 mb-4">
                    输入研究主题，AI 将自动搜索论文、导入、生成大纲和草稿
                  </p>
                  <input className="w-full rounded-lg border px-4 py-3 text-sm" placeholder="输入研究主题..."
                    value={autoTopic} onChange={e => setAutoTopic(e.target.value)}
                    onKeyDown={e => e.key === 'Enter' && startAutonomous()} />
                  <button onClick={startAutonomous} disabled={!autoTopic.trim()}
                    className="mt-3 w-full rounded-lg bg-[var(--color-primary)] px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-40">
                    开始自主综述
                  </button>
                </div>
              )}

              {autoPhase === 'topic_analysis' && autoPhaseStatus === 'completed' && autoData && (
                /* Topic analysis result */
                <div className="max-w-2xl mx-auto">
                  <h3 className="font-bold mb-2">主题分析结果</h3>
                  <div className="rounded-xl border p-4 space-y-2 bg-white">
                    <div><span className="text-xs font-semibold text-[var(--color-ink-mute)]">主题</span>
                      <p className="text-sm mt-0.5">{autoData?.topic || autoTopic}</p></div>
                    <div><span className="text-xs font-semibold text-[var(--color-ink-mute)]">子话题</span>
                      <div className="flex flex-wrap gap-1 mt-1">
                        {(autoData?.subtopics || []).map((s: string, i: number) => <span key={i} className="px-2 py-0.5 rounded-full bg-[var(--color-bg-mute)] text-xs">{s}</span>)}
                      </div></div>
                    <div><span className="text-xs font-semibold text-[var(--color-ink-mute)]">搜索关键词</span>
                      <div className="flex flex-wrap gap-1 mt-1">
                        {(autoData?.keywords || []).map((k: string, i: number) => <span key={i} className="px-2 py-0.5 rounded-full bg-blue-50 text-blue-600 text-xs">{k}</span>)}
                      </div></div>
                  </div>
                  <button onClick={() => handleConfirmPhase()} className="mt-4 w-full rounded-lg bg-[var(--color-primary)] px-4 py-2.5 text-sm font-semibold text-white">
                    确认，开始检索文献
                  </button>
                </div>
              )}

              {autoPhase === 'literature_search' && (
                <div className="flex flex-col items-center justify-center h-64">
                  <Loader2 size={24} className="animate-spin mb-3" />
                  <p className="text-sm text-[var(--color-ink-mute)]">AI 正在自主搜索文献...</p>
                </div>
              )}

              {autoPhase === 'literature_search_completed' && (
                <div className="max-w-2xl mx-auto">
                  <h3 className="font-bold mb-2">文献检索完成</h3>
                  <div className="rounded-xl border p-4 bg-white">
                    <pre className="text-xs whitespace-pre-wrap max-h-64 overflow-y-auto">{autoData?.result || ''}</pre>
                  </div>
                  <button onClick={() => handleConfirmPhase()} className="mt-4 w-full rounded-lg bg-[var(--color-primary)] px-4 py-2.5 text-sm font-semibold text-white">
                    确认，导入选中论文
                  </button>
                </div>
              )}

              {/* Import progress */}
              {(autoPhase === 'import_papers' || autoPhase === 'import_papers_completed') && (
                <div className="max-w-lg mx-auto text-center">
                  <h3 className="font-bold mb-2">导入论文</h3>
                  {autoPhaseStatus === 'in_progress' && autoData && (
                    <div>
                      <Loader2 size={24} className="animate-spin mx-auto mb-3" />
                      <p className="text-sm">正在导入: {autoData.currentTitle || ''}</p>
                      <div className="mt-2 w-full bg-[var(--color-bg-mute)] rounded-full h-2">
                        <div className="bg-[var(--color-primary)] h-2 rounded-full transition-all"
                          style={{ width: `${autoData.total ? (autoData.completed / autoData.total) * 100 : 0}%` }} />
                      </div>
                      <p className="text-xs text-[var(--color-ink-mute)] mt-1">{autoData.completed || 0}/{autoData.total || 0}</p>
                    </div>
                  )}
                  {autoPhaseStatus === 'completed' && autoData && (
                    <div>
                      <Check size={32} className="mx-auto mb-2 text-green-500" />
                      <p className="text-sm">已导入 {autoData.importedCount} 篇论文</p>
                      <button onClick={() => handleConfirmPhase()} className="mt-4 rounded-lg bg-[var(--color-primary)] px-4 py-2.5 text-sm font-semibold text-white">
                        确认，生成大纲
                      </button>
                    </div>
                  )}
                </div>
              )}

              {/* Outline, Draft, Review phases — show data with confirm button */}
              {(autoPhase === 'outline_completed' || autoPhase === 'draft_completed' || autoPhase === 'done') && autoData && (
                <div className="max-w-3xl mx-auto">
                  <h3 className="font-bold mb-2">
                    {autoPhase === 'outline_completed' ? '大纲' :
                     autoPhase === 'draft_completed' ? '草稿' : '审校完成'}
                  </h3>
                  <div className="rounded-xl border p-4 bg-white">
                    <pre className="text-xs whitespace-pre-wrap max-h-96 overflow-y-auto">
                      {autoData.outline || autoData.draft || autoData.finalDraft || ''}
                    </pre>
                  </div>
                  {autoPhase === 'outline_completed' && (
                    <button onClick={() => handleConfirmPhase()} className="mt-4 w-full rounded-lg bg-[var(--color-primary)] px-4 py-2.5 text-sm font-semibold text-white">
                      确认，生成草稿
                    </button>
                  )}
                  {autoPhase === 'draft_completed' && (
                    <button onClick={() => handleConfirmPhase()} className="mt-4 w-full rounded-lg bg-[var(--color-primary)] px-4 py-2.5 text-sm font-semibold text-white">
                      确认，AI 自审修订
                    </button>
                  )}
                </div>
              )}

              {autoLoading && autoPhase === '' && (
                <div className="flex flex-col items-center justify-center h-64">
                  <Loader2 size={24} className="animate-spin mb-3" />
                  <p className="text-sm text-[var(--color-ink-mute)]">正在分析主题...</p>
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
