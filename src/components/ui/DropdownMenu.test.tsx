import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DropdownMenu } from './DropdownMenu';

describe('DropdownMenu', () => {
  it('默认收起，点击触发按钮后展开菜单项', async () => {
    render(<DropdownMenu label="AI" items={[{ key: 'a', label: 'AI 建议', onClick: vi.fn() }]} />);

    expect(screen.queryByText('AI 建议')).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'AI' }));
    expect(screen.getByRole('button', { name: 'AI 建议' })).toBeInTheDocument();
  });

  it('点击菜单项触发回调并收起菜单', async () => {
    const onClick = vi.fn();
    render(<DropdownMenu label="文件" items={[{ key: 'import', label: '导入 JSON', onClick }]} />);

    await userEvent.click(screen.getByRole('button', { name: '文件' }));
    await userEvent.click(screen.getByRole('button', { name: '导入 JSON' }));

    expect(onClick).toHaveBeenCalledTimes(1);
    expect(screen.queryByText('导入 JSON')).not.toBeInTheDocument();
  });

  it('点击菜单外部收起', async () => {
    render(<DropdownMenu label="导出" items={[{ key: 'pdf', label: '导出 PDF', onClick: vi.fn() }]} />);

    await userEvent.click(screen.getByRole('button', { name: '导出' }));
    expect(screen.getByText('导出 PDF')).toBeInTheDocument();

    await userEvent.click(document.body);
    expect(screen.queryByText('导出 PDF')).not.toBeInTheDocument();
  });

  it('disabled 时按钮不可用且不展开', async () => {
    render(<DropdownMenu label="导出" disabled items={[{ key: 'pdf', label: '导出 PDF', onClick: vi.fn() }]} />);

    const trigger = screen.getByRole('button', { name: '导出' });
    expect(trigger).toBeDisabled();

    await userEvent.click(trigger);
    expect(screen.queryByText('导出 PDF')).not.toBeInTheDocument();
  });

  it('支持自定义面板内容（children 优先于 items）', async () => {
    render(
      <DropdownMenu label="样式" items={[{ key: 'x', label: '不应出现', onClick: vi.fn() }]}>
        <div>模板与配色面板</div>
      </DropdownMenu>,
    );

    await userEvent.click(screen.getByRole('button', { name: '样式' }));
    expect(screen.getByText('模板与配色面板')).toBeInTheDocument();
    expect(screen.queryByText('不应出现')).not.toBeInTheDocument();
  });

  it('禁用状态的菜单项不触发回调', async () => {
    const onClick = vi.fn();
    render(<DropdownMenu label="导出" items={[{ key: 'pdf', label: '导出 PDF', onClick, disabled: true }]} />);

    await userEvent.click(screen.getByRole('button', { name: '导出' }));
    await userEvent.click(screen.getByRole('button', { name: '导出 PDF' }));

    expect(onClick).not.toHaveBeenCalled();
  });
});
