import html2canvas from 'html2canvas';
import type { ResumeData } from '../types/resume';

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

/**
 * 导出 PDF —— 使用浏览器原生打印功能
 *
 * 流程：简历 DOM 克隆到全屏 iframe → 顶部显示「打印」和「返回」按钮 →
 * 用户点击「打印」弹出浏览器打印对话框 → 另存为 PDF。
 * 浏览器原生渲染引擎完美处理中文字体，生成的 PDF 支持文字搜索、复制。
 */
export async function exportToPDF(element: HTMLElement, filename = 'resume') {
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

  // 3. 复制父文档中的所有样式（Vite 开发模式注入 <style>，生产模式用 <link>）
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

    /* ---- 简历内容区域（缩放以完整显示，不滚动） ---- */
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
      /* 简历容器：去掉阴影/圆角/缩放，宽高自适应纸张 */
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
    classic: '10mm 12mm',  // 保持现状
    modern:  '0',           // 贴边无空隙
    minimal: '6mm 8mm',    // 减小空隙
  };

  // 各模板的根容器 padding 处理策略
  // Classic/Minimal 根元素有 padding（p-10 / p-12），打印时剥离由 @page 控制边距
  // Modern 根元素无 padding，内部 p-8 属于设计排版，打印时保留
  const rootPadStripping = (template === 'modern')
    ? ''  // 现代模板保留内部排版 padding
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

  // 5.5 计算缩放比例，让整份简历在预览区域完整显示（不滚动）
  const scaleResume = () => {
    const cw = contentArea.clientWidth;
    const ch = contentArea.clientHeight;
    if (cw <= 0 || ch <= 0) return;
    // 简历原始尺寸：210mm × 297mm（不含本身的 padding）
    const resumeW = clone.offsetWidth || 210 * 3.78;  // mm→px 近似
    const resumeH = clone.offsetHeight || 297 * 3.78;
    const padX = 32; // 保留水平边距
    const padY = 32; // 保留垂直边距
    const sx = (cw - padX) / resumeW;
    const sy = (ch - padY) / resumeH;
    const s = Math.min(sx, sy, 1); // 放大不超过 1
    (clone as HTMLElement).style.transform = `scale(${s})`;
  };
  // 先粗略缩放一次
  scaleResume();
  // 绑定 resize，窗口变化时重新计算
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

  // 打印完成后也清理
  win.addEventListener('afterprint', cleanup, { once: true });
  // 超时兜底清理（3分钟）
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

  // 8. 聚焦 iframe，让用户看到预览
  win.focus();
}

/** 导出简历数据为 JSON 文件并触发下载 */
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

/** 从 File 读取 JSON 并返回 ResumeData，失败时抛出错误 */
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
