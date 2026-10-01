import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';

export interface DropdownItem {
  key: string;
  label: string;
  onClick: () => void;
  danger?: boolean;
  disabled?: boolean;
}

interface Props {
  /** 触发按钮内容（文案/图标）。 */
  label: ReactNode;
  /** 菜单项；与 children 二选一（children 优先）。 */
  items?: DropdownItem[];
  /** 自定义面板内容（如样式选择器）。 */
  children?: ReactNode;
  /** 主按钮样式（高亮）。 */
  primary?: boolean;
  disabled?: boolean;
  align?: 'left' | 'right';
  /** 面板额外类名（自定义内容时调整宽度/内边距）。 */
  panelClassName?: string;
}

/**
 * 轻量下拉菜单（F1 Toolbar 分组用），无第三方依赖，点击外部关闭。
 */
export function DropdownMenu({ label, items = [], children, primary = false, disabled = false, align = 'right', panelClassName }: Props) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onDocClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', onDocClick);
    return () => document.removeEventListener('mousedown', onDocClick);
  }, [open]);

  const base = primary
    ? 'bg-blue-600 text-white hover:bg-blue-700'
    : 'text-gray-600 hover:bg-gray-100 border border-gray-200';

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        disabled={disabled}
        className={`flex items-center gap-1.5 px-3 py-1.5 text-sm rounded-md transition-colors disabled:opacity-60 ${base}`}
      >
        {label}
        <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
          <polyline points="6 9 12 15 18 9" />
        </svg>
      </button>
      {open && (
        <div
          className={`absolute top-10 z-50 min-w-[140px] bg-white border border-gray-200 rounded-lg shadow-xl ${
            children ? '' : 'py-1'
          } ${align === 'right' ? 'right-0' : 'left-0'} ${panelClassName ?? ''}`}
        >
          {children ?? items.map((it) => (
            <button
              key={it.key}
              type="button"
              disabled={it.disabled}
              onClick={() => {
                setOpen(false);
                it.onClick();
              }}
              className={`w-full text-left px-3.5 py-2 text-sm transition-colors disabled:opacity-40 ${
                it.danger ? 'text-red-500 hover:bg-red-50' : 'text-gray-700 hover:bg-gray-50'
              }`}
            >
              {it.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
