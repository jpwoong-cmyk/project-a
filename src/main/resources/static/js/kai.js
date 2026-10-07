(() => {
  const $ = (s, root = document) => root.querySelector(s);
  const $$ = (s, root = document) => [...root.querySelectorAll(s)];

  function showStatus(el, ok, message) {
    if (!el) return;
    el.hidden = false;
    el.className = 'status ' + (ok ? 'good' : 'bad');
    el.textContent = message;
  }

  // AI connection test. Works on setup and settings pages.
  $$('[data-ai-test]').forEach(button => button.addEventListener('click', async () => {
    const form = button.closest('form') || document;
    const status = $('[data-ai-status]', form.parentElement || document) || $('[data-ai-status]');
    const data = new URLSearchParams();
    const baseUrl = $('[name="baseUrl"]', form);
    const apiKey = $('[name="apiKey"]', form);
    const model = $('[name="model"]', form) || $('[name="modelName"]', form);
    data.set('baseUrl', baseUrl ? baseUrl.value : '');
    data.set('apiKey', apiKey ? apiKey.value : '');
    data.set('model', model ? model.value : '');
    button.disabled = true;
    const old = button.textContent;
    button.textContent = 'Testing…';
    try {
      const response = await fetch('/api/ai/test', { method: 'POST', headers: {'Content-Type':'application/x-www-form-urlencoded'}, body: data });
      const result = await response.json();
      showStatus(status, result.ok, result.message);
    } catch (e) {
      showStatus(status, false, 'Connection test failed: ' + e.message);
    } finally {
      button.disabled = false;
      button.textContent = old;
    }
  }));

  // Connector selector shared by first-run setup and the Add Source modal.
  function activateConnector(choice, root) {
    $$('.connector-choice', root).forEach(c => c.classList.toggle('active', c === choice));
    const type = choice.dataset.type;
    const installed = choice.dataset.installed === 'true';
    const hidden = $('[name="sourceType"], [name="type"]', root);
    if (hidden) hidden.value = type;
    const location = $('[name="sourceLocation"], [name="location"]', root);
    const locationLabel = $('[data-location-label]', root);
    if (location) location.placeholder = choice.dataset.placeholder || '';
    if (locationLabel) locationLabel.textContent = choice.dataset.locationLabel || 'Location';
    $$('[data-provider-field]', root).forEach(el => {
      const types = (el.dataset.providerField || '').split(',');
      el.hidden = !types.includes(type);
    });
    const note = $('[data-connector-note]', root);
    if (note) {
      note.hidden = installed;
      note.textContent = installed ? '' : 'This connector shell is included, but its provider adapter is not installed in this build yet.';
    }
  }

  $$('[data-connector-root]').forEach(root => {
    $$('.connector-choice', root).forEach(choice => choice.addEventListener('click', () => activateConnector(choice, root)));
    const initial = $('.connector-choice.active', root) || $('.connector-choice[data-installed="true"]', root) || $('.connector-choice', root);
    if (initial) activateConnector(initial, root);
  });

  // Source connection test.
  $$('[data-source-test]').forEach(button => button.addEventListener('click', async () => {
    const form = button.closest('form');
    const status = $('[data-source-status]', form);
    const data = new URLSearchParams();
    ['type','location','name'].forEach(n => {
      const el = $('[name="' + n + '"]', form);
      if (el) data.set(n, el.value);
    });
    const setupType = $('[name="sourceType"]', form);
    const setupLoc = $('[name="sourceLocation"]', form);
    const setupName = $('[name="sourceName"]', form);
    if (setupType) data.set('type', setupType.value);
    if (setupLoc) data.set('location', setupLoc.value);
    if (setupName) data.set('name', setupName.value);
    button.disabled = true;
    const old = button.textContent;
    button.textContent = 'Testing…';
    try {
      const response = await fetch('/api/source/test', { method: 'POST', headers: {'Content-Type':'application/x-www-form-urlencoded'}, body: data });
      const result = await response.json();
      showStatus(status, result.ok, result.message);
    } catch (e) {
      showStatus(status, false, 'Source test failed: ' + e.message);
    } finally {
      button.disabled = false;
      button.textContent = old;
    }
  }));

  // Modal handling.
  $$('[data-open-modal]').forEach(button => button.addEventListener('click', () => {
    const modal = document.getElementById(button.dataset.openModal);
    if (modal) modal.classList.add('open');
  }));
  $$('[data-close-modal]').forEach(button => button.addEventListener('click', () => {
    button.closest('.modal-backdrop')?.classList.remove('open');
  }));
  $$('.modal-backdrop').forEach(backdrop => backdrop.addEventListener('click', e => {
    if (e.target === backdrop) backdrop.classList.remove('open');
  }));

  // First-run wizard.
  const setup = $('[data-setup]');
  if (setup) {
    let step = 0;
    const steps = $$('.setup-step', setup);
    const dots = $$('.step-dot', setup);
    const show = n => {
      step = Math.max(0, Math.min(n, steps.length - 1));
      steps.forEach((s, i) => s.classList.toggle('active', i === step));
      dots.forEach((d, i) => {
        d.classList.toggle('active', i === step);
        d.classList.toggle('done', i < step);
      });
      window.scrollTo({top: 0, behavior: 'smooth'});
    };
    $$('[data-next]', setup).forEach(b => b.addEventListener('click', () => show(step + 1)));
    $$('[data-back]', setup).forEach(b => b.addEventListener('click', () => show(step - 1)));
    show(0);
  }

  // Impact examples.
  const request = $('#change-request');
  $$('[data-example]').forEach(b => b.addEventListener('click', () => {
    if (request) { request.value = b.dataset.example || b.textContent.trim(); request.focus(); }
  }));

  // Impact submit/finalize feedback.
  const scanForm = $('#impact-form');
  if (scanForm) scanForm.addEventListener('submit', () => {
    const b = $('button[type="submit"]', scanForm);
    if (b) { b.disabled = true; b.textContent = 'Starting analysis…'; }
  });
  $$('[data-finalize]').forEach(b => b.addEventListener('click', e => {
    if (!confirm('Apply every selected change now? KAI will back up originals first.')) {
      e.preventDefault();
      return;
    }
    setTimeout(() => { b.disabled = true; b.textContent = 'Applying…'; }, 0);
  }));

  // Live agent progress.
  const log = $('#agent-log');
  if (log) {
    const poll = async () => {
      try {
        const response = await fetch(log.dataset.url + '?since=' + log.children.length);
        const payload = await response.json();
        payload.lines.forEach(line => {
          const div = document.createElement('div');
          div.textContent = line;
          log.appendChild(div);
        });
        log.scrollTop = log.scrollHeight;
        if (payload.done) location.href = '/impact';
        else setTimeout(poll, 900);
      } catch (_) {
        setTimeout(poll, 2500);
      }
    };
    setTimeout(poll, 900);
  }
})();
