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

  if (role === "assistant") {
    const actions = document.createElement("div");
    actions.className = "message-actions";
    const listenBtn = document.createElement("button");
    listenBtn.type = "button";
    listenBtn.className = "listen-btn";
    listenBtn.innerHTML = `${ICON_SPEAKER} Ouvir`;
    listenBtn.addEventListener("click", () => toggleListenButton(listenBtn, content));
    actions.appendChild(listenBtn);
    row.appendChild(actions);
  }

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

// ---------- Leitor de texto (Text-to-Speech) ----------

const ICON_SPEAKER = '<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 5 6 9H2v6h4l5 4V5z"/><path d="M15.5 8.5a5 5 0 0 1 0 7"/></svg>';
const ICON_STOP = '<svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><rect x="5" y="5" width="14" height="14" rx="2"/></svg>';

const ttsFab = document.getElementById("ttsFab");
const ttsModal = document.getElementById("ttsModal");
const ttsCloseBtn = document.getElementById("ttsCloseBtn");
const ttsTextarea = document.getElementById("ttsTextarea");
const ttsVoiceSelect = document.getElementById("ttsVoiceSelect");
const ttsRate = document.getElementById("ttsRate");
const ttsRateValue = document.getElementById("ttsRateValue");
const ttsPlayBtn = document.getElementById("ttsPlayBtn");
const ttsPauseBtn = document.getElementById("ttsPauseBtn");
const ttsStopBtn = document.getElementById("ttsStopBtn");

const ttsSupported = "speechSynthesis" in window;

let ttsVoices = [];
let activeListenBtn = null; // botão "Ouvir" (na mensagem) atualmente falando, se houver

ttsRate.value = localStorage.getItem("tts_rate") || "1";
ttsRateValue.textContent = `${parseFloat(ttsRate.value).toFixed(1)}x`;

function stripMarkdownForSpeech(text) {
  return text
    .replace(/```[\s\S]*?```/g, " trecho de código. ")
    .replace(/`([^`]+)`/g, "$1")
    .replace(/!\[.*?\]\(.*?\)/g, "")
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, "$1")
    .replace(/^#{1,6}\s+/gm, "")
    .replace(/(\*\*|__)(.*?)\1/g, "$2")
    .replace(/(\*|_)(.*?)\1/g, "$2")
    .replace(/^\s*[-*+]\s+/gm, "")
    .replace(/^\s*\d+\.\s+/gm, "")
    .replace(/^>\s?/gm, "")
    .replace(/\n{2,}/g, ". ")
    .trim();
}

function loadTtsVoices() {
  if (!ttsSupported) return;
  const all = window.speechSynthesis.getVoices();
  ttsVoices = all.filter((v) => v.lang.toLowerCase().startsWith("pt"));
  if (!ttsVoices.length) ttsVoices = all;
  populateVoiceSelect();
}

function populateVoiceSelect() {
  const savedURI = localStorage.getItem("tts_voice_uri");
  ttsVoiceSelect.innerHTML = "";
  ttsVoices.forEach((v) => {
    const opt = document.createElement("option");
    opt.value = v.voiceURI;
    opt.textContent = `${v.name} (${v.lang})`;
    ttsVoiceSelect.appendChild(opt);
  });
  const preferred =
    ttsVoices.find((v) => v.voiceURI === savedURI) ||
    ttsVoices.find((v) => v.lang.toLowerCase() === "pt-br") ||
    ttsVoices[0];
  if (preferred) ttsVoiceSelect.value = preferred.voiceURI;
}

function getSelectedTtsVoice() {
  return ttsVoices.find((v) => v.voiceURI === ttsVoiceSelect.value);
}

function speak(text, { onEnd } = {}) {
  if (!ttsSupported) {
    alert("Seu navegador não suporta leitura de texto em voz alta.");
    return;
  }
  const cleanText = stripMarkdownForSpeech(text);
  if (!cleanText) return;

  window.speechSynthesis.cancel();

  const utterance = new SpeechSynthesisUtterance(cleanText);
  const voice = getSelectedTtsVoice();
  if (voice) utterance.voice = voice;
  utterance.lang = voice ? voice.lang : "pt-BR";
  utterance.rate = parseFloat(ttsRate.value) || 1;

  utterance.onend = () => onEnd && onEnd();
  utterance.onerror = () => onEnd && onEnd();

  window.speechSynthesis.speak(utterance);
}

function resetListenButton(btn) {
  if (!btn) return;
  btn.innerHTML = `${ICON_SPEAKER} Ouvir`;
  btn.classList.remove("speaking");
}

function toggleListenButton(btn, content) {
  const isThisSpeaking = activeListenBtn === btn;

  if (activeListenBtn) {
    resetListenButton(activeListenBtn);
    window.speechSynthesis.cancel();
    activeListenBtn = null;
  }

  if (isThisSpeaking) return; // era esse botão: só parou

  activeListenBtn = btn;
  btn.innerHTML = `${ICON_STOP} Parar`;
  btn.classList.add("speaking");

  speak(content, {
    onEnd: () => {
      if (activeListenBtn === btn) {
        resetListenButton(btn);
        activeListenBtn = null;
      }
    },
  });
}

function resetTtsModalButtons() {
  ttsPlayBtn.disabled = false;
  ttsPauseBtn.disabled = true;
  ttsPauseBtn.textContent = "Pausar";
  ttsStopBtn.disabled = true;
}

function openTtsModal() {
  ttsModal.classList.remove("hidden");
  ttsTextarea.focus();
}

function closeTtsModal() {
  window.speechSynthesis.cancel();
  if (activeListenBtn) {
    resetListenButton(activeListenBtn);
    activeListenBtn = null;
  }
  resetTtsModalButtons();
  ttsModal.classList.add("hidden");
}

if (ttsSupported) {
  loadTtsVoices();
  window.speechSynthesis.onvoiceschanged = loadTtsVoices;
} else {
  ttsPlayBtn.disabled = true;
  ttsVoiceSelect.disabled = true;
}

ttsFab.addEventListener("click", openTtsModal);
ttsCloseBtn.addEventListener("click", closeTtsModal);
ttsModal.addEventListener("click", (e) => {
  if (e.target === ttsModal) closeTtsModal();
});

ttsVoiceSelect.addEventListener("change", () => {
  localStorage.setItem("tts_voice_uri", ttsVoiceSelect.value);
});

ttsRate.addEventListener("input", () => {
  ttsRateValue.textContent = `${parseFloat(ttsRate.value).toFixed(1)}x`;
  localStorage.setItem("tts_rate", ttsRate.value);
});

ttsPlayBtn.addEventListener("click", () => {
  const text = ttsTextarea.value.trim();
  if (!text) return;

  if (activeListenBtn) {
    resetListenButton(activeListenBtn);
    activeListenBtn = null;
  }

  ttsPlayBtn.disabled = true;
  ttsPauseBtn.disabled = false;
  ttsStopBtn.disabled = false;

  speak(text, { onEnd: resetTtsModalButtons });
});

ttsPauseBtn.addEventListener("click", () => {
  if (!ttsSupported) return;
  if (window.speechSynthesis.speaking && !window.speechSynthesis.paused) {
    window.speechSynthesis.pause();
    ttsPauseBtn.textContent = "Retomar";
  } else if (window.speechSynthesis.paused) {
    window.speechSynthesis.resume();
    ttsPauseBtn.textContent = "Pausar";
  }
});

ttsStopBtn.addEventListener("click", () => {
  window.speechSynthesis.cancel();
  resetTtsModalButtons();
});
