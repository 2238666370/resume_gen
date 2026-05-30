/** 加粗语法提示，附在多行文本框下方 */
export function BoldHint() {
  return (
    <p className="text-[10px] text-gray-400 mt-1 select-none">
      使用 <code className="bg-gray-100 px-1 rounded font-mono">**文字**</code> 可加粗，如：
      <span className="text-gray-500"> 负责 **前端架构** 设计</span>
    </p>
  );
}
