import html2canvas from 'html2canvas';
import type { ResumeData } from '../types/resume';
import { getWatermarkConfig } from '../api/watermark';
import type { WatermarkConfig } from '../api/watermark';

// ─── 导出 PNG ───

export async function exportToPNG(element: HTMLElement, filename = 'resume') {
  const canvas = await html2canvas(element, {
    scale: 2,
    useCORS: true,
    backgroundColor: '#ffffff',
    logging: false,
  });
  try {
    const wm = await getWatermarkConfig();
    applyWatermarks(canvas, wm);
  } catch {
    // 获取水印配置失败（如未登录）时静默降级，不影响导出
  }
  const link = document.createElement('a');
  link.download = `${filename}.png`;
  link.href = canvas.toDataURL('image/png');
  link.click();
}

// ─── 水印（R8-E） ───

/** 叠加可视水印与盲水印（先画可视，再编码盲水印，避免文本覆盖破坏 LSB）。 */
function applyWatermarks(canvas: HTMLCanvasElement, wm: WatermarkConfig | undefined): void {
  if (!wm || !wm.enabled) return;
  if (wm.visibleText) {
    drawVisibleWatermark(canvas, wm.visibleText, wm.visibleOpacity, wm.visibleDensity);
  }
  if (wm.blindEnabled && wm.blindPayload) {
    encodeLsbWatermark(canvas, wm.blindPayload);
  }
}

/** 可视水印：半透明重复文本，威慑截屏转发。 */
function drawVisibleWatermark(
  canvas: HTMLCanvasElement,
  text: string,
  opacity: number,
  density: string,
): void {
  const ctx = canvas.getContext('2d');
  if (!ctx) return;
  const w = canvas.width;
  const h = canvas.height;
  const step = density === 'heavy' ? 80 : density === 'light' ? 240 : 160;
  ctx.save();
  ctx.globalAlpha = Math.min(Math.max(opacity, 0.01), 1);
  ctx.fillStyle = '#000';
  ctx.font = '16px sans-serif';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  for (let y = 0; y < h + 200; y += step) {
    for (let x = -h; x < w + 200; x += step) {
      ctx.save();
      ctx.translate(x, y);
      ctx.rotate(-Math.PI / 6);
      ctx.fillText(text, 0, 0);
      ctx.restore();
    }
  }
  ctx.restore();
}

/**
 * 盲水印：将负载按「MAGIC(RWMW) + 2 字节长度 + payload」写入像素红通道 LSB，
 * 与后端 WatermarkService.decodeLsb 协议一致，肉眼不可见，可溯源。
 */
function encodeLsbWatermark(canvas: HTMLCanvasElement, payload: string): void {
  const ctx = canvas.getContext('2d');
  if (!ctx) return;
  const w = canvas.width;
  const h = canvas.height;
  const payloadBytes = new TextEncoder().encode(payload);
  if (payloadBytes.length > 512) return;
  const full = new Uint8Array(6 + payloadBytes.length);
  full[0] = 0x52; // R
  full[1] = 0x57; // W
  full[2] = 0x4d; // M
  full[3] = 0x57; // W
  full[4] = (payloadBytes.length >> 8) & 0xff;
  full[5] = payloadBytes.length & 0xff;
  full.set(payloadBytes, 6);

  const totalBits = full.length * 8;
  if (w * h < totalBits) return; // 像素不足以承载水印

  const imageData = ctx.getImageData(0, 0, w, h);
  const data = imageData.data;
  for (let i = 0; i < totalBits; i++) {
    const bit = (full[Math.floor(i / 8)] >> (7 - (i % 8))) & 1;
    const x = i % w;
    const y = Math.floor(i / w);
    const idx = (y * w + x) * 4; // R 通道
    data[idx] = (data[idx] & 0xfe) | bit;
  }
  ctx.putImageData(imageData, 0, 0);
}

// ─── 导出 PDF ───

/** 导出 PDF：iframe 预览 + 浏览器原生打印（在打印对话框中选择「另存为 PDF」）。 */
export async function exportToPDF(
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

// ─── JSON 导出/导入 ───

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
