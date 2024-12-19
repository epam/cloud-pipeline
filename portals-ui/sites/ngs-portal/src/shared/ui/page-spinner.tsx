import { Spin } from 'antd';

export const PageSpinner = () => {
  return (
    <div className="size-full flex items-center justify-center">
      <Spin size="large" />
    </div>
  );
};
