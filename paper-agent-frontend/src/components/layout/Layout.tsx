import { useState } from 'react';
import { Sidebar } from './Sidebar';
import { ChatWindow } from '../chat/ChatWindow';
import { DiscoveryPage } from '../discovery/DiscoveryPage';
import { LibraryPage } from '../library/LibraryPage';
import { WritingPage } from '../writing/WritingPage';
import { ReviewPage } from '../review/ReviewPage';

export type View = 'chat' | 'library' | 'discovery' | 'writing' | 'review';

export function Layout() {
  const [currentView, setCurrentView] = useState<View>('chat');

  return (
    <div className="app-shell">
      <div className="app-frame">
        <Sidebar currentView={currentView} onNavigate={setCurrentView} />
        <main className="main-canvas">
          {currentView === 'chat' && <ChatWindow />}
          {currentView === 'library' && <LibraryPage onNavigate={setCurrentView} />}
          {currentView === 'writing' && <WritingPage />}
          {currentView === 'review' && <ReviewPage />}
          {currentView === 'discovery' && <DiscoveryPage />}
        </main>
      </div>
    </div>
  );
}
