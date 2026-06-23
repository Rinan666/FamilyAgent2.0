const { app, BrowserWindow, Menu, dialog, shell } = require('electron');
const { spawn } = require('child_process');
const fs = require('fs');
const http = require('http');
const path = require('path');

const FRONTEND_URL = process.env.FAMILYAGENT_FRONTEND_URL || 'http://127.0.0.1:3000';
const READY_TIMEOUT_MS = Number(process.env.FAMILYAGENT_READY_TIMEOUT_MS || 240000);
const POLL_INTERVAL_MS = 2500;

let mainWindow;
let isStarting = false;
let launcherState;

function getStackRoot() {
  if (app.isPackaged) {
    return path.join(process.resourcesPath, 'familyagent-stack');
  }

  return path.resolve(__dirname, '..', '..');
}

function getLauncherState() {
  if (launcherState) {
    return launcherState;
  }

  const stackRoot = getStackRoot();
  const configDir = path.join(app.getPath('userData'), 'config');
  const logDir = path.join(app.getPath('userData'), 'logs');
  const envFile = path.join(configDir, '.env.docker');

  launcherState = {
    stackRoot,
    composeFile: path.join(stackRoot, 'docker-compose.stack.yml'),
    envExampleFile: path.join(stackRoot, '.env.docker.example'),
    envFile,
    logDir,
    launcherLogFile: path.join(logDir, 'launcher.log')
  };

  return launcherState;
}

function toComposePath(filePath) {
  return filePath.replace(/\\/g, '/');
}

function appendLog(message) {
  const state = getLauncherState();
  fs.mkdirSync(state.logDir, { recursive: true });
  fs.appendFileSync(state.launcherLogFile, `[${new Date().toISOString()}] ${message}\n`, 'utf8');
}

function ensureRequiredFiles() {
  const state = getLauncherState();

  if (!fs.existsSync(state.composeFile)) {
    throw new Error(`Missing Docker Compose file: ${state.composeFile}`);
  }

  if (!fs.existsSync(state.envExampleFile)) {
    throw new Error(`Missing environment template: ${state.envExampleFile}`);
  }

  fs.mkdirSync(path.dirname(state.envFile), { recursive: true });
  if (!fs.existsSync(state.envFile)) {
    fs.copyFileSync(state.envExampleFile, state.envFile);
    appendLog(`Created config from template: ${state.envFile}`);
  }
}

function createLoadingHtml() {
  return `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline';">
  <title>FamilyAgent</title>
  <style>
    * { box-sizing: border-box; }
    body {
      margin: 0;
      min-height: 100vh;
      display: grid;
      place-items: center;
      color: #17202a;
      background: #f7f5ef;
      font-family: "Segoe UI", "Microsoft YaHei", Arial, sans-serif;
    }
    main {
      width: min(720px, calc(100vw - 48px));
      padding: 32px;
      border: 1px solid #ddd8ca;
      border-radius: 8px;
      background: #fffdf8;
      box-shadow: 0 18px 48px rgba(33, 28, 15, 0.12);
    }
    h1 {
      margin: 0 0 12px;
      font-size: 28px;
      font-weight: 700;
      letter-spacing: 0;
    }
    #title {
      margin: 0 0 10px;
      font-size: 17px;
      font-weight: 600;
    }
    #detail {
      min-height: 84px;
      margin: 0;
      color: #4f5b66;
      white-space: pre-wrap;
      line-height: 1.55;
      word-break: break-word;
    }
    .bar {
      height: 6px;
      margin-top: 24px;
      overflow: hidden;
      border-radius: 999px;
      background: #e5e0d2;
    }
    .bar span {
      display: block;
      width: 42%;
      height: 100%;
      border-radius: inherit;
      background: #157f6e;
      animation: move 1.4s ease-in-out infinite;
    }
    body.error .bar span { background: #b42318; animation: none; width: 100%; }
    body.ready .bar span { background: #157f6e; animation: none; width: 100%; }
    @keyframes move {
      0% { transform: translateX(-100%); }
      50% { transform: translateX(75%); }
      100% { transform: translateX(250%); }
    }
  </style>
</head>
<body>
  <main>
    <h1>FamilyAgent</h1>
    <p id="title">正在启动本地服务</p>
    <p id="detail">首次启动需要构建 Docker 镜像，可能需要几分钟。</p>
    <div class="bar"><span></span></div>
  </main>
  <script>
    window.updateStatus = function (status) {
      document.body.className = status.state || "";
      document.getElementById("title").textContent = status.title || "";
      document.getElementById("detail").textContent = status.detail || "";
    };
  </script>
</body>
</html>`;
}

function updateStatus(title, detail, state = '') {
  if (!mainWindow || mainWindow.isDestroyed()) {
    return;
  }

  const status = JSON.stringify({ title, detail, state });
  mainWindow.webContents.executeJavaScript(`window.updateStatus && window.updateStatus(${status});`).catch(() => {});
}

function loadLoadingPage() {
  mainWindow.loadURL(`data:text/html;charset=utf-8,${encodeURIComponent(createLoadingHtml())}`);
}

