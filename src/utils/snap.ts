/**
 * 吸附对齐（R8-A4 P2）。
 *
 * 拖拽时把「移动元素的三条参考线（左/中/右、上/中/下）」与
 * 「其他元素的九条参考线 + 页面边界与中心线」比对，取阈值内最近的一条吸附，
 * 并返回需要显示的参考线（含跨度，供画布绘制）。
 */

export interface Rect {
  x: number;
  y: number;
  w: number;
  h: number;
}

export interface SnapGuide {
  axis: 'x' | 'y';
  /** 参考线位置（mm）。 */
  pos: number;
  /** 参考线跨度起止（mm），用于绘制。 */
  start: number;
  end: number;
}

export interface SnapInput {
  moving: Rect;
  others: Rect[];
  bounds: { width: number; height: number };
  /** 吸附阈值（mm）。 */
  threshold?: number;
}

export interface SnapResult {
  dx: number;
  dy: number;
  guides: SnapGuide[];
}

const DEFAULT_THRESHOLD = 1.5;

interface Candidate {
  value: number;
  rects: Rect[];
}

function collect(values: { value: number; rect: Rect }[]): Candidate[] {
  const map = new Map<number, Rect[]>();
  for (const { value, rect } of values) {
    const list = map.get(value);
    if (list) list.push(rect);
    else map.set(value, [rect]);
  }
  return [...map.entries()].map(([value, rects]) => ({ value, rects }));
}

const xLines = (rect: Rect): number[] => [rect.x, rect.x + rect.w / 2, rect.x + rect.w];
const yLines = (rect: Rect): number[] => [rect.y, rect.y + rect.h / 2, rect.y + rect.h];

function bestMatch(
  movingLines: number[],
  candidates: Candidate[],
  threshold: number,
): { delta: number; target: number; rects: Rect[] } | null {
  let best: { delta: number; target: number; rects: Rect[] } | null = null;
  for (const movingLine of movingLines) {
    for (const candidate of candidates) {
      const delta = candidate.value - movingLine;
      if (Math.abs(delta) > threshold) continue;
      if (!best || Math.abs(delta) < Math.abs(best.delta)) {
        best = { delta, target: candidate.value, rects: candidate.rects };
      }
    }
  }
  return best;
}

export function computeSnap({
  moving,
  others,
  bounds,
  threshold = DEFAULT_THRESHOLD,
}: SnapInput): SnapResult {
  // 页面本身作为候选矩形，天然提供「页边距 / 页面中心」吸附
  const pageRect: Rect = { x: 0, y: 0, w: bounds.width, h: bounds.height };
  const all = [...others, pageRect];

  const xCandidates = collect(all.flatMap((r) => xLines(r).map((value) => ({ value, rect: r }))));
  const yCandidates = collect(all.flatMap((r) => yLines(r).map((value) => ({ value, rect: r }))));

  const snapX = bestMatch(xLines(moving), xCandidates, threshold);
  const snapY = bestMatch(yLines(moving), yCandidates, threshold);
  const dx = snapX?.delta ?? 0;
  const dy = snapY?.delta ?? 0;

  const guides: SnapGuide[] = [];
  const movedBottom = moving.y + moving.h + dy;
  const movedRight = moving.x + moving.w + dx;

  if (snapX) {
    const tops = [...snapX.rects.map((r) => r.y), moving.y + dy];
    const bottoms = [...snapX.rects.map((r) => r.y + r.h), movedBottom];
    guides.push({ axis: 'x', pos: snapX.target, start: Math.min(...tops), end: Math.max(...bottoms) });
  }
  if (snapY) {
    const lefts = [...snapY.rects.map((r) => r.x), moving.x + dx];
    const rights = [...snapY.rects.map((r) => r.x + r.w), movedRight];
    guides.push({ axis: 'y', pos: snapY.target, start: Math.min(...lefts), end: Math.max(...rights) });
  }

  return { dx, dy, guides };
}
