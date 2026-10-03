(() => {
  'use strict';

  const $ = (sel) => document.querySelector(sel);
  const state = { files: [], summary: null, sortBy: 'name', sortDir: 'asc', page: 1, pageSize: 100 };

  // ------------------------------------------------------------ formatting
  const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  function fmtSize(bytes) {
    if (bytes < 1024) return bytes + ' B';
    const units = ['KB', 'MB', 'GB', 'TB', 'PB'];
    let v = bytes, i = -1;
    while (v >= 1024 && i < units.length - 1) { v /= 1024; i++; }
    return v.toFixed(2) + ' ' + units[i];
  }

  function fmtDur(sec) {
    if (sec === null || sec === undefined) return '-';
    const t = Math.round(sec);
    const h = Math.floor(t / 3600), m = Math.floor((t % 3600) / 60), s = t % 60;
    return [h, m, s].map((n) => String(n).padStart(2, '0')).join(':');
  }

  const fmtRes = (f) => (f.width && f.height ? `${f.width} x ${f.height}` : '-');

  function fmtDate(ms) {
    const d = new Date(ms), p = (n) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
  }

  const typeLabel = { VIDEO: 'Video', IMAGE: 'Image', AUDIO: 'Audio' };

  // ----------------------------------------------------------------- alerts
  function showAlert(msg, ok = false) {
    const el = $('#alert');
    el.textContent = msg;
    el.className = 'alert' + (ok ? ' ok' : '');
    el.hidden = false;
  }
  const clearAlert = () => { $('#alert').hidden = true; };

  function setBusy(on, text) {
    const b = $('#busy');
    if (text) b.textContent = text;
    b.hidden = !on;
    $('#scanBtn').disabled = on;
    const hasData = state.summary !== null;
    $('#excelBtn').disabled = on || !hasData;
    $('#pdfBtn').disabled = on || !hasData;
  }

  // ---------------------------------------------------------------- filters
  function selectedTypes() {
    return [...document.querySelectorAll('input[name=type]:checked')].map((c) => c.value);
  }

  function buildParams() {
    const path = $('#path').value.trim();
    if (!path) { showAlert('Enter a folder path or choose one with Browse folders.'); return null; }
    const types = selectedTypes();
    if (types.length === 0) { showAlert('Select at least one file type.'); return null; }

    const p = new URLSearchParams();
    p.set('path', path);
    p.set('recursive', $('#recursive').checked);
    p.set('types', types.join(','));
    ['extensions', 'name', 'minSizeMb', 'maxSizeMb', 'minDurationMin', 'maxDurationMin'].forEach((id) => {
      const v = $('#' + id).value.trim();
      if (v !== '') p.set(id, v);
    });
    p.set('sortBy', state.sortBy);
    p.set('sortDir', state.sortDir);
    return p;
  }

  async function errorMessage(res) {
    try {
      const j = await res.json();
      if (j && j.error) return j.error;
    } catch (e) { /* not json */ }
    return `Request failed (${res.status}).`;
  }

  // ------------------------------------------------------------------- scan
  async function scan() {
    clearAlert();
    const params = buildParams();
    if (!params) return;
    state.summary = null;
    setBusy(true, 'Scanning files. Large folders can take a minute.');
    try {
      const res = await fetch('/api/scan?' + params.toString());
      if (!res.ok) throw new Error(await errorMessage(res));
      const data = await res.json();
      state.files = data.files;
      state.summary = data.summary;
      state.page = 1;
      render();
      if (data.files.length === 0) showAlert('No files matched. Try removing some filters.');
    } catch (e) {
      $('#results').hidden = true;
      showAlert(e.message);
    } finally {
      setBusy(false);
    }
  }

  // --------------------------------------------------------------- download
  async function download(kind) {
    clearAlert();
    const params = buildParams();
    if (!params) return;
    setBusy(true, `Building the ${kind === 'excel' ? 'Excel' : 'PDF'} report...`);
    try {
      const res = await fetch(`/api/report/${kind}?` + params.toString());
      if (!res.ok) throw new Error(await errorMessage(res));
      const blob = await res.blob();
      const cd = res.headers.get('Content-Disposition') || '';
      const m = /filename="?([^";]+)"?/i.exec(cd);
      const name = m ? m[1] : (kind === 'excel' ? 'media-report.xlsx' : 'media-report.pdf');
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = name;
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 5000);
      showAlert(`Downloaded ${name}`, true);
    } catch (e) {
      showAlert(e.message);
    } finally {
      setBusy(false);
    }
  }

  // ----------------------------------------------------------------- render
  function render() {
    $('#results').hidden = false;
    renderCards();
    renderTable();
    renderSummary();
  }

  function renderCards() {
    const s = state.summary;
    const by = {};
    s.byType.forEach((t) => { by[t.type] = t; });
    const v = by.VIDEO || { count: 0, sizeBytes: 0, durationSeconds: 0 };
    const i = by.IMAGE || { count: 0, sizeBytes: 0, durationSeconds: 0 };
    const a = by.AUDIO || { count: 0, sizeBytes: 0, durationSeconds: 0 };

    $('#cards').innerHTML = `
      <button type="button" class="card" data-pick="ALL">
        <div class="label">Total files</div><div class="value">${s.totalFiles}</div>
        <div class="sub">${fmtSize(s.totalSizeBytes)}</div></button>
      <button type="button" class="card video" data-pick="VIDEO">
        <div class="label">Videos</div><div class="value">${v.count}</div>
        <div class="sub">${fmtSize(v.sizeBytes)}</div></button>
      <button type="button" class="card image" data-pick="IMAGE">
        <div class="label">Images</div><div class="value">${i.count}</div>
        <div class="sub">${fmtSize(i.sizeBytes)}</div></button>
      <button type="button" class="card audio" data-pick="AUDIO">
        <div class="label">Audios</div><div class="value">${a.count}</div>
        <div class="sub">${fmtSize(a.sizeBytes)}</div></button>
      <div class="card video">
        <div class="label">Total video length</div><div class="value">${fmtDur(v.durationSeconds)}</div>
        <div class="sub">hours : minutes : seconds</div></div>
      <div class="card audio">
        <div class="label">Total audio length</div><div class="value">${fmtDur(a.durationSeconds)}</div>
        <div class="sub">hours : minutes : seconds</div></div>
      <div class="card">
        <div class="label">Total size</div><div class="value">${fmtSize(s.totalSizeBytes)}</div>
        <div class="sub">${s.totalSizeBytes.toLocaleString()} bytes</div></div>`;
  }

  function renderTable() {
    const files = state.files;
    const total = files.length;
    const totalPages = Math.max(1, Math.ceil(total / state.pageSize));
    if (state.page > totalPages) state.page = totalPages;
    const start = (state.page - 1) * state.pageSize;
    const slice = files.slice(start, start + state.pageSize);

    $('#matchInfo').textContent = `${total.toLocaleString()} matching`;

    if (slice.length === 0) {
      $('#tbody').innerHTML = '<tr><td colspan="9" class="empty">No files to show.</td></tr>';
    } else {
      $('#tbody').innerHTML = slice.map((f, idx) => `
        <tr>
          <td class="num">${start + idx + 1}</td>
          <td class="name" title="${esc(f.fullPath)}">${esc(f.name)}</td>
          <td><span class="badge ${esc(f.type)}">${esc(typeLabel[f.type] || f.type)}</span></td>
          <td>${esc(f.extension)}</td>
          <td class="num">${fmtSize(f.sizeBytes)}</td>
          <td class="num">${fmtDur(f.durationSeconds)}</td>
          <td>${fmtRes(f)}</td>
          <td>${fmtDate(f.lastModified)}</td>
          <td class="folder">${esc(f.folder)}</td>
        </tr>`).join('');
    }

    const s = state.summary;
    $('#tfoot').innerHTML = `
      <tr>
        <td colspan="4">Total (${s.totalFiles.toLocaleString()} files, all pages)</td>
        <td class="num">${fmtSize(s.totalSizeBytes)}</td>
        <td class="num">${fmtDur(s.totalDurationSeconds)}</td>
        <td colspan="3"></td>
      </tr>`;

    $('#pageInfo').textContent = `Page ${state.page} of ${totalPages}`;
    $('#prevBtn').disabled = state.page <= 1;
    $('#nextBtn').disabled = state.page >= totalPages;

    document.querySelectorAll('th[data-sort]').forEach((th) => {
      th.classList.remove('asc', 'desc');
      if (th.dataset.sort === state.sortBy) th.classList.add(state.sortDir);
    });
  }

  function renderSummary() {
    const s = state.summary;
    const rows = s.byType.map((t) => `
      <tr>
        <td>${esc(typeLabel[t.type] || t.type)}s</td>
        <td class="num">${t.count.toLocaleString()}</td>
        <td class="num">${fmtSize(t.sizeBytes)}</td>
        <td class="num">${t.type === 'IMAGE' ? '-' : fmtDur(t.durationSeconds)}</td>
      </tr>`).join('');
    $('#summaryBody').innerHTML = rows + `
      <tr>
        <td>Total</td>
        <td class="num">${s.totalFiles.toLocaleString()}</td>
        <td class="num">${fmtSize(s.totalSizeBytes)}</td>
        <td class="num">${fmtDur(s.totalDurationSeconds)}</td>
      </tr>`;
    const note = $('#unknownNote');
    if (s.unknownDurationCount > 0) {
      note.textContent = `${s.unknownDurationCount} video or audio file(s) have an unknown length and are not counted in the total. Installing FFmpeg (ffprobe) lets the app read every format.`;
      note.hidden = false;
    } else {
      note.hidden = true;
    }
  }

  // ---------------------------------------------------------------- sorting
  const sorters = {
    name: (f) => f.name.toLowerCase(),
    type: (f) => f.type,
    extension: (f) => f.extension,
    size: (f) => f.sizeBytes,
    duration: (f) => (f.durationSeconds === null ? -1 : f.durationSeconds),
    modified: (f) => f.lastModified
  };

  function sortBy(key) {
    if (state.sortBy === key) {
      state.sortDir = state.sortDir === 'asc' ? 'desc' : 'asc';
    } else {
      state.sortBy = key;
      state.sortDir = 'asc';
    }
    const get = sorters[key];
    const dir = state.sortDir === 'asc' ? 1 : -1;
    state.files.sort((a, b) => {
      const x = get(a), y = get(b);
      return (x < y ? -1 : x > y ? 1 : 0) * dir;
    });
    state.page = 1;
    renderTable();
  }

  // ------------------------------------------------------------ folder picker
  const dlg = $('#dialog');
  let dlgCurrent = null;
  let dlgParent = null;

  async function loadDir(path) {
    const q = path ? '?path=' + encodeURIComponent(path) : '';
    const res = await fetch('/api/browse' + q);
    if (!res.ok) throw new Error(await errorMessage(res));
    const d = await res.json();
    dlgCurrent = d.path;
    dlgParent = d.parent;
    $('#dlgPath').textContent = d.path;
    $('#dlgUp').disabled = !d.parent;
    const list = $('#dlgList');
    list.innerHTML = '';
    if (d.directories.length === 0) {
      list.innerHTML = '<li class="none">No subfolders here.</li>';
      return;
    }
    d.directories.forEach((name) => {
      const li = document.createElement('li');
      li.textContent = name;
      li.addEventListener('click', async () => {
        const sep = d.path.includes('\\') ? '\\' : '/';
        const next = d.path.endsWith(sep) ? d.path + name : d.path + sep + name;
        try { await loadDir(next); } catch (e) { showAlert(e.message); }
      });
      list.appendChild(li);
    });
  }

  async function openPicker() {
    dlg.showModal();
    try {
      await loadDir($('#path').value.trim());
    } catch (e) {
      try { await loadDir(''); } catch (e2) { showAlert(e2.message); }
    }
  }

  // ----------------------------------------------------------------- events
  $('#scanBtn').addEventListener('click', scan);
  $('#excelBtn').addEventListener('click', () => download('excel'));
  $('#pdfBtn').addEventListener('click', () => download('pdf'));
  $('#browseBtn').addEventListener('click', openPicker);
  $('#dlgClose').addEventListener('click', () => dlg.close());
  $('#dlgUp').addEventListener('click', async () => {
    if (dlgParent) { try { await loadDir(dlgParent); } catch (e) { showAlert(e.message); } }
  });
  $('#dlgSelect').addEventListener('click', () => {
    if (dlgCurrent) $('#path').value = dlgCurrent;
    dlg.close();
  });

  $('#path').addEventListener('keydown', (e) => { if (e.key === 'Enter') scan(); });

  $('#resetBtn').addEventListener('click', () => {
    ['extensions', 'name', 'minSizeMb', 'maxSizeMb', 'minDurationMin', 'maxDurationMin']
      .forEach((id) => { $('#' + id).value = ''; });
    document.querySelectorAll('input[name=type]').forEach((c) => { c.checked = true; });
    $('#recursive').checked = true;
    clearAlert();
  });

  $('#pageSize').addEventListener('change', (e) => {
    state.pageSize = parseInt(e.target.value, 10);
    state.page = 1;
    if (state.summary) renderTable();
  });
  $('#prevBtn').addEventListener('click', () => { state.page--; renderTable(); });
  $('#nextBtn').addEventListener('click', () => { state.page++; renderTable(); });

  document.querySelectorAll('th[data-sort]').forEach((th) => {
    th.addEventListener('click', () => { if (state.summary) sortBy(th.dataset.sort); });
  });

  // Clicking a summary card re-scans with only that type selected.
  $('#cards').addEventListener('click', (e) => {
    const card = e.target.closest('[data-pick]');
    if (!card) return;
    const pick = card.dataset.pick;
    document.querySelectorAll('input[name=type]').forEach((c) => {
      c.checked = pick === 'ALL' || c.value === pick;
    });
    scan();
  });
})();
