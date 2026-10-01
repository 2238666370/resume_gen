import { useRef, useState, type PointerEvent as ReactPointerEvent } from 'react';
import type { ResumeData } from '../../types/resume';
import { MM_TO_PX, mmToPx } from '../../types/canvasSchema';
import { flattenElements, isEditable, useCanvasEditorStore, type FlatElement } from '../../store/canvasEditorStore';
import { computeSnap, type Rect, type SnapGuide } from '../../utils/snap';
import { CanvasTemplateRenderer } from './CanvasTemplateRenderer';

interface Props {
  data: ResumeData;
  /** design：设计态（可编辑）；runtime：预览真实数据下的推挤效果。 */
  mode?: 'design' | 'runtime';
}

const HANDLES = ['nw', 'n', 'ne', 'e', 'se', 's', 'sw', 'w'] as const;
type Handle = (typeof HANDLES)[number];

const HANDLE_POS: Record<Handle, { left: string; top: string; cursor: string }> = {
  nw: { left: '0%', top: '0%', cursor: 'nwse-resize' },
  n: { left: '50%', top: '0%', cursor: 'ns-resize' },
  ne: { left: '100%', top: '0%', cursor: 'nesw-resize' },
  e: { left: '100%', top: '50%', cursor: 'ew-resize' },
  se: { left: '100%', top: '100%', cursor: 'nwse-resize' },
  s: { left: '50%', top: '100%', cursor: 'ns-resize' },
  sw: { left: '0%', top: '100%', cursor: 'nesw-resize' },
  w: { left: '0%', top: '50%', cursor: 'ew-resize' },
};

const union = (rects: Rect[]): Rect => {
  const x = Math.min(...rects.map((r) => r.x));
  const y = Math.min(...rects.map((r) => r.y));
  const right = Math.max(...rects.map((r) => r.x + r.w));
  const bottom = Math.max(...rects.map((r) => r.y + r.h));
  return { x, y, w: right - x, h: bottom - y };
};

const intersects = (a: Rect, b: Rect): boolean =>
  a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h;

/**
 * 画布编辑区（R8-A4 P2）：渲染器之上叠一层交互层。
 * 支持拖拽（含吸附与参考线）、8 向缩放、框选多选；拖拽只移动 overlay，松手才提交。
 */
