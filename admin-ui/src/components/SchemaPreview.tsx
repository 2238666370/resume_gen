import { useMemo } from 'react';
import { Alert, Empty, Tag, Typography } from 'antd';

/**
 * 模板 Schema 结构预览（R8-A4 P3）。
 *
 * 管理端审核辅助视图：按元素坐标等比绘制线框，并标出绑定路径，
 * 使管理员在通过/驳回前能看到模板结构与数据来源（不引入完整渲染器，避免两套渲染逻辑）。
 */

interface PreviewElement {
  id?: string;
  type?: string;
  name?: string;
  x?: number;
  y?: number;
  w?: number;
  h?: number;
  zIndex?: number;
  hidden?: boolean;
  value?: string;
  bind?: { path?: string };
  itemTemplate?: PreviewElement[];
  children?: PreviewElement[];
}

interface ParsedSchema {
  layout?: string;
  schemaVersion?: number;
  columns?: number;
  sections?: { id?: string; type?: string; title?: string; column?: string; show?: boolean }[];
  elements?: PreviewElement[];
}

const PAGE_W = 210;
const PAGE_H = 297;
const SCALE = 1.9; // px / mm

const TYPE_LABEL: Record<string, string> = {
  field: '字段', text: '文本', heading: '标题', list: '列表',
  image: '图片', shape: '形状', group: '组', pageBreak: '分页',
};

const labelOf = (el: PreviewElement): string => {
  const type = TYPE_LABEL[el.type ?? ''] ?? el.type ?? '?';
  const bind = el.bind?.path;
  if (bind) return `${type} · ${bind}`;
  if (el.type === 'text' || el.type === 'heading') return `${type} · ${(el.value ?? '').slice(0, 10)}`;
  if (el.type === 'list') return `${type} · ${el.itemTemplate?.length ?? 0} 项模板`;
  return type;
};

function flatten(elements: PreviewElement[], dx = 0, dy = 0, out: { el: PreviewElement; x: number; y: number; nested: boolean }[] = []) {
  for (const el of elements) {
    if (el.hidden) continue;
    const x = dx + (el.x ?? 0);
    const y = dy + (el.y ?? 0);
    out.push({ el, x, y, nested: dx !== 0 || dy !== 0 });
    if (el.type === 'group' && el.children?.length) flatten(el.children, x, y, out);
  }
  return out;
}

export function SchemaPreview({ schema }: { schema?: string | null }) {
  const parsed = useMemo<ParsedSchema | null>(() => {
    if (!schema) return null;
    try {
      const obj = JSON.parse(schema) as ParsedSchema;
      return obj && typeof obj === 'object' ? obj : null;
    } catch {
      return null;
    }
  }, [schema]);

  if (!parsed) {
    return <Empty description="该模板没有可解析的 Schema（或为官方内置模板）" />;
  }

  const isCanvas = parsed.layout === 'canvas' && Array.isArray(parsed.elements);
  const boxes = isCanvas ? flatten(parsed.elements ?? []) : [];
  const sections = (parsed.sections ?? []).filter((s) => s.show !== false);

  return (
    <div style={{ display: 'flex', gap: 16 }}>
      <div style={{ flexShrink: 0 }}>
        {isCanvas ? (
          <div
            style={{
              position: 'relative',
              width: PAGE_W * SCALE,
              height: PAGE_H * SCALE,
              background: '#fff',
              border: '1px solid #e5e7eb',
              overflow: 'hidden',
            }}
          >
            {boxes.map((b, i) => {
              const el = b.el;
              const list = el.type === 'list';
              return (
                <div
                  key={el.id ?? i}
                  style={{
                    position: 'absolute',
                    left: b.x * SCALE,
                    top: b.y * SCALE,
                    width: Math.max((el.w ?? 10) * SCALE, 12),
                    height: Math.max((el.h ?? 5) * SCALE, 14),
                    border: `1px ${b.nested ? 'dashed' : 'solid'} ${list ? '#2563eb' : '#cbd5e1'}`,
                    background: list ? 'rgba(37,99,235,0.06)' : 'rgba(148,163,184,0.06)',
                    fontSize: 10,
                    lineHeight: '14px',
                    color: '#475569',
                    padding: '0 2px',
                    overflow: 'hidden',
                    whiteSpace: 'nowrap',
                    boxSizing: 'border-box',
                  }}
                  title={labelOf(el)}
                >
                  {labelOf(el)}
                </div>
              );
            })}
          </div>
        ) : (
          <Alert
            style={{ width: 320 }}
            type="info"
            showIcon
            message="结构化模板（v1）"
            description={`共 ${sections.length} 个板块，${parsed.columns === 2 ? '双栏' : '单栏'}。列表在右侧。`}
          />
        )}
      </div>

      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ marginBottom: 8 }}>
          <Tag color={isCanvas ? 'blue' : 'default'}>
            {isCanvas ? `自由画布 v${parsed.schemaVersion ?? 2}` : `结构化 v${parsed.schemaVersion ?? 1}`}
          </Tag>
          {isCanvas && <Typography.Text type="secondary">元素 {boxes.length} 个（蓝框为列表块）</Typography.Text>}
        </div>

        {!isCanvas && sections.length > 0 && (
          <div style={{ marginBottom: 8 }}>
            {sections.map((s, i) => (
              <Tag key={s.id ?? i} style={{ marginBottom: 4 }}>
                {s.title || s.type} · {s.type}
                {s.column ? ` · ${s.column === 'right' ? '右栏' : '左栏'}` : ''}
              </Tag>
            ))}
          </div>
        )}

        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          原始 Schema
        </Typography.Text>
        <pre
          style={{
            maxHeight: 360,
            overflow: 'auto',
            background: '#fafafa',
            border: '1px solid #f0f0f0',
            borderRadius: 6,
            padding: 8,
            fontSize: 12,
            lineHeight: '18px',
            marginTop: 4,
          }}
        >
          {JSON.stringify(parsed, null, 2)}
        </pre>
      </div>
    </div>
  );
}
