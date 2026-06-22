import { FilePenLine, Library, MessageCircle, Search, Sparkles } from 'lucide-react';
import type { View } from './Layout';

interface Props {
  currentView: View;
  onNavigate: (view: View) => void;
}

const navItems: Array<{ view: View; label: string; icon: typeof MessageCircle }> = [
  { view: 'chat', label: '对话', icon: MessageCircle },
  { view: 'library', label: '文献库', icon: Library },
  { view: 'writing', label: '写作', icon: FilePenLine },
  { view: 'discovery', label: '论文检索', icon: Search },
];

export function Sidebar({ currentView, onNavigate }: Props) {
  return (
    <>
      <aside className="hidden h-full w-64 shrink-0 border-r border-[var(--color-border)] bg-white/62 px-3 py-4 backdrop-blur-2xl md:flex md:flex-col">
        <div className="mb-4 flex items-center gap-3 px-2">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-[var(--color-primary)] text-white shadow-sm">
            <Sparkles size={18} />
          </div>
          <div>
            <h1 className="text-[17px] font-bold leading-tight tracking-tight">Paper Agent</h1>
            <p className="text-xs text-[var(--color-ink-mute)]">科研文献智能助手</p>
          </div>
        </div>

        <nav className="space-y-1">
          {navItems.map((item) => {
            const Icon = item.icon;
            const active = currentView === item.view;
            return (
              <button
                key={item.view}
                type="button"
                onClick={() => onNavigate(item.view)}
                className={`flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-semibold transition-all ${
                  active
                    ? 'bg-white text-[var(--color-primary)] shadow-sm ring-1 ring-black/5'
                    : 'text-[var(--color-ink-soft)] hover:bg-white/58 hover:text-[var(--color-ink)]'
                }`}
              >
                <Icon size={18} />
                {item.label}
              </button>
            );
          })}
        </nav>

        <div className="mt-auto rounded-lg border border-[var(--color-border)] bg-white/52 p-3">
          <p className="text-xs font-semibold text-[var(--color-ink)]">Phase 3</p>
          <p className="mt-1 text-xs leading-5 text-[var(--color-ink-mute)]">
            写作工作台、证据引用和 Markdown/LaTeX 导出已接入。
          </p>
        </div>
      </aside>

      <nav className="fixed inset-x-3 bottom-3 z-40 grid grid-cols-4 rounded-lg border border-[var(--color-border)] bg-white/82 p-1 shadow-[var(--shadow-md)] backdrop-blur-2xl md:hidden">
        {navItems.map((item) => {
          const Icon = item.icon;
          const active = currentView === item.view;
          return (
            <button
              key={item.view}
              type="button"
              onClick={() => onNavigate(item.view)}
              className={`flex flex-col items-center gap-1 rounded-md px-1.5 py-2 text-[11px] font-semibold transition ${
                active ? 'bg-[var(--color-primary)] text-white' : 'text-[var(--color-ink-mute)]'
              }`}
            >
              <Icon size={17} />
              {item.label}
            </button>
          );
        })}
      </nav>
    </>
  );
}