export function CanvasStage({ data, mode = 'design' }: Props) {
  const schema = useCanvasEditorStore((s) => s.schema);
  const selectedIds = useCanvasEditorStore((s) => s.selectedIds);
  const zoom = useCanvasEditorStore((s) => s.zoom);

  const [drag, setDrag] = useState<{ dx: number; dy: number } | null>(null);
  const [guides, setGuides] = useState<SnapGuide[]>([]);
  const [marquee, setMarquee] = useState<Rect | null>(null);

  const pageRef = useRef<HTMLDivElement>(null);
  const dragStart = useRef<{ px: number; py: number; boxes: Rect[] } | null>(null);
  const resizeStart = useRef<{ px: number; py: number; box: Rect; handle: Handle } | null>(null);
  const marqueeStart = useRef<{ x: number; y: number } | null>(null);

  const flat: FlatElement[] = flattenElements(schema.elements);
  const margin = schema.page.margin;
  /** 内容区尺寸（页面扣除页边距）——元素坐标即以内容区左上角为原点。 */
  const content = {
    width: schema.page.width - margin.left - margin.right,
    height: schema.page.height - margin.top - margin.bottom,
  };
  const toMm = (px: number) => px / (zoom * MM_TO_PX);
  /** 屏幕坐标 → 内容区相对坐标（mm），需扣除页边距。 */
  const localMm = (clientX: number, clientY: number) => {
    const rect = pageRef.current?.getBoundingClientRect();
    if (!rect) return { x: 0, y: 0 };
    return {
      x: (clientX - rect.left) / (zoom * MM_TO_PX) - margin.left,
      y: (clientY - rect.top) / (zoom * MM_TO_PX) - margin.top,
    };
  };

  /** 吸附目标：可见、未被选中、且不在列表条目模板内的元素。 */
  const snapTargets = (): Rect[] =>
    flat
      .filter((f) => !f.el.hidden && !f.inListTemplate && !selectedIds.includes(f.el.id))
      .map((f) => ({ x: f.absX, y: f.absY, w: f.el.w, h: f.el.h }));

  /* ── 移动（含吸附） ── */
  const startDrag = (e: ReactPointerEvent, id: string) => {
    e.stopPropagation();
    const store = useCanvasEditorStore.getState();
    const target = flat.find((f) => f.el.id === id);
    if (!target) return;
    const additive = e.shiftKey;
    if (!isEditable(target.el)) {
      store.select([id]);
      return;
    }
    if (additive) store.toggleSelect(id);
    else if (!store.selectedIds.includes(id)) store.select([id]);

    const selected = flattenElements(store.schema.elements).filter(
      (f) => store.selectedIds.includes(f.el.id) || f.el.id === id,
    );
    store.snapshot();
    dragStart.current = {
      px: e.clientX,
      py: e.clientY,
      boxes: selected.map((f) => ({ x: f.absX, y: f.absY, w: f.el.w, h: f.el.h })),
    };
    setDrag({ dx: 0, dy: 0 });
    setGuides([]);
    (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
  };

  const onDragMove = (e: ReactPointerEvent) => {
    const start = dragStart.current;
    if (!start) return;
    const rawDx = toMm(e.clientX - start.px);
    const rawDy = toMm(e.clientY - start.py);
    const moved = start.boxes.map((b) => ({ ...b, x: b.x + rawDx, y: b.y + rawDy }));
    const snap = computeSnap({
      moving: union(moved),
      others: snapTargets(),
      bounds: content,
    });
    setDrag({ dx: rawDx + snap.dx, dy: rawDy + snap.dy });
    setGuides(snap.guides);
  };

  const endDrag = () => {
    const d = drag;
    dragStart.current = null;
    setDrag(null);
    setGuides([]);
    if (d && (Math.abs(d.dx) > 0.05 || Math.abs(d.dy) > 0.05)) {
      useCanvasEditorStore.getState().nudgeSelected(d.dx, d.dy);
    }
  };

  /* ── 缩放 ── */
  const startResize = (e: ReactPointerEvent, handle: Handle) => {
    e.stopPropagation();
    const store = useCanvasEditorStore.getState();
    const id = store.selectedIds[0];
    const target = flat.find((f) => f.el.id === id);
    if (!target || !isEditable(target.el)) return;
    store.snapshot();
    resizeStart.current = {
      px: e.clientX,
      py: e.clientY,
      handle,
      box: { x: target.absX, y: target.absY, w: target.el.w, h: target.el.h },
    };
    (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
  };

  const onResizeMove = (e: ReactPointerEvent) => {
    const start = resizeStart.current;
    if (!start) return;
    const dx = toMm(e.clientX - start.px);
    const dy = toMm(e.clientY - start.py);
    const { x, y, w, h } = start.box;
    let nx = x;
    let ny = y;
    let nw = w;
    let nh = h;
    if (start.handle.includes('e')) nw = w + dx;
    if (start.handle.includes('s')) nh = h + dy;
    if (start.handle.includes('w')) {
      nx = x + dx;
      nw = w - dx;
    }
    if (start.handle.includes('n')) {
      ny = y + dy;
      nh = h - dy;
    }
    useCanvasEditorStore.getState().setBox(useCanvasEditorStore.getState().selectedIds[0], {
      x: nx, y: ny, w: Math.max(2, nw), h: Math.max(2, nh),
    });
  };

  const endResize = () => {
    resizeStart.current = null;
  };

  /* ── 框选 ── */
  const startMarquee = (e: ReactPointerEvent) => {
    if (mode === 'runtime') return;
    const p = localMm(e.clientX, e.clientY);
    marqueeStart.current = p;
    setMarquee({ x: p.x, y: p.y, w: 0, h: 0 });
    useCanvasEditorStore.getState().select([]);
    (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
  };

  const onMarqueeMove = (e: ReactPointerEvent) => {
    const start = marqueeStart.current;
    if (!start) return;
    const p = localMm(e.clientX, e.clientY);
    setMarquee({
      x: Math.min(start.x, p.x),
      y: Math.min(start.y, p.y),
      w: Math.abs(p.x - start.x),
      h: Math.abs(p.y - start.y),
    });
  };

  const endMarquee = () => {
    if (!marqueeStart.current) return;
    marqueeStart.current = null;
    const box = marquee;
    setMarquee(null);
    if (!box || box.w < 1 || box.h < 1) return;
    const hit = flat
      .filter((f) => !f.el.hidden && !f.inListTemplate)
      .filter((f) => intersects(box, { x: f.absX, y: f.absY, w: f.el.w, h: f.el.h }))
      .map((f) => f.el.id);
    useCanvasEditorStore.getState().select(hit);
  };

  const single = selectedIds.length === 1 ? flat.find((f) => f.el.id === selectedIds[0]) : undefined;

  return (
    <div className="flex-1 overflow-auto bg-gray-100 p-6">
      <div
        className="mx-auto"
        style={{ width: mmToPx(schema.page.width) * zoom, height: mmToPx(schema.page.height) * zoom }}
      >
        <div
          style={{
            width: mmToPx(schema.page.width),
            height: mmToPx(schema.page.height),
            transform: `scale(${zoom})`,
            transformOrigin: 'top left',
          }}
        >
          <div
            ref={pageRef}
            className="relative shadow-2xl"
            onPointerDown={startMarquee}
            onPointerMove={onMarqueeMove}
            onPointerUp={endMarquee}
            onPointerCancel={endMarquee}
            style={{
              width: mmToPx(schema.page.width),
              height: mmToPx(schema.page.height),
            }}
          >
            <CanvasTemplateRenderer data={data} schema={schema} mode={mode} />

            {/*
              交互层：必须与渲染器的内容区（页面扣除页边距）严格对齐，
              否则选中框 / 参考线 / 框选会整体偏移一个页边距。
              网格也画在这一层，使 10mm 网格线与元素坐标（内容区原点）对齐。
            */}
            <div
              className={`absolute ${mode === 'runtime' ? 'pointer-events-none' : ''}`}
              style={{
                left: mmToPx(margin.left),
                top: mmToPx(margin.top),
                right: mmToPx(margin.right),
                bottom: mmToPx(margin.bottom),
                backgroundImage:
                  'linear-gradient(to right, rgba(0,0,0,0.05) 1px, transparent 1px), linear-gradient(to bottom, rgba(0,0,0,0.05) 1px, transparent 1px)',
                backgroundSize: `${mmToPx(10)}px ${mmToPx(10)}px`,
              }}
            >
              {flat.map((f) => {
                if (f.inListTemplate && f.el.hidden) return null;
                const selected = selectedIds.includes(f.el.id);
                const dx = selected && drag ? drag.dx : 0;
                const dy = selected && drag ? drag.dy : 0;
                return (
                  <div
                    key={f.el.id}
                    onPointerDown={(e) => startDrag(e, f.el.id)}
                    onPointerMove={onDragMove}
                    onPointerUp={endDrag}
                    onPointerCancel={endDrag}
                    className={`absolute cursor-move ${f.el.hidden ? 'opacity-30' : ''}`}
                    style={{
                      left: mmToPx(f.absX + dx),
                      top: mmToPx(f.absY + dy),
                      width: mmToPx(f.el.w),
                      height: Math.max(mmToPx(f.el.h), 4),
                      outline: selected
                        ? '1px solid #2563eb'
                        : f.inListTemplate
                          ? '1px dashed rgba(37,99,235,0.35)'
                          : 'none',
                    }}
                    title={f.inListTemplate ? `${f.el.name ?? f.el.type}（条目模板）` : (f.el.name ?? f.el.type)}
                  />
                );
              })}

              {guides.map((g, i) =>
                g.axis === 'x' ? (
                  <div
                    key={`gx${i}`}
                    className="absolute w-px bg-pink-500 pointer-events-none"
                    style={{ left: mmToPx(g.pos), top: mmToPx(g.start), height: mmToPx(Math.max(g.end - g.start, 0.5)) }}
                  />
                ) : (
                  <div
                    key={`gy${i}`}
                    className="absolute h-px bg-pink-500 pointer-events-none"
                    style={{ top: mmToPx(g.pos), left: mmToPx(g.start), width: mmToPx(Math.max(g.end - g.start, 0.5)) }}
                  />
                ),
              )}

              {marquee && (
                <div
                  className="absolute border border-blue-500 bg-blue-500/10 pointer-events-none"
                  style={{
                    left: mmToPx(marquee.x),
                    top: mmToPx(marquee.y),
                    width: mmToPx(marquee.w),
                    height: mmToPx(marquee.h),
                  }}
                />
              )}

              {single && isEditable(single.el) && (
                <div
                  className="absolute"
                  style={{
                    left: mmToPx(single.absX),
                    top: mmToPx(single.absY),
                    width: mmToPx(single.el.w),
                    height: Math.max(mmToPx(single.el.h), 4),
                  }}
                >
                  {HANDLES.map((h) => (
                    <div
                      key={h}
                      onPointerDown={(e) => startResize(e, h)}
                      onPointerMove={onResizeMove}
                      onPointerUp={endResize}
                      onPointerCancel={endResize}
                      className="absolute w-2 h-2 bg-white border border-blue-600 rounded-full"
                      style={{
                        left: HANDLE_POS[h].left,
                        top: HANDLE_POS[h].top,
                        transform: 'translate(-50%, -50%)',
                        cursor: HANDLE_POS[h].cursor,
                      }}
                    />
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
