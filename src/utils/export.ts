import html2canvas from 'html2canvas';
import type { ResumeData } from '../types/resume';

// ─── 判断是否在 Electron 环境 ───
const isElectron = (): boolean =>
  typeof window !== 'undefined' && !!window.electron;

// ─── 收集页面所有 CSS（用于 Electron PDF 导出） ───
function collectAllCSS(): string {
  let css = '';

  // 1. 内联 <style> 标签
  document.querySelectorAll('style').forEach((s) => {
    css += s.textContent + '\n';
  });

  // 2. 通过 CSSOM 收集所有样式表规则（涵盖 Tailwind 等）
  for (const sheet of document.styleSheets) {
    try {
      for (const rule of sheet.cssRules) {
        css += rule.cssText + '\n';
      }
    } catch {
      // 跨域样式表无法读取，跳过
    }
  }

  return css;
}

// ─── 构建完整 HTML（含内联 CSS + 简历 DOM） ───
function buildFullHTML(element: HTMLElement): string {
  const allCSS = collectAllCSS();

  // 克隆简历 DOM
  const clone = element.cloneNode(true) as HTMLElement;
  clone.removeAttribute('id');
  clone.style.boxShadow = 'none';
  clone.style.borderRadius = '0';
  clone.style.margin = '0';
  // 去掉缩放变换
  clone.style.transform = 'none';

  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <title>简历</title>
  <style>
    /* ── 全局 reset ── */
    *, *::before, *::after { box-sizing: border-box; }
    body {
      margin: 0; padding: 0;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC',
                   'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
      -webkit-font-smoothing: antialiased;
    }

    /* ── 应用原有所有样式 ── */
    ${allCSS}

    /* ── 打印专用（Electron printToPDF，页边距由主进程控制为 0） ── */
    @media print {
      @page {
        size: A4;
        margin: 0;
      }
      body {
        margin: 0 !important;
        padding: 0 !important;
        background: white !important;
      }
      * {
        -webkit-print-color-adjust: exact !important;
        print-color-adjust: exact !important;
      }
    }
  </style>
</head>
<body>
  <div id="root-print">${clone.outerHTML}</div>
</body>
</html>`;
}

// ─── 导出 PNG（不变） ───

export async function exportToPNG(element: HTMLElement, filename = 'resume') {
  const canvas = await html2canvas(element, {
    scale: 2,
    useCORS: true,
    backgroundColor: '#ffffff',
    logging: false,
  });
  const link = document.createElement('a');
  link.download = `${filename}.png`;
  link.href = canvas.toDataURL('image/png');
  link.click();
}

// ─── 导出 PDF ───

/**
 * Electron 环境：直接调用主进程 printToPDF，弹出保存对话框
 * 浏览器环境：iframe 预览 + 浏览器原生打印（兼容旧逻辑）
 */
export async function exportToPDF(element: HTMLElement, filename = 'resume') {
  if (isElectron()) {
    return exportToPDF_Electron(element, filename);
  }
  return exportToPDF_Browser(element, filename);
}

/** Electron：收集样式 + 构建 HTML → IPC → 主进程 printToPDF + 保存对话框 */
async function exportToPDF_Electron(
  element: HTMLElement,
  filename: string,
): Promise<void> {
  const html = buildFullHTML(element);

  const result = await window.electron!.invoke('export-pdf', {
    html,
    defaultName: filename,
  });

  if (result && typeof result === 'object') {
    const r = result as { success?: boolean; canceled?: boolean; filePath?: string };
    if (r.canceled) {
      return; // 用户取消保存
    }
    if (r.success) {
      showToast('✓ PDF 已导出', '#22c55e');
    } else {
      showToast('导出失败，请重试', '#ef4444');
    }
  }
}

/** 浏览器：使用原生打印 → iframe 预览 */
async function exportToPDF_Browser(
  element: HTMLElement,
  filename: string,
): Promise<void> {
  // 1. 创建全屏 iframe 作为预览 + 打印容器
  const iframe = document.createElement('iframe');
  iframe.style.cssText =
    'position:fixed;top:0;left:0;width:100%;height:100%;border:none;z-index:99999;background:#f0f2f5;';
  iframe.title = filename;
  document.body.appendChild(iframe);

  const doc = iframe.contentDocument!;
  const win = iframe.contentWindow!;

  // 2. 写入完整 HTML（含控制栏 + 简历内容）
  doc.open();
  doc.write(`<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <title>简历预览 - ${filename}</title>
</head>
<body>
  <div id="print-toolbar">
    <div class="print-toolbar-left">
      <span class="print-toolbar-title">📄 简历打印预览</span>
      <span class="print-toolbar-hint">💡 打印时请选择「<b>另存为 PDF</b>」，页边距建议选择「<b>无</b>」</span>
    </div>
    <div class="print-toolbar-actions">
      <button id="btn-print" class="print-btn print-btn-primary">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M6 9V2h12v7"/><path d="M6 18H4a2 2 0 01-2-2v-5a2 2 0 012-2h16a2 2 0 012 2v5a2 2 0 01-2 2h-2"/>
          <rect x="6" y="14" width="12" height="8"/>
        </svg>
        打印
      </button>
      <button id="btn-return" class="print-btn print-btn-secondary">← 返回编辑</button>
    </div>
  </div>
  <div id="print-content"></div>
</body>
</html>`);
  doc.close();

  // 3. 复制父文档中的所有样式
  const parentStyles = document.querySelectorAll('style');
  parentStyles.forEach((s) => doc.head.appendChild(s.cloneNode(true)));

  const parentLinks = document.querySelectorAll('link[rel="stylesheet"]');
  parentLinks.forEach((l) => doc.head.appendChild(l.cloneNode(true)));

  // 4. 注入全部 CSS（控制栏样式 + 打印专用样式）
  const printCSS = doc.createElement('style');
  printCSS.textContent = `
    /* ---- 基础重置 ---- */
    html { height: 100%; }
    body {
      margin: 0; padding: 0;
      height: 100%;
      display: flex; flex-direction: column;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC',
                   'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
      background: #f0f2f5;
    }

    /* ---- 控制栏 ---- */
    #print-toolbar {
      flex-shrink: 0;
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0 24px;
      height: 52px;
      background: #fff;
      border-bottom: 1px solid #e5e7eb;
      box-shadow: 0 1px 3px rgba(0,0,0,0.06);
      z-index: 10;
    }
    .print-toolbar-left {
      display: flex;
      align-items: baseline;
      gap: 14px;
      min-width: 0;
    }
    .print-toolbar-title {
      font-size: 15px;
      font-weight: 600;
      color: #374151;
      white-space: nowrap;
    }
    .print-toolbar-hint {
      font-size: 13px;
      color: #6b7280;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .print-toolbar-hint b { color: #2563eb; }
    .print-toolbar-actions {
      display: flex; gap: 10px;
      flex-shrink: 0;
    }
    .print-btn {
      display: inline-flex; align-items: center; gap: 6px;
      padding: 7px 18px;
      border: none; border-radius: 8px;
      font-size: 14px; font-weight: 500;
      cursor: pointer;
      transition: all 0.15s;
      white-space: nowrap;
    }
    .print-btn-primary {
      background: #2563eb; color: #fff;
    }
    .print-btn-primary:hover { background: #1d4ed8; }
    .print-btn-secondary {
      background: #f3f4f6; color: #4b5563;
    }
    .print-btn-secondary:hover { background: #e5e7eb; }

    /* ---- 简历内容区域（缩放以完整显示） ---- */
    #print-content {
      flex: 1;
      overflow: hidden;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 16px;
    }
    #print-content > div {
      background: #fff;
      box-shadow: 0 1px 6px rgba(0,0,0,0.1);
      width: 210mm;
      min-height: 297mm;
      transform-origin: center center;
    }

    /* ---- 打印样式（模板无关的通则） ---- */
    @media print {
      html, body {
        margin: 0 !important; padding: 0 !important;
        background: white !important;
        width: auto !important; height: auto !important;
        overflow: visible !important;
      }
      #print-toolbar { display: none !important; }
      #print-content {
        overflow: visible !important;
        padding: 0 !important;
        display: block !important;
        align-items: unset !important;
        justify-content: unset !important;
      }
      #print-content > div {
        box-shadow: none !important;
        border-radius: 0 !important;
        margin: 0 !important;
        width: 100% !important;
        min-height: auto !important;
        height: auto !important;
        transform: none !important;
      }
      * {
        -webkit-print-color-adjust: exact !important;
        print-color-adjust: exact !important;
      }
    }
  `;
  doc.head.appendChild(printCSS);

  // 5. 克隆简历 DOM 并插入内容区
  const clone = element.cloneNode(true) as HTMLElement;
  clone.removeAttribute('id');
  clone.style.boxShadow = 'none';
  clone.style.borderRadius = '0';
  clone.style.margin = '0';
  const contentArea = doc.getElementById('print-content')!;
  contentArea.appendChild(clone);

  // 5.1 根据模板类型注入差异化打印规则
  const template = clone.dataset.template || 'classic';

  const pageMargins: Record<string, string> = {
    classic: '10mm 12mm',
    modern:  '0',
    minimal: '6mm 8mm',
  };

  const rootPadStripping = (template === 'modern')
    ? ''
    : '#print-content > div { padding: 0 !important; }';

  const templatePrintCSS = doc.createElement('style');
  templatePrintCSS.textContent = `
    @media print {
      @page {
        size: A4;
        margin: ${pageMargins[template] || pageMargins.classic};
      }
      ${rootPadStripping}
    }
  `;
  doc.head.appendChild(templatePrintCSS);

  // 5.5 计算缩放比例
  const scaleResume = () => {
    const cw = contentArea.clientWidth;
    const ch = contentArea.clientHeight;
    if (cw <= 0 || ch <= 0) return;
    const resumeW = clone.offsetWidth || 210 * 3.78;
    const resumeH = clone.offsetHeight || 297 * 3.78;
    const padX = 32;
    const padY = 32;
    const sx = (cw - padX) / resumeW;
    const sy = (ch - padY) / resumeH;
    const s = Math.min(sx, sy, 1);
    (clone as HTMLElement).style.transform = `scale(${s})`;
  };
  scaleResume();
  win.addEventListener('resize', scaleResume);

  // 6. 绑定按钮事件
  const cleanup = () => {
    if (document.body.contains(iframe)) {
      document.body.removeChild(iframe);
    }
  };

  doc.getElementById('btn-print')!.addEventListener('click', () => {
    win.focus();
    win.print();
  });

  doc.getElementById('btn-return')!.addEventListener('click', cleanup);

  win.addEventListener('afterprint', cleanup, { once: true });
  setTimeout(cleanup, 180_000);

  // 7. 等待字体和图片加载完毕
  await new Promise<void>((resolve) => {
    if (doc.fonts?.ready) {
      doc.fonts.ready.then(() => setTimeout(resolve, 400));
    } else {
      setTimeout(resolve, 800);
    }
  });

  const images = clone.querySelectorAll('img');
  if (images.length > 0) {
    await Promise.allSettled(
      Array.from(images).map(
        (img) =>
          new Promise<void>((resolveImg) => {
            if (img.complete) resolveImg();
            else {
              img.onload = () => resolveImg();
              img.onerror = () => resolveImg();
              setTimeout(resolveImg, 5000);
            }
          }),
      ),
    );
  }

  win.focus();
}

// ─── Toast 提示 ───

function showToast(text: string, bg = '#22c55e', duration = 2000) {
  const toast = document.createElement('div');
  toast.textContent = text;
  toast.style.cssText = `position:fixed;top:20px;left:50%;transform:translateX(-50%);background:${bg};color:white;padding:8px 20px;border-radius:8px;font-size:14px;z-index:9999;pointer-events:none;`;
  document.body.appendChild(toast);
  setTimeout(() => toast.remove(), duration);
}

// ─── JSON 导出/导入（不变） ───

export function exportJSON(data: ResumeData, filename = 'resume') {
  const safeName = filename.replace(/[^a-zA-Z0-9\u4e00-\u9fa5_-]/g, '_');
  const json = JSON.stringify(data, null, 2);
  const blob = new Blob([json], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.download = `${safeName}.json`;
  link.href = url;
  link.click();
  URL.revokeObjectURL(url);
}

export function parseResumeJSON(file: File): Promise<ResumeData> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const data = JSON.parse(reader.result as string);
      if (!data || typeof data !== 'object') throw new Error('无效的 JSON 格式');
      resolve(data as ResumeData);
    };
    reader.onerror = () => reject(new Error('文件读取失败'));
    reader.readAsText(file);
  });
}
