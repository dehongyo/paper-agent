import { useState } from 'react';
import { exportWriting, generateDraft, generateOutline } from '../../api/client';
import type { CitationStyle, WritingLength, WritingRequest, WritingResponse, WritingType } from '../../types';
import { ExportPanel } from './ExportPanel';
import { PaperPicker } from './PaperPicker';
import { ReferenceList } from './ReferenceList';
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

  return (
    <div className="page-shell">
      <div className="page-content">
        <header className="page-header">
          <div>
            <p className="eyebrow">WRITING STUDIO</p>
            <h1 className="page-title">写作工作台</h1>
            <p className="page-subtitle">基于本地文献证据生成综述大纲、草稿、参考文献和 Markdown/LaTeX 导出。</p>
          </div>
        </header>

        {error && (
          <div className="surface-solid mb-5 p-4 text-sm font-semibold text-red-600">
            {error}
          </div>
        )}

        <div className="grid gap-5 xl:grid-cols-[320px_minmax(0,1fr)_320px]">
          <aside className="grid content-start gap-4">
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

          <main className="grid min-w-0 content-start gap-4">
            <WritingEditor
              outline={result.outline}
              draft={result.draft}
              onOutlineChange={(outline) => setResult((prev) => ({ ...prev, outline }))}
              onDraftChange={(draft) => setResult((prev) => ({ ...prev, draft }))}
            />
            <div className="flex justify-end">
              <button type="button" onClick={() => void run('export')} disabled={isLoading || !result.draft.trim()} className="secondary-button">
                重新生成导出
              </button>
            </div>
            <ReferenceList references={result.references} />
            <ExportPanel markdown={result.exportMarkdown} latex={result.exportLatex} />
          </main>

          <aside>
            <WritingEvidencePanel evidence={result.evidence} />
          </aside>
        </div>
      </div>
    </div>
  );
}
