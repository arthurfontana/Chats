const STORAGE_KEY = "glm_chat_conversations";

const messagesEl = document.getElementById("messages");
const emptyState = document.getElementById("emptyState");
const chatListEl = document.getElementById("chatList");
const inputEl = document.getElementById("input");
const sendBtn = document.getElementById("sendBtn");
const composer = document.getElementById("composer");
const newChatBtn = document.getElementById("newChatBtn");

let conversations = loadConversations();
let activeId = conversations.length ? conversations[0].id : createConversation();
let isStreaming = false;

renderSidebar();
renderMessages();

function loadConversations() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch {
    return [];
  }
}

function saveConversations() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(conversations));
}

function createConversation() {
  const id = crypto.randomUUID();
  conversations.unshift({ id, title: "Nova conversa", messages: [] });
  saveConversations();
  return id;
}

function getActive() {
  return conversations.find((c) => c.id === activeId);
}

function renderSidebar() {
  chatListEl.innerHTML = "";
  for (const conv of conversations) {
    const item = document.createElement("div");
    item.className = "chat-item" + (conv.id === activeId ? " active" : "");
    item.innerHTML = `<span class="title"></span><button class="delete-btn" title="Excluir">✕</button>`;
    item.querySelector(".title").textContent = conv.title;
    item.addEventListener("click", (e) => {
      if (e.target.closest(".delete-btn")) return;
      activeId = conv.id;
      renderSidebar();
      renderMessages();
    });
    item.querySelector(".delete-btn").addEventListener("click", (e) => {
      e.stopPropagation();
      deleteConversation(conv.id);
    });
    chatListEl.appendChild(item);
  }
}

function deleteConversation(id) {
  conversations = conversations.filter((c) => c.id !== id);
  if (conversations.length === 0) {
    activeId = createConversation();
  } else if (id === activeId) {
    activeId = conversations[0].id;
  }
  saveConversations();
  renderSidebar();
  renderMessages();
}

function renderMessages() {
  const conv = getActive();
  messagesEl.innerHTML = "";
  if (!conv.messages.length) {
    messagesEl.appendChild(emptyState);
    return;
  }
  for (const msg of conv.messages) {
    messagesEl.appendChild(buildMessageRow(msg.role, msg.content));
  }
  scrollToBottom();
}

function buildMessageRow(role, content) {
  const row = document.createElement("div");
  row.className = `message-row ${role}`;
  const bubble = document.createElement("div");
  bubble.className = "bubble";
  bubble.innerHTML = renderMarkdown(content);
  row.appendChild(bubble);
  return row;
}

function renderMarkdown(text) {
  const html = window.marked ? window.marked.parse(text) : escapeHtml(text);
  return window.DOMPurify ? window.DOMPurify.sanitize(html) : html;
}

function escapeHtml(text) {
  const div = document.createElement("div");
  div.textContent = text;
  return div.innerHTML;
}

function scrollToBottom() {
  messagesEl.scrollTop = messagesEl.scrollHeight;
}

// Auto-resize textarea
inputEl.addEventListener("input", () => {
  inputEl.style.height = "auto";
  inputEl.style.height = Math.min(inputEl.scrollHeight, 200) + "px";
  sendBtn.disabled = !inputEl.value.trim() || isStreaming;
});

inputEl.addEventListener("keydown", (e) => {
  if (e.key === "Enter" && !e.shiftKey) {
    e.preventDefault();
    composer.requestSubmit();
  }
});

newChatBtn.addEventListener("click", () => {
  activeId = createConversation();
  renderSidebar();
  renderMessages();
  inputEl.focus();
});

composer.addEventListener("submit", async (e) => {
  e.preventDefault();
  const text = inputEl.value.trim();
  if (!text || isStreaming) return;

  const conv = getActive();
  conv.messages.push({ role: "user", content: text });
  if (conv.messages.length === 1) {
    conv.title = text.slice(0, 40) + (text.length > 40 ? "…" : "");
  }
  saveConversations();
  renderSidebar();
  renderMessages();

  inputEl.value = "";
  inputEl.style.height = "auto";
  sendBtn.disabled = true;
  isStreaming = true;

  const assistantRow = buildMessageRow("assistant", "");
  const bubble = assistantRow.querySelector(".bubble");
  bubble.classList.add("cursor-blink");
  messagesEl.appendChild(assistantRow);
  scrollToBottom();

  let fullText = "";

  try {
    const res = await fetch("/api/chat", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ messages: conv.messages }),
    });

    if (!res.ok || !res.body) {
      throw new Error(`Erro do servidor (${res.status})`);
    }

    const reader = res.body.getReader();
    const decoder = new TextDecoder();

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      fullText += decoder.decode(value, { stream: true });
      bubble.innerHTML = renderMarkdown(fullText);
      bubble.classList.add("cursor-blink");
      scrollToBottom();
    }
  } catch (err) {
    fullText += `\n\n_Erro ao conectar com o servidor: ${err.message}_`;
    bubble.innerHTML = renderMarkdown(fullText);
  } finally {
    bubble.classList.remove("cursor-blink");
    conv.messages.push({ role: "assistant", content: fullText });
    saveConversations();
    isStreaming = false;
    sendBtn.disabled = !inputEl.value.trim();
  }
});
