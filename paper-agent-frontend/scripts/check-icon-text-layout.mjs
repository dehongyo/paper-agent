import { readFileSync } from 'node:fs';

const files = {
  css: readFileSync('src/index.css', 'utf8'),
  sidebar: readFileSync('src/components/layout/Sidebar.tsx', 'utf8'),
  paperCard: readFileSync('src/components/library/PaperCard.tsx', 'utf8'),
};

const failures = [];

function expectMatch(name, content, pattern) {
  if (!pattern.test(content)) {
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
  'grid-template-columns: repeat(3, minmax(0, 1fr));',
]);

expectMatch('Sidebar uses stable icon class', files.sidebar, /className="sidebar-nav-icon"/);
expectMatch('Sidebar wraps desktop nav copy', files.sidebar, /className="sidebar-nav-copy"/);
expectMatch('Sidebar wraps mobile labels', files.sidebar, /className="mobile-tab-label"/);
expectMatch('PaperCard uses action grid class', files.paperCard, /className="[^"]*\bpaper-card-actions\b[^"]*"/);
expectMatch('PaperCard wraps button labels', files.paperCard, /className="button-label"/);

if (failures.length > 0) {
  console.error('Icon/text layout contract failed:');
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log('Icon/text layout contract passed.');
