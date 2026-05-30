import html2canvas from 'html2canvas';
import { jsPDF } from 'jspdf';
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

export async function exportToPDF(element: HTMLElement, filename = 'resume') {
  const canvas = await html2canvas(element, {
    scale: 2,
    useCORS: true,
    backgroundColor: '#ffffff',
    logging: false,
  });

  const imgData = canvas.toDataURL('image/png');
  const pdf = new jsPDF({
    orientation: 'portrait',
    unit: 'mm',
    format: 'a4',
  });

  // jsPDF v4: pageSize.getWidth() / getHeight() 是方法，不是属性
  const pdfWidth = pdf.internal.pageSize.getWidth();
  const pdfHeight = pdf.internal.pageSize.getHeight();
  const imgWidth = canvas.width;
  const imgHeight = canvas.height;
  const ratio = imgWidth / imgHeight;
  const imgPdfHeight = pdfWidth / ratio;

  if (imgPdfHeight <= pdfHeight) {
    pdf.addImage(imgData, 'PNG', 0, 0, pdfWidth, imgPdfHeight);
  } else {
    // Multi-page support
    let yOffset = 0;
    const pageHeightPx = (pdfHeight * imgWidth) / pdfWidth;

    while (yOffset < imgHeight) {
      const pageCanvas = document.createElement('canvas');
      pageCanvas.width = imgWidth;
      pageCanvas.height = Math.min(pageHeightPx, imgHeight - yOffset);
      const ctx = pageCanvas.getContext('2d')!;
      ctx.drawImage(canvas, 0, -yOffset);
      const pageData = pageCanvas.toDataURL('image/png');
      if (yOffset > 0) pdf.addPage();
      pdf.addImage(pageData, 'PNG', 0, 0, pdfWidth, (pageCanvas.height * pdfWidth) / imgWidth);
      yOffset += pageHeightPx;
    }
  }

  pdf.save(`${filename}.pdf`);
}

/** 导出简历数据为 JSON 文件并触发下载 */
export function exportJSON(data: ResumeData, filename = 'resume') {
  // 构建简洁文件名（取个人姓名或默认）
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
      try {
        const data = JSON.parse(reader.result as string);
        // 基本校验
        if (!data || typeof data !== 'object') throw new Error('无效的 JSON 格式');
        resolve(data as ResumeData);
      } catch (e) {
        reject(new Error('文件解析失败，请确认是有效的简历 JSON 文件'));
      }
    };
    reader.onerror = () => reject(new Error('文件读取失败'));
    reader.readAsText(file);
  });
}
