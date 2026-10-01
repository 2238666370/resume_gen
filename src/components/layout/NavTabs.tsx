interface TabItem<T extends string> {
  key: T;
  label: string;
}

interface Props<T extends string> {
  items: TabItem<T>[];
  value: T;
  onChange: (key: T) => void;
  /** 右侧附加内容（如搜索框、操作按钮）。 */
  right?: React.ReactNode;
}

/**
 * 页内二级 Tab（F1）：选中态由调用方驱动（通常来自 URL 查询参数）。
 */
export function NavTabs<T extends string>({ items, value, onChange, right }: Props<T>) {
  return (
    <div className="flex items-center justify-between gap-4 mb-6 flex-wrap">
      <div className="flex gap-1 bg-gray-100 p-1 rounded-lg w-fit">
        {items.map((it) => (
          <button
            key={it.key}
            type="button"
            onClick={() => onChange(it.key)}
            className={`px-4 py-1.5 text-sm rounded-md transition-colors ${
              value === it.key
                ? 'bg-white text-gray-800 shadow-sm font-medium'
                : 'text-gray-500 hover:text-gray-700'
            }`}
          >
            {it.label}
          </button>
        ))}
      </div>
      {right && <div className="flex items-center gap-2">{right}</div>}
    </div>
  );
}
