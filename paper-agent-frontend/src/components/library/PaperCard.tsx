import type { PaperListItem } from '../../types';
import { ExternalLink, FileText, MessageCircle, Pencil, Trash2 } from 'lucide-react';

interface Props {
  paper: PaperListItem;
  onChat: (paperId: number) => void;
  onEdit: (paperId: number) => void;
  onDelete: (paperId: number) => void;
}

const statusMap: Record<string, { label: string; className: string }> = {
  UPLOADED: { label: '已上传', className: 'bg-slate-100 text-slate-600' },
  PARSING: { label: '解析中', className: 'bg-amber-100 text-amber-700' },
  PARSED: { label: '已解析', className: 'bg-blue-100 text-blue-700' },
  EMBEDDING: { label: '向量化中', className: 'bg-amber-100 text-amber-700' },
  READY: { label: '就绪', className: 'bg-emerald-100 text-emerald-700' },
  ERROR: { label: '错误', className: 'bg-red-100 text-red-700' },
};

export function PaperCard({ paper, onChat, onEdit, onDelete }: Props) {
  const status = statusMap[paper.status] ?? { label: paper.status, className: 'bg-slate-100 text-slate-600' };

  return (
    <article className="surface group flex min-h-[300px] flex-col p-5 transition duration-200 hover:-translate-y-0.5 hover:shadow-[var(--shadow-md)]">
      <div className="flex items-start justify-between gap-3">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-[var(--color-primary-soft)] text-[var(--color-primary)]">
          <FileText size={18} />
        </div>
        <span className={`pill ${status.className}`}>{status.label}</span>
      </div>

      <h3 className="mt-4 line-clamp-2 text-[17px] font-bold leading-snug tracking-tight text-[var(--color-ink)]">
        {paper.title}
      </h3>
      <p className="mt-2 line-clamp-1 text-xs text-[var(--color-ink-mute)]">{paper.authors || '作者未记录'}</p>
      <p className="mt-1 line-clamp-1 text-xs text-[var(--color-ink-mute)]">
        {paper.filename} · {new Date(paper.createdAt).toLocaleDateString('zh-CN')}
      </p>

      {paper.tags.length > 0 && (
        <div className="mt-3 flex flex-wrap gap-1.5">
          {paper.tags.slice(0, 5).map((tag) => (
            <span key={tag} className="pill bg-white/72 text-[var(--color-primary)] ring-1 ring-[var(--color-border)]">
              {tag}
            </span>
          ))}
        </div>
      )}

      <p className="mt-4 line-clamp-4 text-sm leading-6 text-[var(--color-ink-soft)]">{paper.summary || '暂无摘要。'}</p>

      {paper.sourceUrl && (
        <a
          href={paper.sourceUrl}
          target="_blank"
          rel="noreferrer"
          className="mt-3 inline-flex items-center gap-1.5 text-xs font-semibold text-[var(--color-primary)] hover:underline"
        >
          <ExternalLink size={13} />
          打开来源
        </a>
      )}

      <div className="mt-auto grid grid-cols-3 gap-2 pt-5">
        <button
          disabled={paper.status !== 'READY'}
          onClick={() => onChat(paper.id)}
          className="secondary-button min-h-10 px-2 text-xs disabled:opacity-45"
        >
          <MessageCircle size={14} />
          对话
        </button>
        <button onClick={() => onEdit(paper.id)} className="secondary-button min-h-10 px-2 text-xs">
          <Pencil size={14} />
          编辑
        </button>
        <button
          onClick={() => onDelete(paper.id)}
          className="inline-flex min-h-10 items-center justify-center gap-1.5 rounded-lg border border-red-200 bg-white/68 px-2 text-xs font-semibold text-red-600 transition hover:bg-red-50"
        >
          <Trash2 size={14} />
          删除
        </button>
      </div>
    </article>
  );
}
