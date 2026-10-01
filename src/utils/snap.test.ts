import { describe, it, expect } from 'vitest';
import { computeSnap, type Rect } from './snap';

const BOUNDS = { width: 210, height: 297 };

const rect = (x: number, y: number, w: number, h: number): Rect => ({ x, y, w, h });

describe('computeSnap', () => {
  it('阈值内吸附到其他元素左边缘，并给出竖向参考线', () => {
    // 其他元素宽 30（中心 35），与移动元素的中心线 40.8 不构成同距竞争，吸附结果唯一
    const r = computeSnap({
      moving: rect(20.8, 100, 40, 10),
      others: [rect(20, 50, 30, 10)],
      bounds: BOUNDS,
    });
    expect(r.dx).toBeCloseTo(-0.8, 5);
    expect(r.guides).toEqual([{ axis: 'x', pos: 20, start: 50, end: 110 }]);
  });

  it('左边缘与中心线同距时优先取中心对齐（平局取更近者）', () => {
    const r = computeSnap({
      moving: rect(20.8, 100, 40, 10),
      others: [rect(20, 50, 40, 10)],
      bounds: BOUNDS,
    });
    expect(r.dx).toBeCloseTo(-0.8, 5);
    expect(r.guides[0]).toMatchObject({ axis: 'x', pos: 40 });
  });

  it('超出阈值不吸附', () => {
    const r = computeSnap({
      moving: rect(30, 100, 40, 10),
      others: [rect(20, 50, 40, 10)],
      bounds: BOUNDS,
    });
    expect(r.dx).toBe(0);
    expect(r.guides).toHaveLength(0);
  });

  it('吸附到页面水平中心（居中摆放）', () => {
    // 页面中心 105；元素宽 40，居中时 x = 85
    const r = computeSnap({ moving: rect(86.2, 10, 40, 10), others: [], bounds: BOUNDS });
    expect(r.dx).toBeCloseTo(-1.2, 5);
  });

  it('吸附到页面左边界（贴边）', () => {
    const r = computeSnap({ moving: rect(1.0, 10, 40, 10), others: [], bounds: BOUNDS });
    expect(r.dx).toBeCloseTo(-1, 5);
  });

  it('同时命中 x / y 两个方向', () => {
    const r = computeSnap({
      moving: rect(20.5, 50.4, 40, 10),
      others: [rect(20, 50, 40, 10)],
      bounds: BOUNDS,
    });
    expect(r.dx).toBeCloseTo(-0.5, 5);
    expect(r.dy).toBeCloseTo(-0.4, 5);
    expect(r.guides).toHaveLength(2);
  });

  it('参考线跨度覆盖移动元素与被吸附元素', () => {
    const r = computeSnap({
      moving: rect(20.5, 200, 40, 10),
      others: [rect(20, 50, 30, 10)],
      bounds: BOUNDS,
    });
    const guide = r.guides.find((g) => g.axis === 'x');
    expect(guide).toBeDefined();
    expect(guide!.start).toBe(50);
    expect(guide!.end).toBe(210);
  });

  it('可选阈值参数生效', () => {
    const far = { moving: rect(24, 100, 40, 10), others: [rect(20, 50, 40, 10)], bounds: BOUNDS };
    expect(computeSnap(far).dx).toBe(0);
    expect(computeSnap({ ...far, threshold: 5 }).dx).toBeCloseTo(-4, 5);
  });
});
