interface Props {
  label?: string;
  className?: string;
}

/**
 * 不确定进度条：AI 异步任务无法提供真实百分比，用流动动画提示处理中。
 */
export function ProgressBar({ label, className = '' }: Props) {
  return (
    <div className={className}>
      {label && <p className="text-xs text-gray-400 mb-1.5">{label}</p>}
      <div className="relative h-1.5 w-full overflow-hidden rounded-full bg-gray-100">
        <div className="progress-bar-indeterminate absolute top-0 h-full w-1/3 rounded-full bg-blue-500" />
      </div>
      <style>{`
        .progress-bar-indeterminate {
          animation: progress-slide 1.2s ease-in-out infinite;
        }
        @keyframes progress-slide {
          0% { left: -35%; }
          100% { left: 100%; }
        }
      `}</style>
    </div>
  );
}