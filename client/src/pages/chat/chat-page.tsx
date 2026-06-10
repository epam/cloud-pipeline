import AIChatPage from '../../components/ai-chat';
import {LegacyComponentBridge} from '../_shared/legacy-component-bridge';

function ChatPage() {
  return <LegacyComponentBridge component={AIChatPage} />;
}

export {ChatPage};
