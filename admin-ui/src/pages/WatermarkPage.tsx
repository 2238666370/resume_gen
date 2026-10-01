import { useState } from 'react';
import { Alert, Button, Card, Descriptions, Result, Typography, Upload } from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import type { UploadFile } from 'antd';
import { api } from '../api';

const { Dragger } = Upload;

/** 水印溯源：上传疑似盗用图片 → 解码盲水印 → 定位来源账号/设备。 */
export default function WatermarkPage() {
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<Record<string, unknown> | null>(null);
  const [error, setError] = useState('');

  const onUpload = async (file: UploadFile) => {
    if (!file.originFileObj) return;
    setLoading(true);
    setError('');
    setResult(null);
    try {
      const data = await api.decodeWatermark(file.originFileObj as File);
      setResult(data);
    } catch (err) {
      setError((err as Error)?.message || '解码失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ maxWidth: 720 }}>
      <Card title="水印溯源" bordered={false}>
        <Typography.Paragraph type="secondary">
          上传疑似盗用 / 转发的图片，系统将解码其中嵌入的盲水印，定位来源账号与设备（仅供溯源威慑）。
        </Typography.Paragraph>
        <Dragger
          accept="image/*"
          showUploadList={false}
          customRequest={({ file }) => onUpload(file as UploadFile)}
          disabled={loading}
        >
          <p className="ant-upload-drag-icon"><InboxOutlined /></p>
          <p className="ant-upload-text">{loading ? '解码中...' : '点击或拖拽图片到此处上传'}</p>
          <p className="ant-upload-hint">支持 PNG / JPG，普通压缩、缩放后仍可能解码成功</p>
        </Dragger>

        {error && <Alert style={{ marginTop: 16 }} type="error" message={error} showIcon />}

        {result && (
          <div style={{ marginTop: 16 }}>
            {result.found === false ? (
              <Result status="warning" title="未检测到水印" subTitle="该图片未嵌入盲水印，无法溯源。" />
            ) : (
              <Card size="small" title="溯源结果">
                <Descriptions column={1} bordered size="small">
                  <Descriptions.Item label="来源账号">{String(result.userId ?? '-')}</Descriptions.Item>
                  <Descriptions.Item label="设备标识">{String(result.deviceId ?? '-')}</Descriptions.Item>
                  <Descriptions.Item label="原始负载">{String(result.payload ?? '-')}</Descriptions.Item>
                </Descriptions>
              </Card>
            )}
            <Button style={{ marginTop: 12 }} onClick={() => setResult(null)}>重新上传</Button>
          </div>
        )}
      </Card>
    </div>
  );
}
