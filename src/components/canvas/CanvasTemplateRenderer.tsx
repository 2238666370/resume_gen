import { useMemo } from 'react';
import type { CSSProperties, ReactNode } from 'react';
import { renderRichText } from '../../utils/textRenderer';
import type { ResumeData } from '../../types/resume';
import type { CanvasElement, CanvasSchema, CanvasStyle } from '../../types/canvasSchema';
import { bindToText, mmToPx, ptToPx, resolvePath } from '../../types/canvasSchema';
import {
  buildLayoutNodes, collectPageBreaks, itemTemplateHeight, paginateLayout, type PaginatedPage,
} from '../../utils/layoutEngine';
import { filterVisibleItems } from '../../utils/sectionVisibility';

interface Props {
  data: ResumeData;
  schema: CanvasSchema;
  /** design：按设计坐标渲染（编辑器用）；runtime：应用锚点流式推挤与分页（预览 / 导出用）。 */
  mode?: 'design' | 'runtime';
  className?: string;
}

/** 多页之间的可视化间距（mm），仅渲染层用，不影响纸张尺寸。 */
const PAGE_GAP_MM = 6;

/** 元素样式 → 内联 CSS（坐标 mm、字号 pt）。 */
function toCss(style: CanvasStyle | undefined): CSSProperties {
  const css: CSSProperties = {};
  if (!style) return css;
  if (style.fontFamily) css.fontFamily = style.fontFamily;
  if (style.fontSize) css.fontSize = ptToPx(style.fontSize);
  if (style.fontWeight) css.fontWeight = style.fontWeight;
  if (style.italic) css.fontStyle = 'italic';
  if (style.color) css.color = style.color;
  if (style.lineHeight) css.lineHeight = style.lineHeight;
  if (style.letterSpacing != null) css.letterSpacing = style.letterSpacing;
  if (style.textAlign) css.textAlign = style.textAlign;
  if (style.padding) css.padding = mmToPx(style.padding);
  if (style.background) css.background = style.background;
  if (style.opacity != null) css.opacity = style.opacity;
  if (style.border) {
    css.border = `${style.border.width}px solid ${style.border.color}`;
    css.borderRadius = mmToPx(style.border.radius);
  }
  return css;
}

/** 垂直对齐 → 纵向 flex 的 justify-content。 */
const vAlignToJustify = (v: CanvasStyle['verticalAlign']): CSSProperties['justifyContent'] =>
  v === 'middle' ? 'center' : v === 'bottom' ? 'flex-end' : 'flex-start';

/** 文本内容（支持 `**加粗**`）。 */
function TextContent({ value, style }: { value: string; style?: CanvasStyle }): ReactNode {
  return (
    <div
      style={{
        width: '100%',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        alignItems: style?.textAlign === 'center' ? 'center' : style?.textAlign === 'right' ? 'flex-end' : 'flex-start',
        justifyContent: vAlignToJustify(style?.verticalAlign),
        whiteSpace: 'pre-wrap',
        wordBreak: 'break-word',
        overflow: 'hidden',
      }}
    >
      <div style={{ width: '100%' }}>{renderRichText(value)}</div>
    </div>
  );
}

/** 按元素类型渲染内容（坐标 / 尺寸均为 mm）。 */
function renderElement(el: CanvasElement, ctx: unknown): ReactNode {
  const box: CSSProperties = {
    position: 'absolute',
    left: mmToPx(el.x),
    top: mmToPx(el.y),
    width: mmToPx(el.w),
    height: mmToPx(el.h),
    zIndex: el.zIndex,
    ...toCss(el.style),
  };

  switch (el.type) {
    case 'text':
      return (
        <div key={el.id} style={box}>
          <TextContent value={el.value} style={el.style} />
        </div>
      );

    case 'field': {
      const text = bindToText(resolvePath(ctx, el.bind.path), el.bind.fallback ?? '');
      return (
        <div key={el.id} style={box}>
          <TextContent value={`${el.prefix ?? ''}${text}${el.suffix ?? ''}`} style={el.style} />
        </div>
      );
    }

    case 'heading': {
      const color = el.style?.color ?? '#111827';
      const decoration = el.decoration ?? 'bar';
      const inner: ReactNode =
        decoration === 'capsule' ? (
          <span style={{ display: 'inline-block', padding: '1px 8px', borderRadius: 999, background: color, color: '#fff' }}>
            {el.value}
          </span>
        ) : (
          <span style={{ borderBottom: decoration === 'underline' ? `1px solid ${color}` : undefined, paddingBottom: decoration === 'underline' ? 2 : undefined }}>
            {el.value}
          </span>
        );
      return (
        <div key={el.id} style={{ ...box, display: 'flex', alignItems: 'center', gap: 6 }}>
          {decoration === 'bar' && <span style={{ width: 3, height: mmToPx(el.h) * 0.7, background: color, borderRadius: 2, flexShrink: 0 }} />}
          <span style={{ color, flex: 1, minWidth: 0 }}>{inner}</span>
        </div>
      );
    }

    case 'list': {
      const raw = resolvePath(ctx, el.bind.path);
      // 与布局引擎一致：隐藏的板块不渲染（其高度也已按 0 计算）
      const items = Array.isArray(raw) ? filterVisibleItems(ctx, el.bind.path, raw) : [];
      const itemH = itemTemplateHeight(el);
      const gap = el.itemGap ?? 0;
      return (
        <div key={el.id} style={box}>
          {items.map((item, i) => (
            <div
              key={i}
              style={{
                position: 'absolute',
                left: 0,
                top: mmToPx(i * (itemH + gap)),
                width: '100%',
                height: mmToPx(itemH),
              }}
            >
              {el.itemTemplate.map((child) => renderElement(child, item))}
              {el.divider && i < items.length - 1 && (
                <div style={{ position: 'absolute', left: 0, right: 0, bottom: 0, borderTop: '1px solid #e5e7eb' }} />
              )}
            </div>
          ))}
        </div>
      );
    }

    case 'image': {
      const src = el.source === 'avatar' ? bindToText(resolvePath(ctx, 'personal.avatar')) : (el.url ?? '');
      if (!src) return <div key={el.id} style={box} />;
      return (
        <div key={el.id} style={box}>
          <img
            src={src}
            alt=""
            style={{ width: '100%', height: '100%', objectFit: el.fit ?? 'cover', borderRadius: el.radius ? mmToPx(el.radius) : undefined }}
          />
        </div>
      );
    }

    case 'shape': {
      const shapeStyle: CSSProperties = { width: '100%', height: '100%', ...toCss(el.style) };
      if (el.shape === 'ellipse') shapeStyle.borderRadius = '50%';
      if (el.shape === 'rect') shapeStyle.borderRadius = el.style?.border?.radius ? mmToPx(el.style.border.radius) : 0;
      if (el.fill) shapeStyle.background = el.fill;
      if (el.stroke) shapeStyle.border = `1px solid ${el.stroke}`;
      if (el.shape === 'line') {
        shapeStyle.background = el.stroke ?? el.fill ?? '#9ca3af';
        shapeStyle.height = 1;
        shapeStyle.border = 'none';
      }
      return <div key={el.id} style={{ ...box, display: 'flex' }}><div style={shapeStyle} /></div>;
    }

    case 'group':
      return (
        <div key={el.id} style={box}>
          {el.children.map((child) => renderElement(child, ctx))}
        </div>
      );

    default:
      // pageBreak 不参与内容渲染：运行态由分页切分体现，设计态渲染为分页标记线
      return null;
  }
}

