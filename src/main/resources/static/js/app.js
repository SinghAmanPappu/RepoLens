/* RepoLens interactive front-end: fetch (no reload) + history + toasts + markdown + highlight.
 * Falls back to normal form POST if fetch fails — the app still works without JS.
 */
(function () {
  'use strict';
  const $ = (s, r=document) => r.querySelector(s);
  const $$ = (s, r=document) => [...r.querySelectorAll(s)];
  const esc = (s) => String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const lsKey = 'repolens.history.v2';
  const lsRecent = 'repolens.recentRepos';

  function toast(msg, type='info') {
    const box = $('#toastBox'); if (!box) return;
    const el = document.createElement('div');
    el.className = 'toast-item ' + (type === 'success' ? 'success' : type === 'error' ? 'error' : '');
    el.setAttribute('role', type === 'error' ? 'alert' : 'status');
    el.textContent = msg;
    box.appendChild(el);
    setTimeout(() => { el.style.opacity = '0'; setTimeout(() => el.remove(), 250); }, 4500);
  }

  function renderMarkdown(raw, target) {
    // marked + DOMPurify + highlight.js. Fallback: escaped <pre>.
    let html = '';
    try {
      if (window.marked) {
        html = window.marked.parse(raw);
      } else { html = '<pre>' + esc(raw) + '</pre>'; }
    } catch { html = '<pre>' + esc(raw) + '</pre>'; }
    if (window.DOMPurify) { html = window.DOMPurify.sanitize(html); }
    target.innerHTML = html;
    // Highlight + copy buttons + file chips
    $$('pre code', target).forEach((code) => {
      if (window.hljs) { try { window.hljs.highlightElement(code); } catch {} }
      const pre = code.closest('pre');
      if (pre && !pre.querySelector('.copy-btn')) {
        const b = document.createElement('button');
        b.className = 'btn btn-sm btn-outline-secondary copy-btn float-end';
        b.type = 'button'; b.textContent = 'Copy';
        b.addEventListener('click', async () => {
          try { await navigator.clipboard.writeText(code.innerText); b.textContent = 'Copied';
            setTimeout(() => b.textContent = 'Copy', 1200); }
          catch { toast('Copy failed', 'error'); }
        });
        pre.prepend(b);
      }
    });
    // Auto-link file paths like src/Task3.java
    ['src/', '.java', 'pom.xml', 'application.properties'].forEach(() => {});
  }

  // ---------- health pill ----------
  async function pollHealth() {
    const pill = $('#healthPill');
    if (!pill) return;
    try {
      const r = await fetch('/api/health', { credentials: 'same-origin' });
      if (!r.ok) throw new Error('bad');
      pill.textContent = '● API up'; pill.className = 'pill ok';
    } catch { pill.textContent = '● API down'; pill.className = 'pill bad'; }
  }

  // ---------- repo validation (shared) ----------
  function normalizeRepo(raw) {
    let s = (raw || '').trim();
    s = s.replace(/^https?:\/\/(www\.)?github\.com\//i, '').replace(/\/+$/, '').replace(/\.git$/i, '');
    const parts = s.split('/');
    if (parts.length < 2 || !parts[0] || !parts[1]) return null;
    return parts[0] + '/' + parts[1];
  }

  // ---------- index: analyze (fetch, no reload) ----------
  const analyzeForm = $('#analyzeForm');
  if (analyzeForm) {
    const input = $('#url'), err = $('#urlError'), btn = $('#analyzeBtn'),
          progWrap = $('#progressWrap'), prog = $('#progressFill'), progLabel = $('#progressLabel');
    function setProgress(pct, label) {
      if (!progWrap) return;
      progWrap.classList.remove('d-none');
      prog.style.width = pct + '%';
      if (progLabel && label) progLabel.textContent = label;
    }
    analyzeForm.addEventListener('submit', async (e) => {
      const norm = normalizeRepo(input.value);
      if (!norm) {
        e.preventDefault();
        err.textContent = 'Use owner/repo, e.g. SinghAmanPappu/RepoLens';
        input.classList.add('is-invalid'); return;
      }
      // Progressive enhancement: fetch, fallback to normal POST on failure.
      e.preventDefault();
      err.textContent = ''; input.classList.remove('is-invalid');
      btn.classList.add('btn-loading'); btn.disabled = true;
      try {
        setProgress(15, 'Connecting to GitHub…');
        const fd = new FormData(); fd.append('url', input.value);
        const r = await fetch('/api/analyze', { method: 'POST', body: fd, credentials: 'same-origin' });
        if (!r.ok) throw new Error('HTTP ' + r.status);
        setProgress(70, 'Embedding code into pgvector…');
        const data = await r.json();
        setProgress(100, 'Done');
        if (!data.ok) { toast(data.message || 'Index failed', 'error'); throw new Error(data.message || 'index failed'); }
        toast(data.message || ('Indexed ' + data.repo), 'success');
        // remember recent
        try {
          const recent = JSON.parse(localStorage.getItem(lsRecent) || '[]');
          const next = [data.repo, ...recent.filter(x => x !== data.repo)].slice(0, 5);
          localStorage.setItem(lsRecent, JSON.stringify(next));
        } catch {}
        // clear old chat history so messages don't mix across repos
        try { localStorage.removeItem(lsKey); } catch {}
        // go to chat (interactive chat page) — small delay so user sees 100%
        setTimeout(() => { window.location.href = '/api/ask?repo=' + encodeURIComponent(data.repo); }, 350);
      } catch (err2) {
        toast((err2.message || 'Index failed') + ' — falling back to normal submit', 'error');
        // fallback: classic form POST (server redirects to /api/ask)
        btn.classList.remove('btn-loading'); btn.disabled = false;
        HTMLFormElement.prototype.submit.call(analyzeForm);
      } finally {
        setTimeout(() => { btn.classList.remove('btn-loading'); btn.disabled = false; }, 600);
      }
    });
    $$('.chip[data-repo]').forEach(b => b.addEventListener('click', () => { input.value = b.dataset.repo; input.focus(); }));
    // recent repos
    try {
      const recent = JSON.parse(localStorage.getItem(lsRecent) || '[]');
      const box = $('#recentBox');
      if (box && recent.length) {
        box.classList.remove('d-none');
        const list = $('#recentList');
        recent.forEach(repo => {
          const b = document.createElement('button');
          b.className = 'btn btn-sm btn-outline-secondary chip';
          b.type = 'button'; b.textContent = repo;
          b.addEventListener('click', () => { input.value = repo; input.focus(); });
          list.appendChild(b);
        });
      }
    } catch {}
  }

  // ---------- chat: fetch, append, persist ----------
  const chatForm = $('#chatForm');
  const log = $('#chatLog');
  function pushHistory(q, a) {
    try {
      const h = JSON.parse(localStorage.getItem(lsKey) || '[]');
      h.push({ q, a, t: Date.now() });
      localStorage.setItem(lsKey, JSON.stringify(h.slice(-25)));
    } catch {}
  }
  function appendMsg(q, a, animate=true) {
    if (!log) return;
    if ($('#emptyState')) $('#emptyState').remove();
    const wrap = document.createElement('div');
    wrap.className = 'row-bubble justify-content-end';
    wrap.innerHTML = '<div class="bubble user"><div class="meta">You</div><div></div></div>';
    wrap.querySelector('.bubble div:last-child').textContent = q;
    const ans = document.createElement('div');
    ans.className = 'row-bubble';
    ans.innerHTML = '<div class="avatar" aria-hidden="true">🔍</div><div class="bubble"><div class="meta">RepoLens · Senior Architect</div><div class="markdown-body"></div></div>';
    renderMarkdown(a, ans.querySelector('.markdown-body'));
    log.append(wrap, ans);
    log.scrollTop = log.scrollHeight;
  }
  function showTyping(show=true) {
    let t = $('#typingRow');
    if (!show) { t?.remove(); return; }
    if (!log || t) return;
    t = document.createElement('div');
    t.id = 'typingRow'; t.className = 'row-bubble';
    t.innerHTML = '<div class="avatar" aria-hidden="true">🔍</div><div class="bubble"><div class="typing"><span></span><span></span><span></span></div> <span class="muted small ms-2">Retrieving code…</span></div>';
    log.append(t); log.scrollTop = log.scrollHeight;
  }
  if (chatForm) {
    const input = $('#userPrompt'), btn = $('#askBtn');
    // restore history
    try {
      const h = JSON.parse(localStorage.getItem(lsKey) || '[]');
      h.forEach(m => appendMsg(m.q, m.a, false));
      if (h.length) toast(h.length + ' message(s) restored from this browser.', 'info');
    } catch {}
    chatForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const q = input.value.trim();
      if (!q) return;
      btn.classList.add('btn-loading'); btn.disabled = true;
      showTyping(true);
      try {
        const fd = new FormData(); fd.append('userPrompt', q);
        const r = await fetch('/api/chat-json', { method: 'POST', body: fd, credentials: 'same-origin' });
        if (!r.ok) throw new Error('HTTP ' + r.status);
        const data = await r.json();
        showTyping(false);
        const answer = data.ok ? (data.answer || '') : (data.answer || data.message || 'No answer.');
        appendMsg(q, answer);
        pushHistory(q, answer);
        if (!data.ok) toast('Answer had an error (see message).', 'error');
        input.value = '';
      } catch (err) {
        showTyping(false);
        toast('Chat failed: ' + err.message + ' — trying classic submit…', 'error');
        // fallback to classic POST (returns chat view with answer)
        HTMLFormElement.prototype.submit.call(chatForm);
      } finally {
        btn.classList.remove('btn-loading'); btn.disabled = false;
        input.focus();
      }
    });
    $$('.chip[data-q]').forEach(b => b.addEventListener('click', () => { input.value = b.dataset.q; input.focus(); chatForm.requestSubmit(); }));
    const clear = $('#clearBtn');
    if (clear) clear.addEventListener('click', () => {
      try { localStorage.removeItem(lsKey); } catch {}
      if (log) log.innerHTML = '';
      toast('Chat cleared.', 'success');
    });
    // Enter to send, Shift+Enter newline
    input.addEventListener('keydown', (ev) => {
      if (ev.key === 'Enter' && !ev.shiftKey) { ev.preventDefault(); chatForm.requestSubmit(); }
    });
  }

  document.addEventListener('DOMContentLoaded', pollHealth);
  setInterval(pollHealth, 30000);
  window.RepoLensToast = toast;
})();
