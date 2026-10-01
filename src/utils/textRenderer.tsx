import type { ReactNode } from 'react';

/**
 * 将含 **bold** 语法的字符串解析为 React 节点
 * 支持多行（\n 转为 <br />）
 *
 * 使用示例：
 *   renderRichText("负责 **前端架构** 设计\n提升性能 **30%**")
 */
export function renderRichText(text: string): ReactNode {
  if (!text) return null;
  const lines = text.split('\n');
  return lines.map((line, lineIdx) => {
    const parts = line.split(/\*\*(.*?)\*\*/g);
    const nodes = parts.map((part, i) =>
      i % 2 === 1
        ? <strong key={i} style={{ fontWeight: 700 }}>{part}</strong>
        : part
    );
    return (
      <span key={lineIdx}>
        {nodes}
        {lineIdx < lines.length - 1 && <br />}
      </span>
    );
  });
}

/**
 * 单行版本（无换行处理）
 */
export function renderInlineText(text: string): ReactNode {
  if (!text) return null;
  const parts = text.split(/\*\*(.*?)\*\*/g);
  return parts.map((part, i) =>
    i % 2 === 1
      ? <strong key={i} style={{ fontWeight: 700 }}>{part}</strong>
      : part
  );
}
