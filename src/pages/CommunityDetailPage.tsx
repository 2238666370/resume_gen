import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { addComment, collectPost, deleteComment, getInteraction, getPost, likePost, listComments, reportPost } from '../api/community';
import type { CommunityComment, CommunityPostDetail, Interaction } from '../api/types';
import { ResumeTemplate } from '../components/templates/ResumeTemplate';
import { useAuthStore } from '../store/authStore';

export function CommunityDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const token = useAuthStore((s) => s.token);
  const user = useAuthStore((s) => s.user);

  const [post, setPost] = useState<CommunityPostDetail | null>(null);
  const [comments, setComments] = useState<CommunityComment[]>([]);
  const [interaction, setInteraction] = useState<Interaction | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [content, setContent] = useState('');
  const [replyTo, setReplyTo] = useState<{ id: string; name: string } | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError('');
    try {
      const p = await getPost(id);
      setPost(p);
      setComments(await listComments(id));
      if (token) {
        try {
          setInteraction(await getInteraction(id));
        } catch {
          setInteraction(null);
        }
      }
    } catch (err) {
      setError((err as Error)?.message || '帖子不存在或已下线');
    } finally {
      setLoading(false);
    }
  }, [id, token]);

  useEffect(() => {
    load();
  }, [load]);

  const renderTemplate = useMemo(() => {
    if (!post) return null;
    const resume = post.resume;
    return resume ? <ResumeTemplate data={resume} /> : null;
  }, [post]);

  const requireLogin = (fn: () => void) => {
    if (!token) {
      navigate('/login', { replace: true });
      return;
    }
    fn();
  };

  const toggleLike = () => requireLogin(async () => {
    setInteraction(await likePost(id!));
  });

  const toggleCollect = () => requireLogin(async () => {
    setInteraction(await collectPost(id!));
  });

  const submitComment = async () => {
    if (!content.trim()) return;
    setSubmitting(true);
    try {
      await addComment(id!, content.trim(), replyTo?.id);
      setContent('');
      setReplyTo(null);
      setComments(await listComments(id!));
    } catch (err) {
      alert((err as Error)?.message || '评论失败');
    } finally {
      setSubmitting(false);
    }
  };

  const handleReport = () => requireLogin(async () => {
    const reason = window.prompt('请填写举报理由');
    if (reason === null) return;
    try {
      await reportPost(id!, reason);
      alert('举报已提交');
    } catch (err) {
      alert((err as Error)?.message || '举报失败');
    }
  });

  const renderComment = (c: CommunityComment, depth = 0) => (
    <div key={c.id} className={`${depth > 0 ? 'ml-6 mt-3 border-l-2 border-gray-100 pl-3' : 'mt-3'}`}>
      <div className="flex items-center gap-2">
        <span className="text-sm font-medium text-gray-700">{c.userNickname || '用户'}</span>
        <span className="text-xs text-gray-400">{c.createdAt?.slice(5, 16)}</span>
      </div>
      <p className="text-sm text-gray-600 mt-1">{c.content}</p>
      <div className="flex items-center gap-4 mt-1.5 text-xs text-gray-400">
        <button onClick={() => requireLogin(() => setReplyTo({ id: c.id, name: c.userNickname }))} className="hover:text-blue-500">回复</button>
        {token && user?.id === c.userId && (
          <button onClick={async () => {
            if (!window.confirm('删除这条评论？')) return;
            await deleteComment(c.id);
            setComments(await listComments(id!));
          }} className="hover:text-red-500">删除</button>
        )}
      </div>
      {c.children?.map((child) => renderComment(child, depth + 1))}
    </div>
  );

  if (loading) {
    return <div className="min-h-screen flex items-center justify-center text-gray-500">加载中...</div>;
  }

  if (error || !post) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center gap-4 text-gray-600">
        <p>{error || '帖子不存在或已下线'}</p>
        <button onClick={() => navigate('/discover?tab=community')} className="text-sm text-blue-600 hover:underline">返回社区</button>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-100 py-8 px-4">
      <div className="max-w-[794px] mx-auto">
        <button onClick={() => navigate('/discover?tab=community')} className="text-sm text-gray-500 hover:text-gray-700 mb-4">← 返回社区</button>

        <div className="bg-white rounded-xl shadow-sm p-6 mb-6">
          <h1 className="text-xl font-semibold text-gray-800">{post.title}</h1>
          {post.summary && <p className="text-sm text-gray-500 mt-2">{post.summary}</p>}
          <div className="flex items-center gap-2 mt-3 flex-wrap">
            {post.tags?.map((t) => <span key={t} className="text-xs px-2 py-0.5 rounded bg-blue-50 text-blue-600">#{t}</span>)}
          </div>
          <div className="flex items-center justify-between mt-4 text-sm text-gray-400">
            <span>{post.authorNickname || '匿名'} · {post.createdAt}</span>
            <div className="flex items-center gap-4">
              <button onClick={toggleLike} className={`flex items-center gap-1 ${interaction?.liked ? 'text-blue-600' : 'hover:text-blue-600'}`}>
                👍 {interaction ? interaction.likeCount : post.likeCount}
              </button>
              <button onClick={toggleCollect} className={`flex items-center gap-1 ${interaction?.collected ? 'text-amber-500' : 'hover:text-amber-500'}`}>
                ⭐ {interaction ? interaction.collectCount : post.collectCount}
              </button>
              <span>👁 {post.viewCount}</span>
              <span>💬 {post.commentCount}</span>
              <button onClick={handleReport} className="text-gray-400 hover:text-red-500">举报</button>
            </div>
          </div>
        </div>

        <div className="bg-white shadow-2xl rounded-sm mb-8 overflow-hidden" style={{ minHeight: '1123px' }}>
          {renderTemplate}
        </div>

        <div className="bg-white rounded-xl shadow-sm p-6 mb-8">
          <h2 className="font-semibold text-gray-800 mb-4">评论（{comments.length}）</h2>

          {replyTo && (
            <p className="text-sm text-gray-500 mb-2">回复 @{replyTo.name}
              <button onClick={() => setReplyTo(null)} className="ml-2 text-blue-600">取消</button>
            </p>
          )}
          <div className="flex gap-2 mb-4">
            <input
              value={content}
              onChange={(e) => setContent(e.target.value)}
              onFocus={() => { if (!token) navigate('/login', { replace: true }); }}
              placeholder={token ? '写下你的评论...' : '登录后参与评论'}
              maxLength={1000}
              className="flex-1 border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <button
              onClick={submitComment}
              disabled={submitting || !token}
              className="px-4 py-2 bg-blue-600 text-white rounded-md text-sm hover:bg-blue-700 disabled:opacity-50"
            >
              评论
            </button>
          </div>

          {comments.length === 0 ? (
            <p className="text-sm text-gray-400">还没有评论</p>
          ) : (
            comments.map((c) => renderComment(c))
          )}
        </div>
      </div>
    </div>
  );
}