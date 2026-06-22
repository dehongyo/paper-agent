import type { CitationStyle, WritingLength, WritingType } from '../../types';
import { FilePenLine, Loader2, Sparkles } from 'lucide-react';

interface Props {
  topic: string;
  writingType: WritingType;
  language: 'zh' | 'en';
  length: WritingLength;
  citationStyle: CitationStyle;
  isLoading: boolean;
  onTopicChange: (topic: string) => void;
  onWritingTypeChange: (writingType: WritingType) => void;
  onLanguageChange: (language: 'zh' | 'en') => void;
  onLengthChange: (length: WritingLength) => void;
  onCitationStyleChange: (style: CitationStyle) => void;
  onGenerateOutline: () => void;
  onGenerateDraft: () => void;
}

export function WritingConfigPanel({
  topic,
  writingType,
  language,
  length,
  citationStyle,
  isLoading,
  onTopicChange,
  onWritingTypeChange,
  onLanguageChange,
  onLengthChange,
  onCitationStyleChange,
  onGenerateOutline,
  onGenerateDraft,
}: Props) {
  return (
    <section className="surface p-4">
      <div className="mb-4">
        <h2 className="text-sm font-bold text-[var(--color-ink)]">写作配置</h2>
        <p className="mt-1 text-xs leading-5 text-[var(--color-ink-mute)]">先确定主题和格式，再生成大纲或草稿。</p>
      </div>

      <label className="mb-3 block">
        <span className="mb-1.5 block text-xs font-semibold text-[var(--color-ink-soft)]">综述主题</span>
        <textarea
          value={topic}
          onChange={(event) => onTopicChange(event.target.value)}
          placeholder="例如：检索增强生成在医学影像中的应用"
          className="control min-h-24 resize-none px-3 py-3 text-sm leading-6"
        />
      </label>

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-1">
        <label>
          <span className="mb-1.5 block text-xs font-semibold text-[var(--color-ink-soft)]">写作类型</span>
          <select value={writingType} onChange={(event) => onWritingTypeChange(event.target.value as WritingType)} className="control px-3 text-sm">
            <option value="literature-review">文献综述</option>
            <option value="research-background">研究背景</option>
          </select>
        </label>

        <label>
          <span className="mb-1.5 block text-xs font-semibold text-[var(--color-ink-soft)]">输出语言</span>
          <select value={language} onChange={(event) => onLanguageChange(event.target.value as 'zh' | 'en')} className="control px-3 text-sm">
            <option value="zh">中文</option>
            <option value="en">English</option>
          </select>
        </label>

        <label>
          <span className="mb-1.5 block text-xs font-semibold text-[var(--color-ink-soft)]">目标篇幅</span>
          <select value={length} onChange={(event) => onLengthChange(event.target.value as WritingLength)} className="control px-3 text-sm">
            <option value="short">短篇</option>
            <option value="standard">标准</option>
            <option value="detailed">详细</option>
          </select>
        </label>

        <label>
          <span className="mb-1.5 block text-xs font-semibold text-[var(--color-ink-soft)]">引用格式</span>
          <select value={citationStyle} onChange={(event) => onCitationStyleChange(event.target.value as CitationStyle)} className="control px-3 text-sm">
            <option value="gbt7714">GB/T 7714</option>
            <option value="apa">APA</option>
            <option value="ieee">IEEE</option>
          </select>
        </label>
      </div>

      <div className="mt-4 grid gap-2">
        <button type="button" onClick={onGenerateOutline} disabled={isLoading || !topic.trim()} className="secondary-button w-full">
          {isLoading ? <Loader2 size={16} className="animate-spin" /> : <FilePenLine size={16} />}
          生成大纲
        </button>
        <button type="button" onClick={onGenerateDraft} disabled={isLoading || !topic.trim()} className="primary-button w-full">
          {isLoading ? <Loader2 size={16} className="animate-spin" /> : <Sparkles size={16} />}
          生成草稿
        </button>
      </div>
    </section>
  );
}
