import { readFileSync } from 'node:fs';

const files = {
  css: readFileSync('src/index.css', 'utf8'),
  sidebar: readFileSync('src/components/layout/Sidebar.tsx', 'utf8'),
  paperCard: readFileSync('src/components/library/PaperCard.tsx', 'utf8'),
  paperEditPanel: readFileSync('src/components/library/PaperEditPanel.tsx', 'utf8'),
};

const failures = [];

function expectMatch(name, content, pattern) {
  if (!pattern.test(content)) {
    failures.push(name);
  }
}

function expectNoMatch(name, content, pattern) {
  if (pattern.test(content)) {
    failures.push(name);
  }
}

function expectSelectorRule(name, selector, declarations) {
  const selectorPattern = selector.split(',').map((part) =>
    part.trim().replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  ).join('\\s*,\\s*');
  const rule = new RegExp(`${selectorPattern}\\s*\\{(?<body>[\\s\\S]*?)\\}`, 'm').exec(files.css);

  if (!rule?.groups?.body) {
    failures.push(`${name}: missing selector ${selector}`);
    return;
  }

  for (const declaration of declarations) {
    if (!rule.groups.body.includes(declaration)) {
      failures.push(`${name}: ${selector} missing ${declaration}`);
    }
  }
}

expectSelectorRule('shared primary buttons protect icon/text layout', '.primary-button', [
  'min-width: 0;',
  'max-width: 100%;',
  'overflow: hidden;',
]);
expectSelectorRule('shared secondary buttons protect icon/text layout', '.secondary-button', [
  'min-width: 0;',
  'max-width: 100%;',
  'overflow: hidden;',
]);
expectSelectorRule('shared button icons never compress into labels', '.primary-button > svg, .secondary-button > svg, .danger-button > svg', [
  'flex-shrink: 0;',
]);
expectSelectorRule('shared button labels truncate instead of overlapping icons', '.button-label', [
  'min-width: 0;',
  'overflow: hidden;',
  'text-overflow: ellipsis;',
  'white-space: nowrap;',
]);
expectSelectorRule('sidebar nav row clips long localized copy safely', '.sidebar-nav-button', [
  'min-width: 0;',
  'overflow: hidden;',
]);
expectSelectorRule('sidebar nav icons keep stable width', '.sidebar-nav-icon', [
  'flex-shrink: 0;',
]);
expectSelectorRule('sidebar nav copy can shrink', '.sidebar-nav-copy', [
  'min-width: 0;',
  'overflow: hidden;',
]);
expectSelectorRule('mobile tab buttons have fixed layout bounds', '.mobile-tab-button', [
  'min-width: 0;',
  'overflow: hidden;',
]);
expectSelectorRule('mobile tab labels truncate inside each grid cell', '.mobile-tab-label', [
  'max-width: 100%;',
  'overflow: hidden;',
  'text-overflow: ellipsis;',
  'white-space: nowrap;',
]);
expectSelectorRule('paper card actions avoid squeezing three text buttons', '.paper-card-actions', [
  'grid-template-columns: repeat(3, minmax(88px, 1fr));',
]);
expectSelectorRule('paper card action buttons share one visual treatment', '.paper-card-action-button', [
  'min-height: 38px;',
  'width: 100%;',
  'padding: 0 12px;',
]);
expectSelectorRule('paper card action icons keep stable width', '.paper-card-action-button > svg', [
  'flex-shrink: 0;',
]);
expectSelectorRule('paper card delete action keeps danger color while sharing size', '.paper-card-danger-action', [
  'color: rgb(185, 28, 28);',
  'border-color: rgba(185, 28, 28, 0.28);',
]);
expectSelectorRule('edit panel leaves safe space from viewport edge', '.edit-panel-shell', [
  'margin-left: clamp(24px, 6vw, 96px);',
  'position: relative;',
]);
expectSelectorRule('edit panel content has responsive horizontal padding', '.edit-panel-section', [
  'padding-left: clamp(28px, 4vw, 56px);',
  'padding-right: clamp(28px, 4vw, 56px);',
]);
expectSelectorRule('edit panel close button is anchored to panel corner', '.edit-panel-close', [
  'position: absolute;',
  'top: 20px;',
  'right: 20px;',
  'z-index: 2;',
]);

expectMatch('Sidebar uses stable icon class', files.sidebar, /className="sidebar-nav-icon"/);
expectMatch('Sidebar wraps desktop nav copy', files.sidebar, /className="sidebar-nav-copy"/);
expectMatch('Sidebar wraps mobile labels', files.sidebar, /className="mobile-tab-label"/);
expectMatch('PaperCard uses action grid class', files.paperCard, /className="[^"]*\bpaper-card-actions\b[^"]*"/);
expectMatch('PaperCard uses one shared action button class for all actions', files.paperCard, /paper-card-action-button[\s\S]*paper-card-action-button[\s\S]*paper-card-action-button/);
expectMatch('PaperCard delete action keeps a danger semantic modifier', files.paperCard, /className="[^"]*\bpaper-card-action-button\b[^"]*\bpaper-card-danger-action\b[^"]*"/);
expectNoMatch('PaperCard no longer mixes legacy action button styling in card footer', files.paperCard, /\b(primary-button|secondary-button|danger-button|paper-card-chat-action)\b/);
expectMatch('PaperCard wraps button labels', files.paperCard, /className="button-label"/);
expectMatch('PaperEditPanel uses viewport-safe shell class', files.paperEditPanel, /className="[^"]*\bedit-panel-shell\b[^"]*"/);
expectMatch('PaperEditPanel uses shared edit panel section padding', files.paperEditPanel, /className="[^"]*\bedit-panel-section\b[^"]*"/);
expectMatch('PaperEditPanel anchors close icon with a dedicated class', files.paperEditPanel, /className="[^"]*\bedit-panel-close\b[^"]*"/);

if (failures.length > 0) {
  console.error('Icon/text layout contract failed:');
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log('Icon/text layout contract passed.');
