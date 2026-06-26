import { useEffect, useState } from 'react';
import { getPaperStats } from '../../api/client';
import type { PaperStats } from '../../types';
import { AlertCircle, CheckCircle2, Database, FileText, Layers, Loader2, MessageSquare, Tag } from 'lucide-react';

const statDefs: Array<{ key: keyof PaperStats; label: string; icon: typeof FileText }> = [
  { key: 'totalPapers', label: '论文总数', icon: FileText },
  { key: 'readyPapers', label: '就绪', icon: CheckCircle2 },
  { key: 'processingPapers', label: '处理中', icon: Loader2 },
  { key: 'errorPapers', label: '错误', icon: AlertCircle },
  { key: 'uniqueTags', label: '独立标签', icon: Tag },
  { key: 'totalChunks', label: '向量块', icon: Layers },
  { key: 'totalChatSessions', label: '对话会话', icon: MessageSquare },
  { key: 'totalChatMessages', label: '对话消息', icon: Database },
];

export function StatsDashboard() {
  const [stats, setStats] = useState<PaperStats | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      try {
        setIsLoading(true);
        setStats(await getPaperStats());
        setError(null);
      } catch (err) {
        setError(err instanceof Error ? err.message : '加载统计数据失败');
      } finally {
        setIsLoading(false);
      }
    })();
  }, []);

  if (error) {
    return (
      <div className="surface mb-6 p-5 text-sm font-semibold text-red-600">
        {error}
      </div>
    );
  }

  return (
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4 mb-6">
      {statDefs.map((def) => {
        const Icon = def.icon;
        const value = stats?.[def.key] ?? null;
        return (
          <div key={def.key} className="surface flex items-center gap-4 p-4">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[var(--radius-sm)] bg-[var(--color-primary-soft)] text-[var(--color-ink)]">
              {isLoading ? <Loader2 size={18} className="animate-spin" /> : <Icon size={18} />}
            </div>
            <div className="min-w-0">
              <p className="text-[11px] font-semibold uppercase tracking-wider text-[var(--color-ink-mute)]">{def.label}</p>
              <p className="font-[var(--font-display)] text-2xl font-bold tracking-tight text-[var(--color-ink)]">
                {isLoading ? '—' : value !== null ? value : '—'}
              </p>
            </div>
          </div>
        );
      })}
    </div>
  );
}
