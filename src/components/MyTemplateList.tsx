import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { deleteTemplate, listMyTemplates, publishTemplate } from '../api/templates';
import type { TemplateVO } from '../api/types';
import { getErrorMessage } from '../api/client';

const STATUS_LABEL: Record<number, { text: string; cls: string }> = {
  0: { text: '已下架', cls: 'bg-gray-100 text-gray-500' },
  1: { text: '已上架', cls: 'bg-green-50 text-green-600' },
  2: { text: '待审核', cls: 'bg-amber-50 text-amber-600' },
  3: { text: '草稿', cls: 'bg-blue-50 text-blue-600' },
};

/**
 * 我的模板列表（F1）：从 MyTemplatesPage 抽取，供「工作台 · 我的模板」复用，不含页面壳。
 */
export function MyTemplateList() {
  const navigate = useNavigate();
  const [items, setItems] = useState<TemplateVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setItems(await listMyTemplates());
    } catch (err) {
      setError(getErrorMessage(err, '加载失败'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const handlePublish = async (id: string) => {
    try {
      await publishTemplate(id);
      load();
    } catch (err) {
      alert(getErrorMessage(err, '提交失败'));
    }
  };

  const handleDelete = async (id: string) => {
    if (!window.confirm('确定删除该模板？')) return;
    try {
      await deleteTemplate(id);
      load();
    } catch (err) {
      alert(getErrorMessage(err, '删除失败'));
    }
  };

  if (loading) {
    return <div className="text-center text-gray-400 py-20">加载中...</div>;
  }

  return (
    <>
      {error && <p className="text-sm text-red-500 mb-4">{error}</p>}
      {items.length === 0 ? (
        <div className="text-center py-20">
          <p className="text-gray-400">还没有自定义模板，点击右上角「+ 新建模板」开始</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {items.map((t) => {
            const st = STATUS_LABEL[t.status] ?? STATUS_LABEL[3];
            return (
              <div key={t.id} className="bg-white rounded-xl border border-gray-200 p-5">
                <div className="flex items-start justify-between">
                  <div className="min-w-0">
                    <h2 className="font-medium text-gray-800 truncate">{t.name}</h2>
                    <p className="text-xs text-gray-400 mt-1">{t.category || '未分类'}</p>
                    {t.auditReason && <p className="text-xs text-red-400 mt-1">原因：{t.auditReason}</p>}
                  </div>
                  <span className={`text-xs px-2 py-0.5 rounded flex-shrink-0 ml-3 ${st.cls}`}>{st.text}</span>
                </div>
                <p className="text-xs text-gray-400 mt-2">使用 {t.useCount ?? 0} 次 · 浏览 {t.viewCount ?? 0} 次</p>
                <div className="flex items-center gap-2 mt-4">
                  <button onClick={() => navigate(`/templates/builder/${t.id}`)} className="px-3 py-1.5 text-xs text-blue-600 border border-blue-200 rounded-md hover:bg-blue-50">编辑</button>
                  {(t.status === 3 || t.status === 0) && (
                    <button onClick={() => handlePublish(t.id)} className="px-3 py-1.5 text-xs text-green-600 border border-green-200 rounded-md hover:bg-green-50">发布</button>
                  )}
                  <div className="flex-1" />
                  <button onClick={() => handleDelete(t.id)} className="px-3 py-1.5 text-xs text-red-400 hover:text-red-600">删除</button>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </>
  );
}
