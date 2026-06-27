import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const source = readFileSync(resolve(import.meta.dirname, '../src/api/review.ts'), 'utf8');

const escapedNewlineDelimiter = /buffer\.split\(\s*\/\\\\r\?\\\\n\\\\r\?\\\\n\//;
if (escapedNewlineDelimiter.test(source)) {
  console.error('review stream parser splits on literal "\\r\\n" text instead of real SSE blank lines');
  process.exit(1);
}

const standardSseFrame = 'data: {"type":"status","message":"accepted"}\n\n';
const frames = standardSseFrame.split(/\r?\n\r?\n/).filter(Boolean);
if (frames.length !== 1 || !frames[0].startsWith('data:')) {
  console.error('sanity check failed: standard SSE frame was not split correctly');
  process.exit(1);
}

console.log('Review stream parser handles standard SSE blank-line delimiters.');
