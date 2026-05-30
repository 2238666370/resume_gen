/**
 * 简历生成器 — 本地服务器
 * 打包为 .exe 后双击即可运行，自动打开浏览器
 */
const http = require('http');
const fs = require('fs');
const path = require('path');
const { exec } = require('child_process');

const PORT = 2048;
const DIST_DIR = path.join(__dirname, 'dist');

// MIME 类型映射
const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.css':  'text/css; charset=utf-8',
  '.js':   'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png':  'image/png',
  '.jpg':  'image/jpeg',
  '.svg':  'image/svg+xml',
  '.ico':  'image/x-icon',
};

function getMime(filePath) {
  return MIME[path.extname(filePath).toLowerCase()] || 'application/octet-stream';
}

// SPA 路由：所有非文件路径返回 index.html
function serveFile(res, filePath) {
  const fullPath = path.join(DIST_DIR, filePath);
  // 安全检查：防止目录遍历
  if (!fullPath.startsWith(DIST_DIR)) {
    res.writeHead(403);
    res.end('Forbidden');
    return;
  }
  fs.readFile(fullPath, (err, data) => {
    if (err) {
      // 文件不存在 → SPA fallback
      fs.readFile(path.join(DIST_DIR, 'index.html'), (err2, data2) => {
        if (err2) {
          res.writeHead(404);
          res.end('Not Found');
          return;
        }
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
        res.end(data2);
      });
      return;
    }
    res.writeHead(200, { 'Content-Type': getMime(fullPath) });
    res.end(data);
  });
}

const server = http.createServer((req, res) => {
  let urlPath = req.url.split('?')[0]; // 去掉 query string
  if (urlPath === '/') urlPath = '/index.html';

  // 去掉开头的 /
  const filePath = urlPath.startsWith('/') ? urlPath.slice(1) : urlPath;
  serveFile(res, filePath);
});

server.listen(PORT, () => {
  const url = `http://localhost:${PORT}`;
  console.log(`\n  📄 简历生成器已启动 → ${url}\n`);

  // 自动打开浏览器（跨平台）
  const platform = process.platform;
  const cmd = platform === 'win32'
    ? `start "" "${url}"`
    : platform === 'darwin'
      ? `open "${url}"`
      : `xdg-open "${url}"`;
  exec(cmd, (err) => {
    if (err) console.log('  请手动打开浏览器访问:', url);
  });
});

// 优雅退出
process.on('SIGINT', () => {
  console.log('\n  服务器已关闭');
  server.close();
  process.exit(0);
});

process.on('SIGTERM', () => {
  server.close();
  process.exit(0);
});