function buildDockerArgs(command) {
  const state = getLauncherState();
  return [
    'compose',
    '--env-file',
    state.envFile,
    '-f',
    state.composeFile,
    ...command
  ];
}

function runDocker(command, options = {}) {
  const state = getLauncherState();
  const args = Array.isArray(command) ? command : buildDockerArgs(command);
  const env = {
    ...process.env,
    STACK_ENV_FILE: toComposePath(state.envFile)
  };

  return new Promise((resolve, reject) => {
    appendLog(`docker ${args.join(' ')}`);
    const child = spawn('docker', args, {
      cwd: state.stackRoot,
      env,
      windowsHide: true
    });

    let output = '';

    child.stdout.on('data', (chunk) => {
      const text = chunk.toString();
      output += text;
      appendLog(text.trimEnd());
      if (options.onOutput) {
        options.onOutput(text);
      }
    });

    child.stderr.on('data', (chunk) => {
      const text = chunk.toString();
      output += text;
      appendLog(text.trimEnd());
      if (options.onOutput) {
        options.onOutput(text);
      }
    });

    child.on('error', (error) => {
      reject(new Error(`Failed to run Docker: ${error.message}`));
    });

    child.on('close', (code) => {
      if (code === 0) {
        resolve(output);
        return;
      }

      reject(new Error(output.trim() || `Docker exited with code ${code}`));
    });
  });
}

function waitForFrontend() {
  const startedAt = Date.now();

  return new Promise((resolve, reject) => {
    const poll = () => {
      const request = http.get(FRONTEND_URL, (response) => {
        response.resume();
        if (response.statusCode && response.statusCode < 500) {
          resolve();
          return;
        }

        scheduleNext();
      });

      request.setTimeout(4000, () => {
        request.destroy();
        scheduleNext();
      });

      request.on('error', scheduleNext);
    };

    const scheduleNext = () => {
      if (Date.now() - startedAt > READY_TIMEOUT_MS) {
        reject(new Error(`Frontend did not become ready at ${FRONTEND_URL}`));
        return;
      }

      setTimeout(poll, POLL_INTERVAL_MS);
    };

    poll();
  });
}

async function assertDockerReady() {
  await runDocker(['--version']);
  await runDocker(['compose', 'version']);
}

function lastOutputLine(text) {
  const lines = text.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
  return lines[lines.length - 1] || 'Docker is working...';
}

async function startStack() {
  if (isStarting) {
    return;
  }

  isStarting = true;
  loadLoadingPage();

  try {
    ensureRequiredFiles();
    updateStatus('正在检查 Docker', '请确认 Docker Desktop 已经启动。');
    await assertDockerReady();

    updateStatus('正在启动服务', '正在构建并启动 PostgreSQL、Redis、RabbitMQ、MinIO、AI 服务、后端和前端。');
    await runDocker(buildDockerArgs(['up', '-d', '--build', '--remove-orphans']), {
      onOutput: (text) => updateStatus('正在启动服务', lastOutputLine(text))
    });

    updateStatus('正在等待前端就绪', `服务已经启动，正在连接 ${FRONTEND_URL}。`);
    await waitForFrontend();

    updateStatus('启动完成', '正在打开 FamilyAgent。', 'ready');
    await mainWindow.loadURL(FRONTEND_URL);
  } catch (error) {
    appendLog(error.stack || error.message);
    updateStatus(
      '启动失败',
      `${error.message}\n\n可以从菜单打开配置文件和日志目录排查。`,
      'error'
    );
    dialog.showErrorBox('FamilyAgent 启动失败', error.message);
  } finally {
    isStarting = false;
  }
}

async function stopStack() {
  const state = getLauncherState();

  try {
    ensureRequiredFiles();
    updateStatus('正在停止服务', '正在执行 docker compose down。');
    await runDocker(buildDockerArgs(['down']));
    updateStatus('服务已停止', `配置文件: ${state.envFile}`, 'ready');
  } catch (error) {
    appendLog(error.stack || error.message);
    dialog.showErrorBox('FamilyAgent 停止失败', error.message);
  }
}

function createMenu() {
  const state = getLauncherState();

  return Menu.buildFromTemplate([
    {
      label: 'FamilyAgent',
      submenu: [
        { label: '打开应用', click: () => mainWindow.loadURL(FRONTEND_URL) },
        { label: '启动/重启服务', click: startStack },
        { label: '停止服务', click: stopStack },
        { type: 'separator' },
        { label: '打开配置文件', click: () => shell.openPath(state.envFile) },
        { label: '打开日志目录', click: () => shell.openPath(state.logDir) },
        { type: 'separator' },
        { role: 'quit', label: '退出' }
      ]
    },
    {
      label: '视图',
      submenu: [
        { role: 'reload', label: '刷新' },
        { role: 'toggleDevTools', label: '开发者工具' }
      ]
    }
  ]);
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 860,
    minWidth: 960,
    minHeight: 640,
    title: 'FamilyAgent',
    backgroundColor: '#f7f5ef',
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });

  Menu.setApplicationMenu(createMenu());
  startStack();
}

app.whenReady().then(createWindow);

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) {
    createWindow();
  }
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});