/**
 * 画布模板渲染器（R8-A4）：编辑器画布、简历预览、市场缩略图、导出共用。
 * 只读渲染，不含任何交互。
 *
 * 运行态若存在分页符则渲染为多页（每页独立推挤）；无分页符时结构与此前完全一致。
 */
export function CanvasTemplateRenderer({ data, schema, mode = 'runtime', className }: Props) {
  const { page, theme } = schema;
  const contentHeight = page.height - page.margin.top - page.margin.bottom;
  const breaks = useMemo(() => collectPageBreaks(schema.elements), [schema.elements]);
  const byId = useMemo(() => new Map(schema.elements.map((el) => [el.id, el])), [schema.elements]);

  const pages = useMemo<PaginatedPage[]>(() => {
    const nodes = buildLayoutNodes(schema.elements, data);
    if (mode === 'design') {
      // 设计态：保持作者摆放的坐标与设计高度，不推挤、不分页
      const placed = nodes.map((n) => ({ id: n.id, x: n.x, y: n.y, w: n.w, h: n.h, shift: 0 }));
      return [{ index: 0, top: 0, nodes: placed, overflowIds: [] }];
    }
    return paginateLayout(nodes, breaks, contentHeight);
  }, [schema.elements, data, mode, breaks, contentHeight]);

  const pageStyle: CSSProperties = {
    position: 'relative',
    width: mmToPx(page.width),
    height: mmToPx(page.height),
    padding: `${mmToPx(page.margin.top)}px ${mmToPx(page.margin.right)}px ${mmToPx(page.margin.bottom)}px ${mmToPx(page.margin.left)}px`,
    background: '#ffffff',
    overflow: 'hidden',
    boxSizing: 'border-box',
    fontFamily: theme?.fontFamily,
    fontSize: ptToPx(theme?.baseFontSize ?? 10.5),
    color: theme?.palette?.text ?? '#111827',
  };

  const breakElements = schema.elements.filter((el) => el.type === 'pageBreak' && !el.hidden);

  const renderPageContent = (p: PaginatedPage, withMarkers: boolean): ReactNode => (
    <>
      {p.nodes.map((n) => {
        const el = byId.get(n.id);
        if (!el) return null;
        // 用「已布局位置与运行时高度」覆盖设计值（x 不变）
        return renderElement({ ...el, y: n.y, h: n.h } as CanvasElement, data);
      })}

      {/* 设计态：分页符渲染为标记线，提示「下一页从这里开始」 */}
      {withMarkers &&
        breakElements.map((b, i) => (
          <div
            key={b.id}
            style={{ position: 'absolute', left: 0, right: 0, top: mmToPx(b.y), borderTop: '1px dashed #94a3b8' }}
          >
            <span
              style={{
                position: 'absolute',
                right: 0,
                top: -14,
                fontSize: 9,
                lineHeight: '12px',
                color: '#64748b',
                background: '#ffffff',
                padding: '0 4px',
              }}
            >
              分页 · 第 {i + 2} 页从这里开始
            </span>
          </div>
        ))}
    </>
  );

  if (pages.length === 1) {
    return (
      <div className={className} data-template="canvas" style={pageStyle}>
        <div style={{ position: 'relative', width: '100%', height: '100%' }}>
          {renderPageContent(pages[0], mode === 'design')}
        </div>
      </div>
    );
  }

  return (
    <div
      className={className}
      data-template="canvas"
      data-pages={pages.length}
      style={{ display: 'flex', flexDirection: 'column', gap: mmToPx(PAGE_GAP_MM) }}
    >
      {pages.map((p) => (
        <div key={p.index} data-page={p.index + 1} style={pageStyle}>
          <div style={{ position: 'relative', width: '100%', height: '100%' }}>
            {renderPageContent(p, false)}
          </div>
        </div>
      ))}
    </div>
  );
}
