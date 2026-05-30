/**
 * 简历生成器 — Electron 主进程
 * 加载 Vite 构建的 dist/ 静态文件
 */
const { app, BrowserWindow, shell, ipcMain, dialog } = require('electron');
const path = require('path');
const fs = require('fs');

let mainWindow = null;

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1400,
    height: 900,
    minWidth: 1000,
    minHeight: 700,
    title: '简历生成器',
    icon: path.join(__dirname, 'dist', 'favicon.svg'),
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      preload: path.join(__dirname, 'preload.cjs'),
    },
  });

  // 去掉默认菜单栏
  mainWindow.setMenuBarVisibility(false);

  // 加载 dist/index.html（相对路径，兼容 file://）
  mainWindow.loadFile(path.join(__dirname, 'dist', 'index.html'));

  // 外部链接用系统浏览器打开
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url);
    return { action: 'deny' };
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

// ─── IPC: PDF 导出（主进程 printToPDF） ───

ipcMain.handle('export-pdf', async (_, { html, defaultName }) => {
  // 弹出保存对话框
  const { canceled, filePath } = await dialog.showSaveDialog(mainWindow, {
    title: '导出 PDF',
    defaultPath: path.join(app.getPath('desktop'), `${defaultName || 'resume'}.pdf`),
    filters: [{ name: 'PDF 文件', extensions: ['pdf'] }],
  });

  if (canceled || !filePath) return { success: false, canceled: true };

  // 创建隐藏窗口加载 HTML → printToPDF
  const win = new BrowserWindow({
    show: false,
    webPreferences: { nodeIntegration: false, contextIsolation: true },
  });

  try {
    await win.loadURL(
      `data:text/html;charset=utf-8,${encodeURIComponent(html)}`
    );

    const pdf = await win.webContents.printToPDF({
      printBackground: true,
      pageSize: 'A4',
      margins: { top: 0, bottom: 0, left: 0, right: 0 },
      preferCSSPageSize: true,
    });

    fs.writeFileSync(filePath, pdf);
    return { success: true, filePath };
  } finally {
    win.destroy();
  }
});

// ─── 应用生命周期 ───

app.whenReady().then(createWindow);

app.on('window-all-closed', () => {
  app.quit();
});

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) {
    createWindow();
  }
});
