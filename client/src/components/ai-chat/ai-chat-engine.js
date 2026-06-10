import {observable, makeAutoObservable} from 'mobx';
import preferences from '../../models/preferences/PreferencesLoad';
import {io} from 'socket.io-client';

/**
 * @returns {Promise<{base: string, socketIOUrl: URL}>}
 */
async function _initializeAiChatAPI() {
  await preferences.fetchIfNeededOrWait();
  const {api} = preferences.miscAIPreferences || {};
  if (!api) {
    throw new Error('Chatbot API is not specified');
  }
  let base = api;
  if (!base.endsWith('/')) {
    base = base + '/';
  }
  const socketIOUrl = new URL('socket.io', base);
  return {
    base,
    socketIOUrl,
  };
}

async function _aiApiFetch(uri, options) {
  const {base} = await _initializeAiChatAPI();
  if (uri.startsWith('/')) {
    uri = uri.slice(1);
  }
  const response = await fetch(`${base}${uri}`, options);
  if (!response.ok) {
    throw new Error(
      response.statusText
        ? `error fetching "${uri}": ${response.statusText}`
        : `error fetching "${uri}": ${response.status}`,
    );
  }
  const data = await response.json();
  const {status = 'OK', message, payload} = data || {};
  if (!/^OK$/i.test(status)) {
    throw new Error(message || `error fetching "${uri}"`);
  }
  return payload;
}

async function _aiApiGet(uri) {
  const fetchOptions = {
    mode: 'cors',
    credentials: 'include',
    method: 'GET',
    headers: {
      'Content-Type': 'application/json; charset=UTF-8;',
    },
  };
  return _aiApiFetch(uri, fetchOptions);
}

async function _aiApiPost(uri, body) {
  const fetchOptions = {
    mode: 'cors',
    credentials: 'include',
    method: 'POST',
    headers: {
      'Content-Type': 'application/json; charset=UTF-8;',
    },
    body: JSON.stringify(body),
  };
  return _aiApiFetch(uri, fetchOptions);
}

class AiChatEngine {
  _pending = true;
  _socketPending = false;
  _messagePending = false;
  _error = undefined;
  _socketError = undefined;
  _messageError = undefined;
  _chat = undefined;
  _messages = [];
  _initialized = false;
  _chatId = undefined;

  _initializePromise = undefined;
  _initializeToken = {};

  _socket = undefined;
  _socketToken = {};

  _messageToken = {};

  constructor(options = undefined) {
    makeAutoObservable(this);
    const {chatId} = options || {};
    this.changeChat(chatId);
  }

  get pending() {
    return this._pending || this._messagePending || this._socketPending;
  }

  get initialized() {
    return this._initialized;
  }

  get error() {
    return this._error;
  }

  get socketError() {
    return this._socketError;
  }

  get messageError() {
    return this._messageError;
  }

  get messages() {
    return this._messages;
  }

  destroy() {
    this.stopListeningMessage();
    this._initializeToken = {};
    this._initializePromise = undefined;
    this._messageToken = {};
  }

  async changeChat(chatId) {
    if (this._chatId === chatId) {
      return;
    }
    this.stopListeningMessage();
    this._initialized = false;
    this._chatId = chatId;
    this._chat = undefined;
    this._pending = true;
    this._error = undefined;
    this._messages = [];
    this._initializeToken = {};
    this._initializePromise = undefined;
    this._messageToken = {};
    this._messagePending = false;
    this._messageError = undefined;
    await this.initialize();
  }

  async sendMessage(message) {
    const token = (this._messageToken = {});
    this._messageError = undefined;
    this._messagePending = true;
    const applyChanges = (fn) => {
      if (token === this._messageToken) {
        fn();
        return true;
      }
      return false;
    };
    const invalidated = () => token !== this._messageToken;
    try {
      await this.initialize();
      if (!this._initialized || !this._chat) {
        throw new Error('Chatbot not initialized');
      }
      if (invalidated()) {
        return;
      }
      await _aiApiPost(`chat/${this._chat.identifier}/message`, message);
      if (invalidated()) {
        return;
      }
      await this.reloadMessages();
    } catch (e) {
      applyChanges(() => {
        this._messageError = e.message;
      });
    } finally {
      applyChanges(() => {
        this._messagePending = false;
      });
    }
  }

  async reload() {
    await this.initialize(true);
  }

  async initialize(force = false) {
    if (!this._initializePromise || force) {
      this._initializePromise = (async () => {
        const token = (this._initializeToken = {});
        const applyChanges = (fn) => {
          if (token === this._initializeToken) {
            fn();
            return true;
          }
          return false;
        };
        const stopInitialization = () => token !== this._initializeToken;
        let listenMessage;
        try {
          this._pending = true;
          this._initialized = false;
          const chat = await (async () => {
            if (this._chatId) {
              return _aiApiGet(`chat/${this._chatId}`);
            }
            return _aiApiPost('chat');
          })();
          if (stopInitialization()) {
            return;
          }
          if (!chat) {
            throw new Error('Error initializing chat');
          }
          const messages = await _aiApiGet(`chat/${chat.identifier}/messages`);
          return applyChanges(() => {
            this._chat = chat;
            this._chatId = chat.identifier;
            this._messages = (messages || []).map((message) => observable(message));
            this._error = undefined;
            this._initialized = true;
            const assistantMessages = (messages || []).filter(
              (message) => (message.role || '').toLowerCase() === 'assistant',
            );
            const pendingAssistantMessages = assistantMessages.filter((message) => message.pending);
            listenMessage = pendingAssistantMessages.pop();
          });
        } catch (error) {
          applyChanges(() => {
            this._error = error.message;
          });
          return false;
        } finally {
          applyChanges(() => {
            this._pending = false;
            if (this._initialized && listenMessage) {
              this.startListeningMessage(listenMessage);
            }
          });
        }
      })();
    }
    return this._initializePromise;
  }

