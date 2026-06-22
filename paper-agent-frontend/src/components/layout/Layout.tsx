import { useState } from 'react';
import { Sidebar } from './Sidebar';
import { ChatWindow } from '../chat/ChatWindow';
import { DiscoveryPage } from '../discovery/DiscoveryPage';
import { LibraryPage } from '../library/LibraryPage';
import { WritingPage } from '../writing/WritingPage';

export type View = 'chat' | 'library' | 'discovery' | 'writing';

export function Layout() {
  const [currentView, setCurrentView] = useState<View>('chat');

  return (
    <div className="app-shell flex h-screen">
      <Sidebar currentView={currentView} onNavigate={setCurrentView} />
      <main className="flex-1 overflow-hidden">
        {currentView === 'chat' && <ChatWindow />}
        {currentView === 'library' && <LibraryPage />}
        {currentView === 'writing' && <WritingPage />}
        {currentView === 'discovery' && <DiscoveryPage />}
      </main>
    </div>
  );
}
