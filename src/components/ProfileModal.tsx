import { ProfileForm } from './ProfileForm';

interface Props {
  onClose: () => void;
}

/** 个人信息弹窗（F1 保留的快捷入口），表单复用 ProfileForm。 */
export function ProfileModal({ onClose }: Props) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={onClose}>
      <div
        className="bg-white rounded-xl shadow-2xl w-[520px] max-w-[92vw] max-h-[85vh] overflow-auto p-6"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between mb-5">
          <h2 className="text-base font-semibold text-gray-800">个人信息</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-xl leading-none">×</button>
        </div>
        <ProfileForm onSaved={onClose} />
      </div>
    </div>
  );
}
