import type { PaperListItem } from '../../types';
import { ExternalLink, FileText, MessageCircle, Pencil, Trash2 } from 'lucide-react';

interface Props {
  paper: PaperListItem;
  onChat: (paperId: number) => void;
  onEdit: (paperId: number) => void;
  onDelete: (paperId: number) => void;
}

const statusMap: Record<string, { label: string; className: string }> = {
  UPLOADED: { label: '已上传', className: 'bg-[var(--color-primary-soft)] text-[var(--color-ink-mute)]' },
  PARSING: { label: '解析中', className: 'bg-[var(--color-primary-soft)] text-[var(--color-ink)]' },
  PARSED: { label: '已解析', className: 'bg-[var(--color-primary-soft)] text-[var(--color-ink)]' },
  EMBEDDING: { label: '向量化中', className: 'bg-[var(--color-primary-soft)] text-[var(--color-ink)]' },
  READY: { label: '就绪', className: 'bg-[var(--fg)] text-white' },
  ERROR: { label: '错误', className: 'bg-red-50 text-red-700' },
};

export function PaperCard({ paper, onChat, onEdit, onDelete }: Props) {
  const status = statusMap[paper.status] ?? { label: paper.status, className: 'bg-[var(--color-primary-soft)] text-[var(--color-ink-mute)]' };

  return (
    <article className="surface paper-card">
      <div className="paper-card-body">
        <div className="flex items-start justify-between gap-3">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[var(--radius-sm)] border border-[var(--glass-border-strong)] bg-[var(--color-primary-soft)] text-[var(--color-ink)]">
            <FileText size={16} />
          </div>
          <span className={`pill shrink-0 ${status.className}`}>{status.label}</span>
        </div>

        <h3 className="paper-card-title mt-3.5">
          {paper.title}
        </h3>

        <p className="mt-2 line-clamp-1 text-xs text-[var(--color-ink-mute)]">
          {paper.authors || '作者未记录'}
        </p>
        <p className="mt-0.5 line-clamp-1 text-xs text-[var(--color-ink-mute)]">
          {paper.filename} · {new Date(paper.createdAt).toLocaleDateString('zh-CN')}
        </p>

        {paper.tags.length > 0 && (
          <div className="mt-3 flex flex-wrap gap-1.5">
            {paper.tags.slice(0, 4).map((tag) => (
              <span key={tag} className="pill bg-[var(--surface)] text-[var(--color-ink-soft)] ring-1 ring-[var(--color-border)]">
                {tag}
              </span>
            ))}
          </div>
        )}

        <p className="paper-card-summary">
          {paper.summary || '暂无摘要。'}
        </p>

        {paper.sourceUrl && (
          <a
            href={paper.sourceUrl}
            target="_blank"
            rel="noreferrer"
            className="mt-3 inline-flex items-center gap-1.5 text-xs font-semibold text-[var(--color-ink)] hover:underline"
          >
            <ExternalLink size={12} />
            打开来源
          </a>
        )}
      </div>

      <div className="paper-card-footer">
        <div className="paper-card-actions">
          <button
            disabled={paper.status !== 'READY'}
            onClick={() => onChat(paper.id)}
            className="primary-button min-h-[38px] px-2 text-xs"
          >
            <MessageCircle size={13} />
            <span className="button-label">对话</span>
          </button>
          <button onClick={() => onEdit(paper.id)} className="secondary-button min-h-[38px] px-2 text-xs">
            <Pencil size={13} />
            <span className="button-label">编辑</span>
          </button>
          <button onClick={() => onDelete(paper.id)} className="danger-button min-h-[38px] px-2 text-xs">
            <Trash2 size={13} />
            <span className="button-label">删除</span>
          </button>
        </div>
      </div>
    </article>
  );
}
