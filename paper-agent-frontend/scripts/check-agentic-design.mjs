import { readFileSync } from 'node:fs';

const css = readFileSync('src/index.css', 'utf8');
const failures = [];

function expectIncludes(name, snippet) {
  if (!css.includes(snippet)) {
    failures.push(`${name}: missing ${snippet}`);
  }
}

expectIncludes('light paper background token', '--bg: #fafaf8;');
expectIncludes('white surface token', '--surface: #ffffff;');
expectIncludes('black primary accent token', '--accent: #0d0d0d;');
expectIncludes('legacy alias uses light background', '--color-bg: var(--bg);');
expectIncludes('legacy alias uses glass paper surface', '--color-paper: rgba(255, 255, 255, 0.78);');
expectIncludes('legacy alias uses black accent', '--color-primary: var(--accent);');
expectIncludes('body uses light base', 'var(--bg);');
expectIncludes('app frame uses glass surface', 'background: rgba(255, 255, 255, 0.55);');
expectIncludes('sidebar uses frosted glass', 'background: rgba(255, 255, 255, 0.55);');
expectIncludes('primary action uses black fill', 'background: var(--fg);');
expectIncludes('selection uses subtle ink tint', 'background: rgba(0, 0, 0, 0.1);');

if (failures.length > 0) {
  console.error('Agentic design contract failed:');
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log('Agentic design contract passed.');
