import AddWxAccount from '@/components/AddWxAccount';
import { PageContainer } from '@ant-design/pro-components';
import { useModel } from '@umijs/max';
import React from 'react';

const Welcome: React.FC = () => {
  const { initialState } = useModel('@@initialState');
  console.log(initialState, 'initialState');

  return (
    <PageContainer>
      <AddWxAccount />
    </PageContainer>
  );
};

export default Welcome;
