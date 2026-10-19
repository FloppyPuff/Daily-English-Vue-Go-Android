// The word and sentence banks live once, in ../content. They are copied into
// public/ before dev and build so the Vue app never carries a second copy in git.
import { cpSync, mkdirSync, rmSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const source = resolve(root, '..', 'content');
const target = join(root, 'public', 'data');

rmSync(target, { recursive: true, force: true });
mkdirSync(target, { recursive: true });
for (const file of ['words.json', 'sentences.json']) {
  cpSync(join(source, file), join(target, file));
}
cpSync(join(source, 'papers'), join(target, 'papers'), { recursive: true });
console.log(`synced ${source} -> ${target}`);
