const fs = require('fs');
const path = require('path');

const desktopRoot = path.resolve(__dirname, '..');
const repoRoot = path.resolve(desktopRoot, '..');
const stageRoot = path.join(desktopRoot, '.stack');

const excludedDirectoryNames = new Set([
  '.git',
  '.next',
  '.pytest_cache',
  '.ruff_cache',
  '.turbo',
  '.venv',
  '.venv-test',
  '__pycache__',
  'coverage',
  'dist',
  'logs',
  'node_modules',
  'output',
  'target'
]);

const excludedFileNames = new Set([
  '.codex-runtime-pids.txt',
  '.service-pids.txt',
  'tsconfig.tsbuildinfo'
]);

const excludedExtensions = new Set([
  '.log',
  '.pyc'
]);

const excludedRelativePaths = new Set([
  path.normalize('ai-service/dip/data'),
  path.normalize('ai-service/dip/output'),
  path.normalize('ai-service/dip/experiment.py'),
  path.normalize('ai-service/dip/setup_data.py'),
  path.normalize('ai-service/dip/visualize.py'),
  path.normalize('ai-service/tests'),
  path.normalize('frontend/vitest.config.ts')
]);

const includedRoots = [
  'backend',
  'frontend',
  'ai-service',
  'docker-compose.stack.yml',
  '.env.docker.example'
];

let copiedFiles = 0;
let copiedBytes = 0;

function isExcluded(sourcePath) {
  const relativePath = path.relative(repoRoot, sourcePath);
  if (!relativePath || relativePath.startsWith('..')) {
    return false;
  }

  const normalizedRelativePath = path.normalize(relativePath);
  if (excludedRelativePaths.has(normalizedRelativePath)) {
    return true;
  }

  const parts = normalizedRelativePath.split(path.sep);
  if (parts.some((part) => excludedDirectoryNames.has(part))) {
    return true;
  }

  const name = path.basename(sourcePath);
  if (excludedFileNames.has(name)) {
    return true;
  }

  return excludedExtensions.has(path.extname(name));
}

function copyRecursive(sourcePath, destinationPath) {
  if (!fs.existsSync(sourcePath) || isExcluded(sourcePath)) {
    return;
  }

  const stat = fs.statSync(sourcePath);
  if (stat.isDirectory()) {
    fs.mkdirSync(destinationPath, { recursive: true });
    for (const entry of fs.readdirSync(sourcePath)) {
      copyRecursive(path.join(sourcePath, entry), path.join(destinationPath, entry));
    }
    return;
  }

  if (!stat.isFile()) {
    return;
  }

  fs.mkdirSync(path.dirname(destinationPath), { recursive: true });
  fs.copyFileSync(sourcePath, destinationPath);
  copiedFiles += 1;
  copiedBytes += stat.size;
}

function formatBytes(bytes) {
  if (bytes < 1024) {
    return `${bytes} B`;
  }

  const units = ['KB', 'MB', 'GB'];
  let value = bytes / 1024;
  let unitIndex = 0;
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024;
    unitIndex += 1;
  }

  return `${value.toFixed(1)} ${units[unitIndex]}`;
}

fs.rmSync(stageRoot, { recursive: true, force: true });
fs.mkdirSync(stageRoot, { recursive: true });

for (const includedRoot of includedRoots) {
  copyRecursive(
    path.join(repoRoot, includedRoot),
    path.join(stageRoot, includedRoot)
  );
}

console.log(`Staged ${copiedFiles} files (${formatBytes(copiedBytes)}) in ${stageRoot}`);
