import { useState } from 'react';
import { exportWriting, generateDraft, generateOutline, saveWritingVersion } from '../../api/client';
import type { CitationStyle, ReferenceItem, WritingLength, WritingRequest, WritingResponse, WritingType } from '../../types';
import { Clock, Save } from 'lucide-react';
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
      </div>
    </div>
  );
}
