import { readFileSync } from 'node:fs';
import { relative, resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');

const allowedRawApiFiles = new Set([
  'src/api/base.ts',
]);

const files = [
  'src/api/base.ts',
  'src/api/client.ts',
  'src/api/review.ts',
  'src/api/autonomous-writing.ts',
  'src/components/library/PaperViewer.tsx',
];

const rawApiPattern = /(['"`])\/api(?:\1|\/)/;
const localhostProxyPattern = /localhost:5173\/api/;

const failures = [];

for (const file of files) {
  const absolute = resolve(root, file);
  let source = '';
  try {
    source = readFileSync(absolute, 'utf8');
  } catch {
    if (file === 'src/api/base.ts') {
      failures.push(`${file}: shared API base helper is missing`);
      continue;
    }
    throw new Error(`Unable to read ${file}`);
  }

  const rel = relative(root, absolute).replaceAll('\\', '/');
  if (!allowedRawApiFiles.has(rel) && rawApiPattern.test(source)) {
    failures.push(`${rel}: raw /api URL found outside src/api/base.ts`);
  }
  if (!allowedRawApiFiles.has(rel) && localhostProxyPattern.test(source)) {
    failures.push(`${rel}: localhost proxy URL found outside src/api/base.ts`);
  }
}

if (failures.length > 0) {
  console.error(failures.join('\n'));
  process.exit(1);
}

console.log('API proxy URLs are centralized.');