  async reloadMessages() {
    try {
      await this.initialize();
      this.stopListeningMessage();
      if (!this._initialized || !this._chat) {
        throw new Error('Chatbot not initialized');
      }
      this._pending = true;
      const messages = await _aiApiGet(`chat/${this._chatId}/messages`);
      this._messages = (messages || []).map((message) => observable(message));
      this._error = undefined;
      const assistantMessages = (messages || []).filter(
        (message) => (message.role || '').toLowerCase() === 'assistant',
      );
      const pendingAssistantMessages = assistantMessages.filter((message) => message.pending);
      const listenMessage = pendingAssistantMessages.pop();
      if (this._initialized && listenMessage) {
        await this.startListeningMessage(listenMessage);
      }
    } catch (error) {
      this._error = error.message;
    } finally {
      this._pending = false;
    }
  }

  async startListeningMessage(message) {
    if (!message) {
      return;
    }
    this.stopListeningMessage();
    const token = (this._socketToken = {});
    const applyChanges = (fn) => {
      if (token === this._socketToken) {
        fn();
        return true;
      }
      return false;
    };
    let socket;
    try {
      const messageId =
        typeof message === 'string'
          ? message
          : typeof message === 'object'
            ? message.identifier
            : undefined;
      if (!messageId) {
        throw new Error(`Unsupported message type "${typeof message}"`);
      }
      const messageInstance = this._messages.find((m) => m.identifier === messageId);
      if (!messageInstance) {
        throw new Error(`Message ${messageId} not found`);
      }
      const messageIdShort = messageInstance.identifier.slice(0, 8);
      console.log(`[AI Chat engine] starting listening message ${messageIdShort}`, messageInstance);
      this._socketPending = true;
      this._socketError = undefined;
      let finished = false;
      const {socketIOUrl} = await _initializeAiChatAPI();
      socket = this._socket = io(socketIOUrl.origin, {
        withCredentials: true,
        secure: true,
        path: socketIOUrl.pathname,
        transports: ['websocket'],
      });
      const waitUntilConnect = () =>
        new Promise((resolve, reject) => {
          const errorGenerator = (socketError) => () => {
            finished = true;
            reject(new Error(socketError));
          };
          socket.on('connect', resolve);
          const onConnectError = errorGenerator('socket connect error');
          const onDisconnect = errorGenerator('socket disconnected');
          socket.on('connect_error', onConnectError);
          socket.on('disconnect', onDisconnect);
        });
      console.log('[AI Chat engine] connecting to socket...');
      await waitUntilConnect();
      console.log('[AI Chat engine] connected to socket');
      applyChanges(() => {
        this._socket = socket;

        const onDone = () => {
          if (finished) {
            return;
          }
          finished = true;
          applyChanges(() => {
            console.log(
              `[AI Chat engine] finishing listening message ${messageIdShort} updates...`,
            );
            this.stopListeningMessage();
            this.reloadMessages();
            this._socketPending = false;
          });
        };
        const onError = (error = '') => {
          applyChanges(() => {
            console.log(
              `[AI Chat engine] error listening message ${messageIdShort} updates: ${error}`,
            );
            this._socketError = error;
          });
        };
        const onUpdateMessage = (data) => {
          const {parts = [], status, pending} = data;
          messageInstance.parts = parts;
          messageInstance.status = status;
          messageInstance.pending = pending;
        };
        const onUpdateMessagePart = (part) => {
          const {parts = []} = messageInstance;
          const {identifier} = part;
          const partIndex = parts.findIndex((o) => o.identifier === identifier);
          if (partIndex >= 0) {
            const newParts = parts.slice();
            newParts.splice(partIndex, 1, part);
            messageInstance.parts = newParts;
          }
        };
        socket.on('done', onDone);
        socket.on('error', onError);
        socket.on('update_message', onUpdateMessage);
        socket.on('update_part', onUpdateMessagePart);
        socket.on('disconnect', onDone);
        console.log(`[AI Chat engine] starting listening message ${messageIdShort} updates...`);
        socket.emit('assistant', {
          chat_id: messageInstance.chat_id,
          message_id: messageInstance.identifier,
        });
      });
    } catch (error) {
      this._socketError = error.message;
      this.stopListeningMessage();
      await this.reloadMessages();
    } finally {
      this._socketPending = false;
    }
  }

  stopListeningMessage = () => {
    this._socketToken = {};
    if (this._socket) {
      console.log('[AI Chat engine] stop listening message updates');
      this._socket.close();
      this._socket = undefined;
      this._socketPending = false;
      this._socketError = undefined;
      return true;
    }
    return false;
  };
}

export default AiChatEngine;
