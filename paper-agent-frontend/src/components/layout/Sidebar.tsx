import { ClipboardCheck, FilePenLine, Library, MessageCircle, Search } from 'lucide-react';
import type { View } from './Layout';
import { ThemeToggle } from './ThemeToggle';

interface Props {
  currentView: View;
  onNavigate: (view: View) => void;
}

const navItems: Array<{ view: View; label: string; helper: string; icon: typeof MessageCircle }> = [
  { view: 'chat', label: '对话', helper: '围绕论文追问', icon: MessageCircle },
  { view: 'library', label: '文献库', helper: '管理本地论文', icon: Library },
  { view: 'writing', label: '写作', helper: '综述与引用', icon: FilePenLine },
  { view: 'review', label: '论文评审', helper: '深度审读意见', icon: ClipboardCheck },
  { view: 'discovery', label: '论文检索', helper: '外部文献链接', icon: Search },
];

export function Sidebar({ currentView, onNavigate }: Props) {
  return (
    <>
      <aside className="desktop-sidebar">
        <div>
          <div className="flex h-10 w-10 items-center justify-center rounded-[10px] bg-[var(--fg)] text-white shadow-sm" style={{ fontFamily: 'var(--font-display)', fontSize: 18, fontWeight: 700, letterSpacing: '-0.02em' }}>
            PA
          </div>
          <h1 className="mt-4 font-[var(--font-display)] text-[24px] font-bold leading-tight tracking-[-0.015em] text-[var(--color-ink)]">
            Paper Agent
          </h1>
          <p className="mt-2 text-[13px] leading-5 text-[var(--color-ink-mute)]">
            面向论文阅读、追问、检索和写作的轻量研究工作台。
          </p>
        </div>

        <nav className="mt-8 space-y-1.5">
          {navItems.map((item) => {
            const Icon = item.icon;
            const active = currentView === item.view;
            return (
              <button
                key={item.view}
                type="button"
                onClick={() => onNavigate(item.view)}
                className={`sidebar-nav-button ${
                  active
                    ? 'border-[var(--color-border-strong)] bg-[var(--color-primary-soft)] text-[var(--color-primary)] shadow-sm'
                    : 'border-transparent text-[var(--color-ink-soft)] hover:border-[var(--color-border)] hover:bg-[var(--color-primary-soft)]'
                }`}
              >
                <Icon size={17} className="sidebar-nav-icon" />
                <span className="sidebar-nav-copy">
                  <span className="block truncate text-[13px] font-bold">{item.label}</span>
                  <span className="mt-0.5 block truncate text-[11px] font-medium text-[var(--color-ink-mute)]">{item.helper}</span>
                </span>
              </button>
            );
          })}
        </nav>

        <div className="mt-auto pt-6 border-t border-[var(--color-border)]">
          <ThemeToggle />
        </div>
      </aside>

      <nav className="mobile-tabbar">
        {navItems.map((item) => {
          const Icon = item.icon;
          const active = currentView === item.view;
          return (
            <button
              key={item.view}
              type="button"
              onClick={() => onNavigate(item.view)}
              className={`mobile-tab-button flex flex-col items-center gap-1 rounded-[var(--radius-sm)] px-1.5 py-2 text-[11px] font-semibold transition ${
                active ? 'bg-[var(--color-primary-soft)] text-[var(--color-primary)]' : 'text-[var(--color-ink-mute)]'
              }`}
            >
              <Icon size={17} />
              <span className="mobile-tab-label">{item.label}</span>
            </button>
          );
        })}
      </nav>
    </>
  );
}
