(function () {
  'use strict';
  var API = '/api/v1';
  var ROLE_ORDER = ['guest', 'user', 'premium', 'moderator', 'editor', 'admin', 'super_admin'];
  var state = { access: null, refresh: null, me: null, roles: [], page: 'dashboard' };

  // ---------- helpers (text is always set through text nodes, never parsed as markup) ----------
  function h(tag, attrs) {
    var el = document.createElement(tag);
    var i = 2, k;
    attrs = attrs || {};
    for (k in attrs) {
      if (k === 'class') el.className = attrs[k];
      else if (k.slice(0, 2) === 'on') el.addEventListener(k.slice(2), attrs[k]);
      else if (attrs[k] !== false && attrs[k] != null) el.setAttribute(k, attrs[k]);
    }
    for (; i < arguments.length; i++) add(el, arguments[i]);
    return el;
  }
  function add(el, c) {
    if (c == null || c === false) return;
    if (Array.isArray(c)) { c.forEach(function (x) { add(el, x); }); return; }
    el.appendChild(c.nodeType ? c : document.createTextNode(String(c)));
  }
  function $(id) { return document.getElementById(id); }
  function rank(role) { return Math.max(0, ROLE_ORDER.indexOf(role)); }
  function myRank() { return state.roles.reduce(function (m, r) { return Math.max(m, rank(r)); }, 0); }
  function can(min) { return myRank() >= rank(min); }
  function toast(msg) {
    var t = $('toast'); t.textContent = msg; t.classList.add('show');
    clearTimeout(toast.t); toast.t = setTimeout(function () { t.classList.remove('show'); }, 3500);
  }
  function qs(o) {
    var p = [];
    Object.keys(o).forEach(function (k) { if (o[k] !== '' && o[k] != null) p.push(encodeURIComponent(k) + '=' + encodeURIComponent(o[k])); });
    return p.length ? '?' + p.join('&') : '';
  }
  function fmt(s) { if (!s) return ''; var d = new Date(s); return isNaN(d) ? String(s) : d.toLocaleString(); }
  function pill(t) { return h('span', { class: 'pill' }, t); }
  function table(cols, rows) {
    return h('div', { class: 'tablewrap' }, h('table', {},
      h('thead', {}, h('tr', {}, cols.map(function (c) { return h('th', { scope: 'col' }, c); }))),
      h('tbody', {}, rows.length ? rows : h('tr', {}, h('td', { colspan: cols.length, class: 'muted' }, 'Nothing here.')))));
  }
  function errText(e) { return (e && e.message) || 'Something went wrong.'; }

  // ---------- API with one token refresh ----------
  function saveTokens(a, r) {
    state.access = a; state.refresh = r;
    try { sessionStorage.setItem('eb_admin_tokens', JSON.stringify({ a: a, r: r })); } catch (e) { /* ignore */ }
  }
  function loadTokens() {
    try { var t = JSON.parse(sessionStorage.getItem('eb_admin_tokens') || 'null'); if (t) { state.access = t.a; state.refresh = t.r; } } catch (e) { /* ignore */ }
  }
  function clearTokens() { state.access = state.refresh = null; try { sessionStorage.removeItem('eb_admin_tokens'); } catch (e) { /* ignore */ } }
  async function raw(method, path, body, token) {
    var res;
    try {
      res = await fetch(API + path, {
        method: method,
        headers: Object.assign({ Accept: 'application/json' }, body !== undefined ? { 'Content-Type': 'application/json' } : {}, token ? { Authorization: 'Bearer ' + token } : {}),
        body: body !== undefined ? JSON.stringify(body) : undefined
      });
    } catch (e) { var ne = new Error('Cannot reach the server. Check your connection.'); ne.status = 0; throw ne; }
    var data = null;
    try { data = await res.json(); } catch (e) { /* empty body */ }
    if (!res.ok) {
      var err = new Error((data && data.error && data.error.message) || ('Request failed (' + res.status + ').'));
      err.status = res.status; err.code = data && data.error && data.error.code; err.fields = data && data.error && data.error.fields;
      throw err;
    }
    return data;
  }
  async function api(method, path, body) {
    try { return await raw(method, path, body, state.access); }
    catch (e) {
      if (e.status !== 401 || !state.refresh) throw e;
      try {
        var r = await raw('POST', '/auth/refresh', { refreshToken: state.refresh });
        saveTokens(r.accessToken, r.refreshToken);
      } catch (e2) { signOut(); throw e; }
      return raw(method, path, body, state.access);
    }
  }

  // ---------- auth ----------
  async function startSession(auth) {
    saveTokens(auth.accessToken, auth.refreshToken);
    state.me = auth.user;
    // The server decides who is staff: this call needs moderator or higher.
    var u = await api('GET', '/admin/users/' + encodeURIComponent(auth.user.id));
    state.roles = u.roles || [];
    if (myRank() < rank('moderator')) { var e = new Error('This account has no admin access.'); e.status = 403; throw e; }
    $('login').classList.add('hidden'); $('app').classList.remove('hidden');
    $('who').textContent = auth.user.email + ' (' + state.roles.join(', ') + ')';
    buildNav(); go(location.hash.slice(1) || 'dashboard');
  }
  function signOut() {
    var rt = state.refresh;
    if (rt && state.access) { raw('POST', '/auth/logout', { refreshToken: rt }, state.access).catch(function () { }); }
    clearTokens(); state.me = null; state.roles = [];
    $('app').classList.add('hidden'); $('login').classList.remove('hidden');
  }
  async function restore() {
    loadTokens();
    if (!state.access) return showLogin();
    try {
      var me = await api('GET', '/users/me');
      state.me = me;
      var u = await api('GET', '/admin/users/' + encodeURIComponent(me.id));
      state.roles = u.roles || [];
      if (myRank() < rank('moderator')) throw new Error('no access');
      $('login').classList.add('hidden'); $('app').classList.remove('hidden');
      $('who').textContent = me.email + ' (' + state.roles.join(', ') + ')';
      buildNav(); go(location.hash.slice(1) || 'dashboard');
    } catch (e) { clearTokens(); showLogin(); }
  }
  function showLogin() { $('app').classList.add('hidden'); $('login').classList.remove('hidden'); }

  // ---------- pages ----------
  var PAGES = [
    { id: 'dashboard', label: 'Dashboard', min: 'moderator', render: pDashboard },
    { id: 'users', label: 'Users', min: 'moderator', render: pUsers },
    { id: 'content', label: 'Content', min: 'editor', render: pContent },
    { id: 'questions', label: 'Questions', min: 'editor', render: pQuestions },
    { id: 'imports', label: 'Imports', min: 'editor', render: pImports },
    { id: 'contests', label: 'Contests', min: 'editor', render: pContests },
    { id: 'exams', label: 'Exams', min: 'editor', render: pExams },
    { id: 'reports', label: 'Reports', min: 'moderator', render: pReports },
    { id: 'flags', label: 'Feature flags', min: 'admin', render: pFlags },
    { id: 'config', label: 'App config', min: 'admin', render: pConfig },
    { id: 'audit', label: 'Audit logs', min: 'admin', render: pAudit },
    { id: 'later', label: 'Banners, notifications, AI usage', min: 'moderator', render: pLater }
  ];
  function buildNav() {
    var nav = $('nav'); nav.textContent = '';
    PAGES.filter(function (p) { return can(p.min); }).forEach(function (p) {
      nav.appendChild(h('button', { type: 'button', 'data-page': p.id, onclick: function () { go(p.id); $('nav').classList.remove('open'); } }, p.label));
    });
  }
  function go(id) {
    var page = PAGES.filter(function (p) { return p.id === id && can(p.min); })[0] || PAGES[0];
    state.page = page.id; location.hash = page.id;
    Array.prototype.forEach.call($('nav').children, function (b) {
      if (b.getAttribute('data-page') === page.id) b.setAttribute('aria-current', 'page'); else b.removeAttribute('aria-current');
    });
    var main = $('main'); main.textContent = '';
    main.appendChild(h('h2', {}, page.label));
    var body = h('div', {}, h('p', { class: 'muted' }, 'Loading...'));
    main.appendChild(body); main.focus();
    Promise.resolve().then(function () { return page.render(body); }).catch(function (e) { fail(body, e, function () { go(page.id); }); });
  }
  function fail(body, e, retry) {
    body.textContent = '';
    body.appendChild(h('div', { class: 'card' },
      h('p', { class: 'error', role: 'alert' }, e.status === 403 ? 'Your role does not allow this.' : errText(e)),
      h('button', { type: 'button', onclick: retry }, 'Try again')));
  }
  async function act(fn, ok) {
    try { await fn(); if (ok) toast(ok); return true; } catch (e) { toast(errText(e)); return false; }
  }

  // Dashboard
  async function pDashboard(body) {
    var jobs = {
      subjects: can('editor') ? api('GET', '/admin/subjects?limit=200') : null,
      reports: api('GET', '/admin/abuse-reports?status=open&limit=50'),
      contests: can('editor') ? api('GET', '/admin/contests') : null,
      exams: can('editor') ? api('GET', '/admin/exams') : null,
      flags: can('admin') ? api('GET', '/admin/feature-flags') : null
    };
    var keys = Object.keys(jobs), vals = await Promise.all(keys.map(function (k) { return jobs[k]; })), r = {};
    keys.forEach(function (k, i) { r[k] = vals[i]; });
    function kpi(label, v) { return h('div', { class: 'card' }, h('div', { class: 'muted' }, label), h('div', { class: 'kpi' }, v == null ? '-' : v)); }
    body.textContent = '';
    body.appendChild(h('div', { class: 'grid' },
      kpi('Open abuse reports', r.reports.items.length + (r.reports.nextCursor ? '+' : '')),
      kpi('Subjects (max 200 shown)', r.subjects && r.subjects.items.length),
      kpi('Contests', r.contests && r.contests.items.length),
      kpi('Exams', r.exams && r.exams.items.length),
      kpi('Feature flags on', r.flags && r.flags.filter(function (f) { return f.enabled; }).length)));
    body.appendChild(h('p', { class: 'muted' }, 'A dash means your role cannot see that number. User totals, AI usage and cost need backend endpoints that do not exist yet.'));
  }

  // Users
  async function pUsers(body) {
    var q = h('input', { type: 'search', 'aria-label': 'Search users' });
    var st = h('select', { 'aria-label': 'Status' }, ['', 'active', 'suspended'].map(function (s) { return h('option', { value: s }, s || 'Any status'); }));
    var list = h('div'), cursor = null;
    function rowFor(u) {
      var acts = [];
      if (can('admin')) {
        acts.push(h('button', { type: 'button', onclick: async function () {
          var next = u.status === 'active' ? 'suspended' : 'active', reason = next === 'suspended' ? prompt('Reason for suspending ' + u.email + '?') : '';
          if (reason === null) return;
          if (await act(function () { return api('PATCH', '/admin/users/' + u.id + '/status', { status: next, reason: reason || null }); }, 'Status set to ' + next)) load(false).catch(function (e) { toast(errText(e)); });
        } }, u.status === 'active' ? 'Suspend' : 'Reactivate'));
        acts.push(h('button', { type: 'button', onclick: async function () {
          var v = prompt('Roles for ' + u.email + ' (comma separated):', u.roles.join(', '));
          if (v === null) return;
          var roles = v.split(',').map(function (s) { return s.trim(); }).filter(Boolean);
          if (await act(function () { return api('PUT', '/admin/users/' + u.id + '/roles', { roles: roles }); }, 'Roles saved')) load(false).catch(function (e) { toast(errText(e)); });
        } }, 'Roles'));
      }
      return h('tr', {}, h('td', {}, u.email), h('td', {}, u.displayName), h('td', {}, pill(u.status)), h('td', {}, u.roles.join(', ')), h('td', {}, fmt(u.createdAt)), h('td', {}, acts));
    }
    async function load(more) {
      var d = await api('GET', '/admin/users' + qs({ q: q.value.trim(), status: st.value, limit: 20, cursor: more ? cursor : null }));
      cursor = d.nextCursor;
      var rows = d.items.map(rowFor);
      if (!more) { list.textContent = ''; list.appendChild(table(['Email', 'Name', 'Status', 'Roles', 'Joined', ''], rows)); }
      else { var tb = list.querySelector('tbody'); rows.forEach(function (r) { tb.appendChild(r); }); }
      var old = list.querySelector('.more'); if (old) old.remove();
      if (cursor) list.appendChild(h('button', { type: 'button', class: 'more', onclick: function () { load(true).catch(function (e) { toast(errText(e)); }); } }, 'Load more'));
    }
    body.textContent = '';
    body.appendChild(h('div', { class: 'row' }, h('label', {}, 'Search', q), h('label', {}, 'Status', st), h('button', { type: 'button', class: 'primary', onclick: function () { load(false).catch(function (e) { toast(errText(e)); }); } }, 'Search')));
    body.appendChild(list);
    await load(false);
  }

  // Content
  async function pContent(body) {
    var trail = [], holder = h('div');
    var title = h('input', { 'aria-label': 'New subject title' });
    async function show(kind, id, label) {
      holder.textContent = '';
      var d = id ? await api('GET', '/admin/content/' + kind + '/' + id + '/children') : await api('GET', '/admin/subjects?limit=200');
      var crumbs = h('p', {}, h('button', { type: 'button', onclick: function () { trail = []; show().catch(function (e) { toast(errText(e)); }); } }, 'All subjects'), trail.map(function (t) { return ' / ' + t; }).join(''));
      var rows = d.items.map(function (n) {
        var childKind = { subject: 'subject', category: 'category', subcategory: 'subcategory' }[n.kind];
        return h('tr', {}, h('td', {}, n.kind), h('td', {}, n.title + (n.titleBn ? ' / ' + n.titleBn : '')), h('td', {}, pill(n.status)), h('td', {}, fmt(n.updatedAt)),
          h('td', {}, [
            n.kind !== 'lesson' ? h('button', { type: 'button', onclick: function () { trail.push(n.title); show(childKind, n.id).catch(function (e) { toast(errText(e)); }); } }, 'Open') : null,
            h('button', { type: 'button', onclick: async function () {
              var next = n.status === 'published' ? 'draft' : 'published';
              if (await act(function () { return api('PATCH', '/admin/content/' + n.kind + '/' + n.id + '/status', { status: next }); }, n.title + ' is now ' + next)) show(kind, id).catch(function (e) { toast(errText(e)); });
            } }, n.status === 'published' ? 'Unpublish' : 'Publish')
          ]));
      });
      holder.appendChild(crumbs); holder.appendChild(table(['Type', 'Title', 'Status', 'Updated', ''], rows));
    }
    body.textContent = '';
    body.appendChild(h('div', { class: 'card' }, h('div', { class: 'row' }, h('label', {}, 'New subject (saved as draft)', title),
      h('button', { type: 'button', class: 'primary', onclick: async function () {
        if (!title.value.trim()) return toast('Enter a title first.');
        if (await act(function () { return api('POST', '/admin/subjects', { title: title.value.trim(), status: 'draft' }); }, 'Subject created')) { title.value = ''; trail = []; show().catch(function (e) { toast(errText(e)); }); }
      } }, 'Create'))));
    body.appendChild(holder);
    await show();
  }

  // Questions
  async function pQuestions(body) {
    var lesson = h('input', { 'aria-label': 'Lesson id' });
    var st = h('select', { 'aria-label': 'Status' }, ['', 'draft', 'review', 'published', 'archived'].map(function (s) { return h('option', { value: s }, s || 'Any status'); }));
    var list = h('div');
    async function load() {
      var d = await api('GET', '/admin/questions' + qs({ lessonId: lesson.value.trim(), status: st.value, limit: 50 }));
      var rows = d.items.map(function (q) {
        var buttons = [];
        ['draft', 'review', 'published', 'archived'].forEach(function (s) {
          if (s === q.status) return;
          if ((s === 'published' || s === 'archived') && !can('moderator')) return;
          buttons.push(h('button', { type: 'button', onclick: async function () {
            var note = (s === 'draft' || s === 'archived') ? prompt('Review note (optional):') : '';
            if (note === null) return;
            if (await act(function () { return api('PATCH', '/admin/questions/' + q.id + '/status', { status: s, reviewNote: note || null }); }, 'Moved to ' + s)) load().catch(function (e) { toast(errText(e)); });
          } }, s));
        });
        return h('tr', {}, h('td', {}, q.questionText), h('td', {}, q.type), h('td', {}, pill(q.status)), h('td', {}, q.difficulty),
          h('td', {}, h('ul', {}, q.options.map(function (o) { return h('li', {}, (o.isCorrect ? '\u2713 ' : '') + o.text); }))), h('td', {}, buttons));
      });
      list.textContent = ''; list.appendChild(table(['Question', 'Type', 'Status', 'Difficulty', 'Options (\u2713 = correct)', 'Move to'], rows));
      if (d.nextCursor) list.appendChild(h('p', { class: 'muted' }, 'More questions exist; narrow the filter.'));
    }
    body.textContent = '';
    body.appendChild(h('div', { class: 'row' }, h('label', {}, 'Lesson id', lesson), h('label', {}, 'Status', st), h('button', { type: 'button', class: 'primary', onclick: function () { load().catch(function (e) { toast(errText(e)); }); } }, 'Filter')));
    body.appendChild(list);
    await load();
  }

  // Imports
  async function pImports(body) {
    var ta = h('textarea', { 'aria-label': 'Questions JSON', spellcheck: 'false' });
    ta.value = '{"questions":[{"lessonId":"","type":"single_choice","questionText":"","options":[{"text":"","isCorrect":true},{"text":"","isCorrect":false}]}]}';
    var out = h('div');
    body.textContent = '';
    body.appendChild(h('div', { class: 'card' },
      h('p', {}, 'Paste JSON: {"questions":[...]} with at most 200 questions. Imported questions start as drafts unless you set a status.'),
      ta, h('button', { type: 'button', class: 'primary', onclick: async function () {
        var parsed; try { parsed = JSON.parse(ta.value); } catch (e) { return toast('That is not valid JSON.'); }
        try {
          var r = await api('POST', '/admin/questions/import', parsed);
          out.textContent = '';
          out.appendChild(h('div', { class: 'card' }, h('p', {}, 'Batch ' + r.batchId + ': ' + r.status + '. ' + r.successCount + ' of ' + r.totalRows + ' imported, ' + r.errorCount + ' failed.'),
            r.errors.length ? h('ul', {}, r.errors.map(function (m) { return h('li', {}, m); })) : null));
        } catch (e) { toast(errText(e)); }
      } }, 'Import'), out));
  }

  // Contests / exams
  function statusPage(path, cols, rowFn) {
    return async function (body) {
      var d = await api('GET', path);
      body.textContent = '';
      body.appendChild(table(cols, d.items.map(function (it) { return rowFn(it, function () { go(state.page); }); })));
      if (!can('admin')) body.appendChild(h('p', { class: 'muted' }, 'Changing status needs the admin role.'));
    };
  }
  function statusButton(kind, it, refresh) {
    if (!can('admin')) return null;
    return h('button', { type: 'button', onclick: async function () {
      var s = prompt('New status for "' + it.title + '":', it.status);
      if (!s) return;
      if (await act(function () { return api('PATCH', '/admin/' + kind + '/' + it.id + '/status', { status: s.trim() }); }, 'Status updated')) refresh();
    } }, 'Change status');
  }
  var pContests = statusPage('/admin/contests', ['Title', 'Status', 'Starts', 'Ends', 'Participants', ''], function (c, r) {
    return h('tr', {}, h('td', {}, c.title), h('td', {}, pill(c.status)), h('td', {}, fmt(c.startsAt)), h('td', {}, fmt(c.endsAt)), h('td', {}, c.participantCount), h('td', {}, statusButton('contests', c, r)));
  });
  var pExams = statusPage('/admin/exams', ['Title', 'Status', 'Opens', 'Closes', ''], function (x, r) {
    return h('tr', {}, h('td', {}, x.title), h('td', {}, pill(x.status)), h('td', {}, fmt(x.opensAt)), h('td', {}, fmt(x.closesAt)), h('td', {}, statusButton('exams', x, r)));
  });

  // Reports
  async function pReports(body) {
    var st = h('select', { 'aria-label': 'Status' }, ['open', 'reviewing', 'resolved', 'dismissed', ''].map(function (s) { return h('option', { value: s }, s || 'Any'); }));
    var list = h('div');
    async function load() {
      var d = await api('GET', '/admin/abuse-reports' + qs({ status: st.value, limit: 50 }));
      list.textContent = '';
      list.appendChild(table(['Target', 'Reason', 'Details', 'Status', 'Filed', ''], d.items.map(function (r) {
        function set(s) { return h('button', { type: 'button', onclick: async function () {
          var note = prompt('Note (optional):'); if (note === null) return;
          if (await act(function () { return api('PATCH', '/admin/abuse-reports/' + r.id, { status: s, resolutionNote: note || null }); }, 'Report ' + s)) load().catch(function (e) { toast(errText(e)); });
        } }, s); }
        return h('tr', {}, h('td', {}, r.targetType + ' ' + r.targetId), h('td', {}, r.reason), h('td', {}, r.details || ''), h('td', {}, pill(r.status)), h('td', {}, fmt(r.createdAt)),
          h('td', {}, ['reviewing', 'resolved', 'dismissed'].filter(function (s) { return s !== r.status; }).map(set)));
      })));
    }
    body.textContent = '';
    body.appendChild(h('div', { class: 'row' }, h('label', {}, 'Status', st), h('button', { type: 'button', class: 'primary', onclick: function () { load().catch(function (e) { toast(errText(e)); }); } }, 'Filter')));
    body.appendChild(list); await load();
  }

  // Flags / config
  async function pFlags(body) {
    var d = await api('GET', '/admin/feature-flags');
    body.textContent = '';
    body.appendChild(table(['Key', 'Description', 'Enabled', ''], d.map(function (f) {
      return h('tr', {}, h('td', {}, f.key), h('td', {}, f.description), h('td', {}, pill(f.enabled ? 'on' : 'off')),
        h('td', {}, can('super_admin') ? h('button', { type: 'button', onclick: async function () {
          if (await act(function () { return api('PUT', '/admin/feature-flags/' + encodeURIComponent(f.key), { enabled: !f.enabled }); }, f.key + (f.enabled ? ' turned off' : ' turned on'))) go('flags');
        } }, f.enabled ? 'Turn off' : 'Turn on') : null));
    })));
    if (!can('super_admin')) body.appendChild(h('p', { class: 'muted' }, 'Changing flags needs the super_admin role.'));
  }
  async function pConfig(body) {
    var d = await api('GET', '/admin/config');
    body.textContent = '';
    body.appendChild(table(['Key', 'Value (JSON)', 'Description', ''], d.map(function (c) {
      return h('tr', {}, h('td', {}, c.key), h('td', {}, JSON.stringify(c.value)), h('td', {}, c.description),
        h('td', {}, h('button', { type: 'button', onclick: async function () {
          var v = prompt('New JSON value for ' + c.key + ':', JSON.stringify(c.value)); if (v === null) return;
          var parsed; try { parsed = JSON.parse(v); } catch (e) { return toast('That is not valid JSON.'); }
          if (await act(function () { return api('PUT', '/admin/config/' + encodeURIComponent(c.key), { value: parsed }); }, 'Saved')) go('config');
        } }, 'Edit')));
    })));
  }

  // Audit
  async function pAudit(body) {
    var rt = h('input', { 'aria-label': 'Resource type' }), ac = h('input', { 'aria-label': 'Action' }), list = h('div'), cursor = null;
    async function load(more) {
      var d = await api('GET', '/admin/audit-logs' + qs({ resourceType: rt.value.trim(), action: ac.value.trim(), limit: 25, cursor: more ? cursor : null }));
      cursor = d.nextCursor;
      var rows = d.items.map(function (a) { return h('tr', {}, h('td', {}, fmt(a.createdAt)), h('td', {}, a.actorId || ''), h('td', {}, a.action), h('td', {}, a.resourceType + (a.resourceId ? ' ' + a.resourceId : ''))); });
      if (!more) { list.textContent = ''; list.appendChild(table(['When', 'Actor', 'Action', 'Resource'], rows)); }
      else { var tb = list.querySelector('tbody'); rows.forEach(function (r) { tb.appendChild(r); }); }
      var old = list.querySelector('.more'); if (old) old.remove();
      if (cursor) list.appendChild(h('button', { type: 'button', class: 'more', onclick: function () { load(true).catch(function (e) { toast(errText(e)); }); } }, 'Load more'));
    }
    body.textContent = '';
    body.appendChild(h('div', { class: 'row' }, h('label', {}, 'Resource type', rt), h('label', {}, 'Action', ac), h('button', { type: 'button', class: 'primary', onclick: function () { load(false).catch(function (e) { toast(errText(e)); }); } }, 'Filter')));
    body.appendChild(list); await load(false);
  }

  function pLater(body) {
    body.textContent = '';
    body.appendChild(h('div', { class: 'card' }, h('p', {}, 'These sections need backend modules that are not built yet, so there is nothing to show or change:'),
      h('ul', {}, h('li', {}, 'AI usage and cost: the AI gateway (Step 32) records usage, but there is no admin endpoint to read it yet (queue row 32b).'), h('li', {}, 'Banners and notifications: Step 34.')),
      h('p', { class: 'muted' }, 'Admin MFA also needs a backend step (not in the plan yet).')));
  }

  // ---------- boot ----------
  document.addEventListener('DOMContentLoaded', function () {
    $('loginForm').addEventListener('submit', async function (ev) {
      ev.preventDefault(); $('loginError').textContent = '';
      try {
        var auth = await raw('POST', '/auth/login', { email: $('email').value.trim(), password: $('password').value, deviceName: 'admin-web' });
        $('password').value = '';
        await startSession(auth);
      } catch (e) { clearTokens(); $('loginError').textContent = errText(e); }
    });
    $('logoutBtn').addEventListener('click', signOut);
    $('menuBtn').addEventListener('click', function () { $('nav').classList.toggle('open'); });
    window.addEventListener('hashchange', function () { if (state.me && location.hash.slice(1) !== state.page) go(location.hash.slice(1)); });
    restore();
  });
})();
