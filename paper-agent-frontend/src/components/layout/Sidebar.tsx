import { FilePenLine, Library, MessageCircle, Search, Sparkles } from 'lucide-react';
import type { View } from './Layout';

interface Props {
  currentView: View;
  onNavigate: (view: View) => void;
}

const navItems: Array<{ view: View; label: string; helper: string; icon: typeof MessageCircle }> = [
  { view: 'chat', label: '对话', helper: '围绕论文追问', icon: MessageCircle },
  { view: 'library', label: '文献库', helper: '管理本地论文', icon: Library },
  { view: 'writing', label: '写作', helper: '综述与引用', icon: FilePenLine },
  { view: 'discovery', label: '论文检索', helper: '外部文献链接', icon: Search },
];

const playbook = [
  { title: '选择论文', body: '从文献库进入，先看到该论文的历史会话。' },
  { title: '持续追问', body: '会话上下文保存在数据库，回答保持连续。' },
  { title: '引用证据', body: '回答附带来源片段，方便回到原文核对。' },
  { title: '形成草稿', body: '把可信证据推进到写作工作台。' },
];

export function Sidebar({ currentView, onNavigate }: Props) {
  return (
    <>
      <aside className="desktop-sidebar">
        <div className="flex items-center justify-between">
          <div className="text-[11px] font-bold text-[var(--color-ink)]">paperagent.local</div>
          <div className="inline-flex h-7 items-center rounded-full bg-[var(--color-primary-soft)] px-3 text-[10px] font-semibold text-[var(--color-primary)] shadow-sm">
            Research OS
          </div>
        </div>

        <div className="mt-8">
          <div className="flex h-9 w-9 items-center justify-center rounded-[var(--radius-sm)] border border-[var(--glass-border-strong)] bg-[var(--color-primary-soft)] text-[var(--color-primary)] shadow-sm">
            <Sparkles size={17} />
          </div>
          <h1 className="mt-4 text-[24px] font-extrabold leading-tight tracking-tight text-[var(--color-ink)]">
            Paper Agent
          </h1>
          <p className="mt-2 text-[13px] leading-5 text-[var(--color-ink-mute)]">
            面向论文阅读、追问、检索和写作的轻量研究工作台。
          </p>
        </div>

        <nav className="mt-7 space-y-1.5">
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

        <div className="mt-auto space-y-3 pb-1">
          <p className="px-1 text-[10px] font-semibold uppercase tracking-wider text-[var(--color-ink-mute)]">使用流程</p>
          {playbook.map((item, idx) => (
            <div key={item.title} className="playbook-card">
              <p className="playbook-card-title">{idx + 1}. {item.title}</p>
              <p className="playbook-card-body">{item.body}</p>
            </div>
          ))}
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
