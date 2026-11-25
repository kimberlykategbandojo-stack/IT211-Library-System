/* ============================================================
   app.js - Online Library System
   Browser-side logic. Talks to the Java backend over HTTP.
   All business rules are enforced by Java - this file only
   handles UI, validation, and request/response display.
   ============================================================ */

(function () {
  'use strict';

  // ---------------------- State ---------------------------------------
  const state = {
    token: localStorage.getItem('ols_token') || null,
    student: JSON.parse(localStorage.getItem('ols_student') || 'null'),
    books: [],
    categories: [],
    confirmCallback: null,
    returnCallback: null,
  };

  // ---------------------- Helpers -------------------------------------
  const $  = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));

  function setToken(token, student) {
    state.token = token;
    state.student = student;
    if (token) {
      localStorage.setItem('ols_token', token);
      localStorage.setItem('ols_student', JSON.stringify(student));
    } else {
      localStorage.removeItem('ols_token');
      localStorage.removeItem('ols_student');
    }
    refreshProfileUI();
  }

  function authHeaders() {
    const h = { 'Content-Type': 'application/x-www-form-urlencoded' };
    if (state.token) h['X-Session-Token'] = state.token;
    return h;
  }

  async function api(path, options = {}) {
    const opts = Object.assign({
      headers: Object.assign({}, authHeaders(), options.headers || {}),
    }, options);
    if (opts.body && typeof opts.body === 'object') {
      // Encode as form-urlencoded
      opts.body = Object.entries(opts.body)
        .map(([k, v]) => encodeURIComponent(k) + '=' + encodeURIComponent(String(v)))
        .join('&');
    }
    let res;
    try {
      res = await fetch(path, opts);
    } catch (error) {
      // Keep form handlers on their normal error path when the server is
      // stopped or the browser cannot reach it (instead of leaving buttons
      // stuck in their loading state after an unhandled rejection).
      return {
        ok: false,
        status: 0,
        data: { message: 'Cannot connect to the library server. Start the Java server and open this page at http://localhost:2350.' },
      };
    }
    let data = null;
    try { data = await res.json(); } catch (e) { /* ignore */ }
    return { ok: res.ok, status: res.status, data };
  }

  function toast(message, kind = 'info', timeout = 3200) {
    const el = $('#toast');
    el.className = 'toast ' + kind;
    el.textContent = message;
    el.classList.remove('hidden');
    clearTimeout(toast._t);
    toast._t = setTimeout(() => el.classList.add('hidden'), timeout);
  }

  // ---------------------- Validation helpers --------------------------
  /** Returns true if s is exactly 8 digits. */
  function isValidStudentId(s) {
    return typeof s === 'string' && /^\d{8}$/.test(s.trim());
  }
  /** Returns true if s contains at least 2 word-parts, each starting
   *  with a letter (so a surname is guaranteed present). */
  function isValidFullName(s) {
    if (!s) return false;
    const parts = s.trim().split(/\s+/);
    if (parts.length < 2) return false;
    return parts.every(p => /^[A-Za-z][A-Za-z'-]*$/.test(p));
  }
  function isValidPhone(s) {
    if (!s) return false;
    // Allow digits, spaces, +, -, parentheses; require at least 7 digits.
    const digits = s.replace(/[^\d]/g, '');
    return digits.length >= 7 && /^[\d+()\-\s]+$/.test(s);
  }

  function showView(name) {
    // If the user is not signed in, the ONLY view they may see is the auth view.
    // This enforces the workflow: Sign In -> Browse -> Details -> Borrow/Reserve -> Receipt.
    if (!state.student && name !== 'auth') {
      name = 'auth';
    }
    $$('.view').forEach(v => v.classList.add('hidden'));
    const el = $('#view-' + name);
    if (el) el.classList.remove('hidden');
    $$('.nav-link').forEach(l => l.classList.toggle('active', l.dataset.view === name));
    // Refresh data when entering views that need it (only if logged in)
    if (state.student) {
      if (name === 'browse')      loadCatalog();
      if (name === 'borrowed')    loadBorrowed();
      if (name === 'reservations') loadReservations();
      if (name === 'history')     loadHistory();
      if (name === 'receipts')     loadReceipts();
      if (name === 'penalties')    loadPenalties();
      if (name === 'profile-details') loadProfileDetails();
    }
    window.scrollTo({ top: 0, behavior: 'instant' });
  }

  function escapeHtml(s) {
    if (s == null) return '';
    return String(s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  // ---------------------- Profile UI ----------------------------------
  function refreshProfileUI() {
    const avatar = $('#profileAvatar');
    const name = $('#profileName');
    const ddName = $('#ddName');
    const ddId = $('#ddId');
    if (state.student) {
      const initials = (state.student.fullName || '?').split(/\s+/)
        .slice(0, 2).map(w => w.charAt(0).toUpperCase()).join('') || '?';
      avatar.textContent = initials;
      name.textContent = state.student.fullName;
      ddName.textContent = state.student.fullName;
      ddId.textContent = 'ID: ' + state.student.studentId;
      document.body.classList.remove('logged-out');
    } else {
      avatar.textContent = '?';
      name.textContent = 'Guest';
      ddName.textContent = 'Not signed in';
      ddId.textContent = '';
      document.body.classList.add('logged-out');
    }
  }

  function toggleProfileDropdown(open) {
    const dd = $('#profileDropdown');
    const btn = $('#profileBtn');
    const shouldOpen = open === undefined ? dd.hidden : open;
    dd.hidden = !shouldOpen;
    btn.setAttribute('aria-expanded', shouldOpen ? 'true' : 'false');
  }

  // ---------------------- Auth ----------------------------------------
  function setupAuthForms() {
    // Tab switching
    $$('.tab').forEach(t => {
      t.addEventListener('click', () => {
        $$('.tab').forEach(x => x.classList.remove('active'));
        t.classList.add('active');
        const tab = t.dataset.tab;
        $('#loginForm').classList.toggle('hidden', tab !== 'login');
        $('#registerForm').classList.toggle('hidden', tab !== 'register');
      });
    });

    // Login submit
    $('#loginForm').addEventListener('submit', async (e) => {
      e.preventDefault();
      const fd = new FormData(e.target);
      const studentId = (fd.get('studentId') || '').toString().trim();
      if (!studentId) { toast('Student ID Number is required.', 'error'); return; }
      if (!isValidStudentId(studentId)) {
        toast('Student ID Number must be exactly 8 digits (e.g. 20231234). No letters, no email.', 'error', 4500);
        return;
      }
      const btn = e.target.querySelector('button[type="submit"]');
      btn.disabled = true; btn.textContent = 'Signing in...';
      const r = await api('/api/login', { method: 'POST', body: { studentId } });
      btn.disabled = false; btn.textContent = 'Sign In';
      if (r.ok && r.data && r.data.success) {
        setToken(r.data.token, {
          studentId: r.data.studentId,
          fullName: r.data.fullName,
        });
        toast('Welcome back, ' + r.data.fullName + '!', 'success');
        e.target.reset();
        showView('browse');
      } else {
        toast((r.data && r.data.message) || 'Login failed.', 'error');
      }
    });

    // Register submit
    $('#registerForm').addEventListener('submit', async (e) => {
      e.preventDefault();
      const fd = new FormData(e.target);
      const studentId = (fd.get('studentId') || '').toString().trim();
      const fullName = (fd.get('fullName') || '').toString().trim();
      const phone = (fd.get('phone') || '').toString().trim();
      const agree = fd.get('agreeTerms') === 'on';
      // All three fields are required - none may be empty.
      if (!studentId) { toast('Student ID Number is required.', 'error'); return; }
      if (!fullName)  { toast('Full Name is required.', 'error'); return; }
      if (!phone)     { toast('Phone Number is required.', 'error'); return; }
      if (!isValidStudentId(studentId)) {
        toast('Student ID Number must be exactly 8 digits (e.g. 20231234). No letters or email allowed.', 'error', 4500);
        return;
      }
      if (!isValidFullName(fullName)) {
        toast('Full Name must include both your first name AND your surname (e.g. Juan Dela Cruz).', 'error', 4500);
        return;
      }
      if (!isValidPhone(phone)) {
        toast('Phone Number must contain at least 7 digits (letters are not allowed).', 'error', 4500);
        return;
      }
      if (!agree) { toast('You must read and agree to the Terms and Conditions before registering.', 'error', 4500); return; }
      const btn = e.target.querySelector('button[type="submit"]');
      btn.disabled = true; btn.textContent = 'Registering...';
      const r = await api('/api/register', {
        method: 'POST',
        body: { studentId, fullName, phone, agreeTerms: agree ? 'true' : 'false' }
      });
      btn.disabled = false; btn.textContent = 'Register';
      if (r.ok && r.data && r.data.success) {
        setToken(r.data.token, {
          studentId: r.data.studentId,
          fullName: r.data.fullName,
        });
        toast('Account created. Welcome, ' + r.data.fullName + '!', 'success');
        e.target.reset();
        showView('browse');
      } else {
        toast((r.data && r.data.message) || 'Registration failed.', 'error');
      }
    });

    $('#openTerms').addEventListener('click', (e) => {
      e.preventDefault();
      $('#termsModal').classList.remove('hidden');
    });
    $('#closeTermsModal').addEventListener('click', () => $('#termsModal').classList.add('hidden'));
    $('#closeTermsBtn').addEventListener('click', () => $('#termsModal').classList.add('hidden'));
  }

  // ---------------------- Catalog -------------------------------------
  async function loadCatalog() {
    const grid = $('#catalogGrid');
    grid.innerHTML = '<div class="empty-state"><span class="loading"></span> Loading books...</div>';
    const r = await api('/api/books');
    if (!r.ok || !r.data || !r.data.success) {
      grid.innerHTML = '<div class="empty-state">Failed to load books. Is the Java server running?</div>';
      return;
    }
    state.books = r.data.books || [];
    state.categories = r.data.categories || [];
    // Populate category filter
    const sel = $('#categoryFilter');
    const cur = sel.value || 'all';
    sel.innerHTML = '<option value="all">All Categories</option>';
    // Build unique category list from current books (more robust)
    const cats = [];
    state.books.forEach(b => { if (!cats.includes(b.category)) cats.push(b.category); });
    cats.sort().forEach(c => {
      const opt = document.createElement('option');
      opt.value = c.toLowerCase();
      opt.textContent = c;
      sel.appendChild(opt);
    });
    sel.value = cur;
    renderCatalog();
  }

  function renderCatalog() {
    const grid = $('#catalogGrid');
    const search = ($('#searchInput').value || '').trim().toLowerCase();
    const cat = ($('#categoryFilter').value || 'all').toLowerCase();
    let books = state.books;
    if (search) {
      books = books.filter(b =>
        (b.title + ' ' + b.author + ' ' + b.category).toLowerCase().includes(search));
    }
    if (cat !== 'all') {
      books = books.filter(b => b.category.toLowerCase() === cat);
    }
    if (books.length === 0) {
      grid.innerHTML = '<div class="empty-state">No books match your search.</div>';
      return;
    }
    grid.innerHTML = books.map(b => bookCardHtml(b)).join('');
  }

  function bookCardHtml(b) {
    const status = (b.status || 'AVAILABLE').toUpperCase();
    const badge = status === 'AVAILABLE' ? 'badge-available'
              : status === 'BORROWED'   ? 'badge-borrowed'
              : 'badge-reserved';
    const total = b.totalCopies || 1;
    const avail = b.availableCopies != null ? b.availableCopies : total;

    // Expected return date strip: shown for BORROWED / RESERVED books
    // (so any user can see when the book is expected back).
    let expectedHtml = '';
    if ((status === 'BORROWED' || status === 'RESERVED')
        && b.expectedReturnDate) {
      const cls = b.daysOverdue > 0 ? 'overdue'
                : (b.isExtended ? 'extended' : '');
      const extendedBadge = b.isExtended
        ? `<span class="book-extended-badge">Extended +2 days</span>` : '';
      const text = b.daysOverdue > 0
        ? `Overdue by ${b.daysOverdue} day(s) - expected back ${b.expectedReturnDate}`
        : `Expected back: ${b.expectedReturnDate}`;
      expectedHtml = `<div class="book-expected ${cls}">${escapeHtml(text)}${extendedBadge}</div>`;
    }

    let actions = '';
    if (status === 'AVAILABLE') {
      if (state.student) {
        actions = `
          <button class="btn btn-primary btn-sm" data-action="borrow" data-id="${escapeHtml(b.bookId)}">Borrow</button>
          <button class="btn btn-secondary btn-sm" data-action="reserve" data-id="${escapeHtml(b.bookId)}">Reserve</button>`;
      } else {
        actions = `<span class="muted" style="font-size:.85rem;">Sign in to borrow or reserve</span>`;
      }
    } else {
      actions = `<span class="badge ${badge}">${status}</span>`;
    }
    return `
      <div class="book-card" data-bookid="${escapeHtml(b.bookId)}" title="Double-click to view details">
        <div class="book-cover" style="background:${escapeHtml(b.coverColor || '#1f2937')}">
          <span class="cover-title">${escapeHtml(b.title)}</span>
          <span class="cover-copies">${avail} of ${total} copies</span>
        </div>
        <div class="book-body">
          <div class="book-title">${escapeHtml(b.title)}</div>
          <div class="book-author">by ${escapeHtml(b.author)}</div>
          <span class="book-category">${escapeHtml(b.category)}</span>
          <div class="book-desc">${escapeHtml(b.description)}</div>
          ${expectedHtml}
          <div class="book-foot">
            <span class="badge ${badge}">${status}</span>
            ${actions}
          </div>
        </div>
      </div>`;
  }

  function setupCatalogEvents() {
    $('#searchInput').addEventListener('input', renderCatalog);
    $('#categoryFilter').addEventListener('change', renderCatalog);
    $('#clearFilters').addEventListener('click', () => {
      $('#searchInput').value = '';
      $('#categoryFilter').value = 'all';
      renderCatalog();
    });
    // Delegate borrow/reserve clicks
    $('#catalogGrid').addEventListener('click', async (e) => {
      const btn = e.target.closest('button[data-action]');
      if (!btn) return;
      // Don't trigger details modal when a button is clicked
      e.stopPropagation();
      const action = btn.dataset.action;
      const bookId = btn.dataset.id;
      if (action === 'borrow') await doBorrow(bookId);
      if (action === 'reserve') await doReserve(bookId);
    });
    // Double-click anywhere on a book card -> open details modal
    $('#catalogGrid').addEventListener('dblclick', (e) => {
      const card = e.target.closest('.book-card[data-bookid]');
      if (!card) return;
      // Ignore double-clicks that originated from a button (the click
      // handler above already processed them).
      if (e.target.closest('button[data-action]')) return;
      const bookId = card.dataset.bookid;
      showBookDetails(bookId);
    });
    // Book Details modal close
    $('#closeBookDetails').addEventListener('click', () => $('#bookDetailsModal').classList.add('hidden'));
  }

  // ---- Book details modal (opened by double-click) ------------------
  function showBookDetails(bookId) {
    const b = state.books.find(x => x.bookId === bookId);
    if (!b) { toast('Book not found.', 'error'); return; }
    const status = (b.status || 'AVAILABLE').toUpperCase();
    const statusRowCls = status === 'AVAILABLE' ? 'available'
                       : status === 'BORROWED'   ? 'borrowed'
                       : 'reserved';
    const statusText = status === 'AVAILABLE' ? 'Available - this book can be borrowed or reserved.'
                     : status === 'BORROWED'   ? 'Borrowed - currently loaned to another student.'
                     : 'Reserved - currently held by another student.';
    let actionsHtml = '';
    if (status === 'AVAILABLE') {
      if (state.student) {
        actionsHtml = `
          <button class="btn btn-primary" data-action="borrow" data-id="${escapeHtml(b.bookId)}">Borrow Now</button>
          <button class="btn btn-secondary" data-action="reserve" data-id="${escapeHtml(b.bookId)}">Reserve</button>`;
      } else {
        actionsHtml = `<span class="muted">Sign in to borrow or reserve this book.</span>`;
      }
    } else {
      actionsHtml = `<span class="muted">This book is currently ${status.toLowerCase()} and cannot be borrowed or reserved.</span>`;
    }
    $('#bookDetailsBody').innerHTML = `
      <div class="book-details">
        <div class="book-details-cover" style="background:${escapeHtml(b.coverColor || '#1f2937')}">
          ${escapeHtml(b.title)}
        </div>
        <div>
          <h3 class="book-details-title">${escapeHtml(b.title)}</h3>
          <div class="book-details-author">by ${escapeHtml(b.author)}</div>
        </div>
        <div class="book-details-meta">
          <div><div class="label">Book ID</div><div class="value">${escapeHtml(b.bookId)}</div></div>
          <div><div class="label">Category</div><div class="value">${escapeHtml(b.category)}</div></div>
          <div><div class="label">Author</div><div class="value">${escapeHtml(b.author)}</div></div>
          <div><div class="label">Copies Available</div><div class="value">${b.availableCopies != null ? b.availableCopies : '?'} of ${b.totalCopies || 1}</div></div>
          ${b.expectedReturnDate ? `<div><div class="label">Expected Back</div><div class="value">${escapeHtml(b.expectedReturnDate)}${b.isExtended ? ' (extended +2d)' : ''}</div></div>` : ''}
        </div>
        <div>
          <div class="label" style="font-size:.78rem;text-transform:uppercase;letter-spacing:.04em;color:var(--text-muted);margin-bottom:6px;">Summary</div>
          <div class="book-details-summary">${escapeHtml(b.description)}</div>
        </div>
        <div class="book-details-status-row ${statusRowCls}">
          <span class="status-label">Availability Status:</span>
          <span class="status-text">${statusText}</span>
        </div>
      </div>`;
    $('#bookDetailsFoot').innerHTML = actionsHtml;
    $('#bookDetailsModal').classList.remove('hidden');

    // Wire up the footer buttons
    $$('#bookDetailsFoot button[data-action]').forEach(btn => {
      btn.addEventListener('click', async () => {
        const action = btn.dataset.action;
        const id = btn.dataset.id;
        $('#bookDetailsModal').classList.add('hidden');
        if (action === 'borrow') await doBorrow(id);
        if (action === 'reserve') await doReserve(id);
      });
    });
  }

  async function doBorrow(bookId) {
    if (!state.student) { toast('Please sign in first.', 'error'); showView('auth'); return; }
    const r = await api('/api/borrow', { method: 'POST', body: { bookId } });
    if (r.ok && r.data && r.data.success) {
      toast('Book borrowed successfully!', 'success');
      await loadCatalog();
      showReceipt(r.data.receiptNo);
    } else {
      toast((r.data && r.data.message) || 'Borrowing failed.', 'error');
    }
  }

  async function doReserve(bookId) {
    if (!state.student) { toast('Please sign in first.', 'error'); showView('auth'); return; }
    const r = await api('/api/reserve', { method: 'POST', body: { bookId } });
    if (r.ok && r.data && r.data.success) {
      toast('Book reserved successfully.', 'success');
      await loadCatalog();
    } else {
      toast((r.data && r.data.message) || 'Reservation failed.', 'error');
    }
  }

  // ---------------------- Borrowed list ------------------------------
  async function loadBorrowed() {
    const list = $('#borrowedList');
    if (!state.student) {
      list.innerHTML = '<div class="empty-state">Please sign in to see your borrowed books.</div>';
      return;
    }
    list.innerHTML = '<div class="empty-state"><span class="loading"></span> Loading...</div>';
    const r = await api('/api/borrowed');
    if (!r.ok || !r.data || !r.data.success) {
      list.innerHTML = '<div class="empty-state">Failed to load borrowed books.</div>';
      return;
    }
    const items = r.data.borrowed || [];
    if (items.length === 0) {
      list.innerHTML = '<div class="empty-state">You have no active borrowings. Browse the catalog to borrow a book.</div>';
      return;
    }
    list.innerHTML = items.map(recordHtml).join('');
    bindRecordActions(list);
  }

  function recordHtml(r) {
    const overdue = r.computedStatus === 'OVERDUE' || r.status === 'OVERDUE';
    const isLost = r.status === 'LOST' || r.lost === true;
    const cls = ['record'];
    if (overdue) cls.push('overdue');
    if (r.status === 'RETURNED') cls.push('returned');
    if (r.status === 'LOST') cls.push('overdue');
    if (r.status === 'CANCELED' || r.status === 'CANCELLED') cls.push('canceled');
    let actions = '';
    if (r.status === 'ACTIVE' || r.status === 'OVERDUE') {
      actions += `<button class="btn btn-secondary btn-sm" data-action="extend" data-id="${escapeHtml(r.recordId)}" ${r.extended ? 'disabled' : ''}>${r.extended ? 'Extended' : 'Extend (+2 days)'}</button>`;
      actions += `<button class="btn btn-primary btn-sm" data-action="return" data-id="${escapeHtml(r.recordId)}">Return Book</button>`;
      actions += `<button class="btn btn-danger btn-sm" data-action="lost" data-id="${escapeHtml(r.recordId)}">Report Lost</button>`;
    }
    actions += `<button class="btn btn-secondary btn-sm" data-action="receipt" data-id="${escapeHtml(r.receiptNo)}">View Receipt</button>`;

    let penaltyBlock = '';
    if (r.status === 'ACTIVE' || r.status === 'OVERDUE') {
      if (r.overdueDays > 0) {
        penaltyBlock = `
          <div class="record-meta">
            <div><div class="label">Overdue Days</div><div class="value danger">${r.overdueDays} day(s)</div></div>
            <div><div class="label">Current Penalty</div><div class="value danger">&#8369;${r.overduePenalty}</div></div>
            <div><div class="label">Status</div><div class="value danger">OVERDUE</div></div>
          </div>`;
      }
    } else if (r.status === 'LOST') {
      penaltyBlock = `
        <div class="record-meta">
          <div><div class="label">Lost Date</div><div class="value danger">${escapeHtml(r.lostDate || '')}</div></div>
          <div><div class="label">Lost Book Penalty</div><div class="value danger">&#8369;${r.lostPenalty || 1000}</div></div>
          <div><div class="label">Payment</div><div class="value ${r.lostPaid ? 'success' : 'danger'}">${r.lostPaid ? 'Settled' : 'Unpaid - settle in Penalties'}</div></div>
        </div>`;
    } else if (r.status === 'RETURNED') {
      const total = (r.overduePenalty || 0) + (r.damagePenalty || 0);
      if (total > 0) {
        penaltyBlock = `
          <div class="record-meta">
            <div><div class="label">Overdue Penalty</div><div class="value ${r.overduePenalty > 0 ? 'danger' : ''}">&#8369;${r.overduePenalty || 0}</div></div>
            <div><div class="label">Damage (${escapeHtml(r.damageCategory || 'none')})</div><div class="value ${r.damagePenalty > 0 ? 'danger' : ''}">&#8369;${r.damagePenalty || 0}</div></div>
            <div><div class="label">Total Penalty</div><div class="value danger">&#8369;${total}</div></div>
          </div>`;
      } else {
        penaltyBlock = `<div class="record-meta"><div><div class="label">Penalty</div><div class="value success">None</div></div></div>`;
      }
    }

    const badgeCls = isLost ? 'badge-lost'
                  : overdue ? 'badge-overdue'
                  : (r.status === 'RETURNED' ? 'badge-borrowed' : 'badge-available');
    return `
      <div class="${cls.join(' ')}">
        <div class="record-head">
          <div>
            <div class="record-title">${escapeHtml(r.bookTitle)}</div>
            <div class="record-sub">by ${escapeHtml(r.bookAuthor)} &middot; Receipt ${escapeHtml(r.receiptNo)}</div>
          </div>
          <span class="badge ${badgeCls}">${escapeHtml(r.computedStatus || r.status)}</span>
        </div>
        <div class="record-meta">
          <div><div class="label">Borrow Date</div><div class="value">${escapeHtml(r.borrowDate)}</div></div>
          <div><div class="label">Due Date</div><div class="value">${escapeHtml(r.dueDate)}</div></div>
          <div><div class="label">Extended</div><div class="value">${r.extended ? 'Yes (+2 days)' : 'No'}</div></div>
          ${r.returnDate ? `<div><div class="label">Return Date</div><div class="value">${escapeHtml(r.returnDate)}</div></div>` : ''}
        </div>
        ${penaltyBlock}
        <div class="record-actions">${actions}</div>
      </div>`;
  }

  function bindRecordActions(root) {
    root.addEventListener('click', async (e) => {
      const btn = e.target.closest('button[data-action]');
      if (!btn) return;
      const action = btn.dataset.action;
      const id = btn.dataset.id;
      if (action === 'extend') await doExtend(id);
      if (action === 'return') askReturn(id);
      if (action === 'lost') askLost(id);
      if (action === 'receipt') showReceipt(id);
      if (action === 'cancel-reservation') askCancelReservation(id);
      if (action === 'borrow-reserved') askBorrowReserved(id);
      if (action === 'pay-penalty') askPayPenalty(id, btn.dataset.type);
    });
  }

  function askLost(recordId) {
    openConfirm('Report Book as Lost',
      'Are you sure you want to report this book as lost? A penalty of \u20B11,000 will be applied to your account, and you will not be able to borrow or reserve another book until this penalty is settled.',
      async () => {
        const r = await api('/api/lost', { method: 'POST', body: { recordId } });
        if (r.ok && r.data && r.data.success) {
          toast(r.data.message || 'Book reported as lost.', 'success', 5000);
          await loadBorrowed();
        } else {
          toast((r.data && r.data.message) || 'Could not report book as lost.', 'error');
        }
      });
  }

  function askPayPenalty(relatedId, type) {
    const label = type === 'lost' ? 'lost-book' : 'expired-reservation';
    openConfirm('Settle ' + label + ' penalty',
      'Mark this ' + label + ' penalty as paid? (This simulates a cash payment - no real money changes hands.)',
      async () => {
        const r = await api('/api/pay-penalty', { method: 'POST', body: { type, relatedId } });
        if (r.ok && r.data && r.data.success) {
          toast(r.data.message || 'Penalty settled.', 'success');
          // Refresh the penalties view if it's currently visible.
          const penView = $('#view-penalties');
          if (penView && !penView.classList.contains('hidden')) {
            await loadPenalties();
          }
          // Also refresh borrowed list (lost books show payment status there).
          const bView = $('#view-borrowed');
          if (bView && !bView.classList.contains('hidden')) {
            await loadBorrowed();
          }
        } else {
          toast((r.data && r.data.message) || 'Could not settle penalty.', 'error');
        }
      });
  }

  async function doExtend(recordId) {
    const r = await api('/api/extend', { method: 'POST', body: { recordId } });
    if (r.ok && r.data && r.data.success) {
      toast(r.data.message || 'Borrowing extended.', 'success');
      await loadBorrowed();
    } else {
      toast((r.data && r.data.message) || 'Extension failed.', 'error');
    }
  }

  // ---------------------- Return with damage ------------------------
  function askReturn(recordId) {
    $('#returnModal').classList.remove('hidden');
    state.returnCallback = async (damageCategory) => {
      const r = await api('/api/return', { method: 'POST', body: { recordId, damageCategory } });
      if (r.ok && r.data && r.data.success) {
        let msg = r.data.message || 'Book returned.';
        if (r.data.totalPenalty > 0) {
          msg += ` Total penalty: \u20B1${r.data.totalPenalty}.`;
        }
        toast(msg, 'success', 4500);
        await loadBorrowed();
      } else {
        toast((r.data && r.data.message) || 'Return failed.', 'error');
      }
    };
  }

  // ---------------------- Reservations -------------------------------
  async function loadReservations() {
    const list = $('#reservationList');
    if (!state.student) {
      list.innerHTML = '<div class="empty-state">Please sign in to see your reservations.</div>';
      return;
    }
    list.innerHTML = '<div class="empty-state"><span class="loading"></span> Loading...</div>';
    const r = await api('/api/reservations');
    if (!r.ok || !r.data || !r.data.success) {
      list.innerHTML = '<div class="empty-state">Failed to load reservations.</div>';
      return;
    }
    const items = r.data.reservations || [];
    if (items.length === 0) {
      list.innerHTML = '<div class="empty-state">You have no reservations yet.</div>';
      return;
    }
    list.innerHTML = items.map(r => {
      const cls = ['record'];
      if (r.status === 'CANCELED' || r.status === 'CANCELLED') cls.push('canceled');
      if (r.status === 'COMPLETED') cls.push('returned');
      let actions = '';
      if (r.status === 'ACTIVE') {
        actions = `
          <button class="btn btn-primary btn-sm" data-action="borrow-reserved" data-id="${escapeHtml(r.reservationId)}">Borrow</button>
          <button class="btn btn-danger btn-sm" data-action="cancel-reservation" data-id="${escapeHtml(r.reservationId)}">Cancel Reservation</button>`;
      }
      const badgeCls = r.status === 'ACTIVE' ? 'badge-reserved'
                    : r.status === 'COMPLETED' ? 'badge-available'
                    : 'badge-borrowed';
      return `
        <div class="${cls.join(' ')}">
          <div class="record-head">
            <div>
              <div class="record-title">${escapeHtml(r.bookTitle)}</div>
              <div class="record-sub">by ${escapeHtml(r.bookAuthor)} &middot; Reservation ${escapeHtml(r.reservationId)}</div>
            </div>
            <span class="badge ${badgeCls}">${escapeHtml(r.status)}</span>
          </div>
          <div class="record-meta">
            <div><div class="label">Reserve Date</div><div class="value">${escapeHtml(r.reserveDate)}</div></div>
            <div><div class="label">Book ID</div><div class="value">${escapeHtml(r.bookId)}</div></div>
          </div>
          ${actions ? `<div class="record-actions">${actions}</div>` : ''}
        </div>`;
    }).join('');
    bindRecordActions(list);
  }

  function askCancelReservation(reservationId) {
    openConfirm('Cancel Reservation',
      'Are you sure you want to cancel this reservation? The book will become available to other students.',
      async () => {
        const r = await api('/api/cancel-reservation', { method: 'POST', body: { reservationId } });
        if (r.ok && r.data && r.data.success) {
          toast(r.data.message || 'Reservation canceled.', 'success');
          await loadReservations();
        } else {
          toast((r.data && r.data.message) || 'Cancellation failed.', 'error');
        }
      });
  }

  // Convert an active reservation into a borrowing transaction.
  function askBorrowReserved(reservationId) {
    openConfirm('Borrow Reserved Book',
      'Convert this reservation into a borrowing? A digital receipt will be generated and the reservation will be marked as Completed.',
      async () => {
        const r = await api('/api/borrow-reserved', { method: 'POST', body: { reservationId } });
        if (r.ok && r.data && r.data.success) {
          toast('Reservation converted to borrowing. Receipt generated.', 'success');
          await loadReservations();
          showReceipt(r.data.receiptNo);
        } else {
          toast((r.data && r.data.message) || 'Conversion failed.', 'error');
        }
      });
  }

  // ---------------------- Transaction history ------------------------
  async function loadHistory() {
    const list = $('#historyList');
    if (!state.student) {
      list.innerHTML = '<div class="empty-state">Please sign in to see your transaction history.</div>';
      return;
    }
    list.innerHTML = '<div class="empty-state"><span class="loading"></span> Loading...</div>';
    const r = await api('/api/history');
    if (!r.ok || !r.data || !r.data.success) {
      list.innerHTML = '<div class="empty-state">Failed to load history.</div>';
      return;
    }
    const items = r.data.history || [];
    if (items.length === 0) {
      list.innerHTML = '<div class="empty-state">You have no transactions yet.</div>';
      return;
    }
    list.innerHTML = items.map(r => {
      const isRes = !!r.reservationId;
      if (isRes) {
        const badgeCls = r.status === 'ACTIVE' ? 'badge-reserved'
                      : r.status === 'COMPLETED' ? 'badge-available'
                      : 'badge-borrowed';
        return `
          <div class="record ${r.status === 'CANCELED' || r.status === 'CANCELLED' ? 'canceled' : ''}">
            <div class="record-head">
              <div>
                <div class="record-title">${escapeHtml(r.bookTitle)}</div>
                <div class="record-sub">RESERVATION &middot; ${escapeHtml(r.reservationId)}</div>
              </div>
              <span class="badge ${badgeCls}">${escapeHtml(r.status)}</span>
            </div>
            <div class="record-meta">
              <div><div class="label">Reserve Date</div><div class="value">${escapeHtml(r.reserveDate)}</div></div>
            </div>
          </div>`;
      }
      return recordHtml(r);
    }).join('');
    bindRecordActions(list);
  }

  // ---------------------- Receipts -----------------------------------
  async function loadReceipts() {
    const list = $('#receiptsList');
    if (!state.student) {
      list.innerHTML = '<div class="empty-state">Please sign in to see your receipts.</div>';
      return;
    }
    list.innerHTML = '<div class="empty-state"><span class="loading"></span> Loading...</div>';
    const r = await api('/api/receipts');
    if (!r.ok || !r.data || !r.data.success) {
      list.innerHTML = '<div class="empty-state">Failed to load receipts.</div>';
      return;
    }
    const items = r.data.receipts || [];
    if (items.length === 0) {
      list.innerHTML = '<div class="empty-state">You have no receipts yet. Borrow a book to generate one.</div>';
      return;
    }
    list.innerHTML = items.map(d => `
      <div class="record">
        <div class="record-head">
          <div>
            <div class="record-title">${escapeHtml(d.bookTitle)}</div>
            <div class="record-sub">Receipt ${escapeHtml(d.receiptNo)} &middot; ${escapeHtml(d.borrowDateTime)}</div>
          </div>
          <span class="badge ${d.status === 'ACTIVE' ? 'badge-available' : (d.status === 'OVERDUE' ? 'badge-overdue' : 'badge-borrowed')}">${escapeHtml(d.status)}</span>
        </div>
        <div class="record-meta">
          <div><div class="label">Borrow Date</div><div class="value">${escapeHtml(d.borrowDateTime)}</div></div>
          <div><div class="label">Due Date</div><div class="value">${escapeHtml(d.dueDate)}</div></div>
          <div><div class="label">Duration</div><div class="value">${escapeHtml(d.duration)}</div></div>
        </div>
        <div class="record-actions">
          <button class="btn btn-primary btn-sm" data-action="receipt" data-id="${escapeHtml(d.receiptNo)}">View Receipt</button>
        </div>
      </div>`).join('');
    bindRecordActions(list);
  }

  // ---------------------- Penalties -----------------------------------
  async function loadPenalties() {
    const list = $('#penaltiesList');
    const banner = $('#blockBanner');
    if (!state.student) {
      list.innerHTML = '<div class="empty-state">Please sign in to see your penalties.</div>';
      banner.classList.add('hidden');
      return;
    }
    list.innerHTML = '<div class="empty-state"><span class="loading"></span> Loading...</div>';
    // First, get the profile so we know whether the student is blocked.
    const prof = await api('/api/profile');
    if (prof.ok && prof.data && prof.data.success && prof.data.blocked) {
      banner.textContent = prof.data.blockReason || 'You are currently blocked from borrowing or reserving.';
      banner.classList.remove('hidden');
    } else {
      banner.classList.add('hidden');
    }
    const r = await api('/api/penalties');
    if (!r.ok || !r.data || !r.data.success) {
      list.innerHTML = '<div class="empty-state">Failed to load penalties.</div>';
      return;
    }
    const items = r.data.penalties || [];
    // Only show entries that have a non-zero penalty or are still blocking.
    const visible = items.filter(p => (p.totalPenalty || 0) > 0 || p.canPay);
    if (visible.length === 0) {
      list.innerHTML = '<div class="empty-state">You have no penalties. Great job!</div>';
      return;
    }
    list.innerHTML = visible.map(p => {
      if (p.type === 'expired-reservation') {
        return `
          <div class="record overdue">
            <div class="record-head">
              <div>
                <div class="record-title">${escapeHtml(p.bookTitle)}</div>
                <div class="record-sub">EXPIRED RESERVATION &middot; ${escapeHtml(p.reservationId)}</div>
              </div>
              <span class="badge badge-expired">EXPIRED</span>
            </div>
            <div class="record-meta">
              <div><div class="label">Reserve Date</div><div class="value">${escapeHtml(p.reserveDate)}</div></div>
              <div><div class="label">Expiry Date</div><div class="value">${escapeHtml(p.expiryDate)}</div></div>
              <div><div class="label">Penalty</div><div class="value danger">&#8369;${p.expiredPenalty}</div></div>
              <div><div class="label">Status</div><div class="value ${p.paid ? 'success' : 'danger'}">${p.paid ? 'Settled' : 'Unpaid'}</div></div>
            </div>
            ${p.canPay ? `<div class="record-actions"><button class="btn btn-primary btn-sm" data-action="pay-penalty" data-type="expired" data-id="${escapeHtml(p.reservationId)}">Settle Penalty</button></div>` : ''}
          </div>`;
      }
      // Borrowing penalty
      const parts = [];
      if (p.overduePenalty > 0) parts.push(`Overdue (${p.overdueDays}d): \u20B1${p.overduePenalty}`);
      if (p.damagePenalty > 0) parts.push(`Damage (${p.damageCategory}): \u20B1${p.damagePenalty}`);
      if (p.lost) parts.push(`Lost book: \u20B1${p.lostPenalty}`);
      const summary = parts.join(' &middot; ') || 'No penalty';
      return `
        <div class="record ${p.lost ? 'overdue' : (p.status === 'RETURNED' ? 'returned' : '')}">
          <div class="record-head">
            <div>
              <div class="record-title">${escapeHtml(p.bookTitle)}</div>
              <div class="record-sub">${escapeHtml(p.recordId)} &middot; ${escapeHtml(p.status)}</div>
            </div>
            <span class="badge ${p.lost ? 'badge-lost' : (p.overdueDays > 0 ? 'badge-overdue' : 'badge-borrowed')}">${escapeHtml(p.status)}</span>
          </div>
          <div class="record-meta">
            <div><div class="label">Due Date</div><div class="value">${escapeHtml(p.dueDate)}</div></div>
            ${p.returnDate ? `<div><div class="label">Return Date</div><div class="value">${escapeHtml(p.returnDate)}</div></div>` : ''}
            <div><div class="label">Penalty Breakdown</div><div class="value ${p.totalPenalty > 0 ? 'danger' : 'success'}">${summary}</div></div>
            <div><div class="label">Total</div><div class="value danger">&#8369;${p.totalPenalty}</div></div>
          </div>
          ${p.canPay ? `<div class="record-actions"><button class="btn btn-primary btn-sm" data-action="pay-penalty" data-type="lost" data-id="${escapeHtml(p.recordId)}">Settle Lost-Book Penalty</button></div>` : ''}
        </div>`;
    }).join('');
    bindRecordActions(list);
  }

  async function showReceipt(receiptNo) {
    if (!receiptNo) { toast('Receipt number is missing.', 'error'); return; }
    const r = await api('/api/receipt?id=' + encodeURIComponent(receiptNo));
    if (!r.ok || !r.data || !r.data.success) {
      toast((r.data && r.data.message) || 'Could not load receipt.', 'error');
      return;
    }
    const d = r.data.receipt;
    const body = $('#receiptBody');
    const isOverdue = (d.status || '').toUpperCase() === 'OVERDUE' || (d.status || '').toUpperCase() === 'RETURNED-OVERDUE';
    body.innerHTML = `
      <div class="receipt">
        <div class="receipt-head">
          <div class="receipt-no">${escapeHtml(d.receiptNo)}</div>
          <div class="receipt-status" style="color:${isOverdue ? 'var(--red-dark)' : 'var(--blue-dark)'}">${escapeHtml(d.status)}</div>
        </div>
        <div class="receipt-row"><span class="k">Student ID</span><span class="v">${escapeHtml(d.studentId)}</span></div>
        <div class="receipt-row"><span class="k">Student Name</span><span class="v">${escapeHtml(d.studentName)}</span></div>
        <div class="receipt-row"><span class="k">Book Title</span><span class="v">${escapeHtml(d.bookTitle)}</span></div>
        <div class="receipt-row"><span class="k">Author</span><span class="v">${escapeHtml(d.author)}</span></div>
        <div class="receipt-row"><span class="k">Borrow Date / Time</span><span class="v">${escapeHtml(d.borrowDateTime)}</span></div>
        <div class="receipt-row"><span class="k">Due Date</span><span class="v">${escapeHtml(d.dueDate)}</span></div>
        <div class="receipt-row"><span class="k">Duration</span><span class="v">${escapeHtml(d.duration)}</span></div>
        ${d.returnDate ? `<div class="receipt-row"><span class="k">Return Date</span><span class="v">${escapeHtml(d.returnDate)}</span></div>` : ''}
        <div class="receipt-row"><span class="k">Transaction Status</span><span class="v" style="color:${isOverdue ? 'var(--red-dark)' : 'var(--green-dark)'}">${escapeHtml(d.status)}</span></div>
        <div class="receipt-foot">
          This receipt was generated automatically by the Online Library System.<br/>
          Closing this receipt does not delete it. It remains in your profile.
        </div>
      </div>`;
    $('#receiptModal').classList.remove('hidden');
  }

  // ---------------------- Profile details ----------------------------
  async function loadProfileDetails() {
    const el = $('#profileDetails');
    if (!state.student) {
      el.innerHTML = '<div class="empty-state">Please sign in to view your profile.</div>';
      return;
    }
    const r = await api('/api/profile');
    if (!r.ok || !r.data || !r.data.success) {
      el.innerHTML = '<div class="empty-state">Failed to load profile.</div>';
      return;
    }
    const p = r.data;
    el.innerHTML = `
      <div class="detail-row"><span class="k">Full Name</span><span class="v">${escapeHtml(p.fullName)}</span></div>
      <div class="detail-row"><span class="k">Student ID</span><span class="v">${escapeHtml(p.studentId)}</span></div>
      <div class="detail-row"><span class="k">Phone Number</span><span class="v">${escapeHtml(p.phone)}</span></div>
      <div class="detail-row"><span class="k">Registered At</span><span class="v">${escapeHtml(p.registeredAt)}</span></div>
      <div class="detail-row"><span class="k">Active Borrowings</span><span class="v">${p.activeBorrowings} / ${p.maxBorrowings}</span></div>
      <div class="detail-row"><span class="k">Active Reservations</span><span class="v">${p.activeReservations}</span></div>
    `;
  }

  // ---------------------- Confirm dialog -----------------------------
  function openConfirm(title, text, onOk) {
    $('#confirmTitle').textContent = title;
    $('#confirmText').textContent = text;
    state.confirmCallback = onOk;
    $('#confirmModal').classList.remove('hidden');
  }

  // ---------------------- Logout -------------------------------------
  async function doLogout() {
    await api('/api/logout', { method: 'POST' });
    setToken(null, null);
    toast('Signed out.', 'info');
    showView('home');
  }

  // ---------------------- Wire up navigation -------------------------
  function setupNavigation() {
    // Top nav links - ALL of them require authentication.
    $$('.nav-link').forEach(link => {
      link.addEventListener('click', (e) => {
        e.preventDefault();
        if (!state.student) {
          toast('Please sign in first.', 'error');
          showView('auth');
          return;
        }
        showView(link.dataset.view);
      });
    });

    // Brand click - if not signed in, go to auth; otherwise go home.
    $('.brand').addEventListener('click', (e) => {
      e.preventDefault();
      if (!state.student) { showView('auth'); return; }
      showView('home');
    });

    // Hero CTA buttons (these only appear when logged in, on the home view)
    $$('[data-action="go-browse"]').forEach(b => b.addEventListener('click', () => showView('browse')));
    $$('[data-action="go-auth"]').forEach(b => b.addEventListener('click', () => showView('auth')));

    // Profile dropdown toggle
    $('#profileBtn').addEventListener('click', (e) => {
      e.stopPropagation();
      toggleProfileDropdown();
    });
    document.addEventListener('click', (e) => {
      const wrap = $('#profileWrap');
      if (!wrap.contains(e.target)) toggleProfileDropdown(false);
    });

    // Profile dropdown items
    $$('#profileDropdown .dropdown-item').forEach(item => {
      item.addEventListener('click', () => {
        toggleProfileDropdown(false);
        const a = item.dataset.action;
        if (a === 'logout') { doLogout(); return; }
        if (!state.student) { toast('Please sign in first.', 'error'); showView('auth'); return; }
        if (a === 'profile-details') showView('profile-details');
        if (a === 'history')          showView('history');
        if (a === 'receipts')         showView('receipts');
      });
    });

    // Receipt modal close
    $('#closeReceiptModal').addEventListener('click', () => $('#receiptModal').classList.add('hidden'));
    $('#closeReceiptBtn').addEventListener('click', () => $('#receiptModal').classList.add('hidden'));

    // Confirm modal
    $('#confirmCancel').addEventListener('click', () => {
      $('#confirmModal').classList.add('hidden');
      state.confirmCallback = null;
    });
    $('#confirmOk').addEventListener('click', async () => {
      $('#confirmModal').classList.add('hidden');
      const cb = state.confirmCallback;
      state.confirmCallback = null;
      if (cb) await cb();
    });

    // Return modal
    $('#closeReturnModal').addEventListener('click', () => {
      $('#returnModal').classList.add('hidden');
      state.returnCallback = null;
    });
    $('#returnCancel').addEventListener('click', () => {
      $('#returnModal').classList.add('hidden');
      state.returnCallback = null;
    });
    $('#returnConfirm').addEventListener('click', async () => {
      const damage = $('#damageCategory').value;
      $('#returnModal').classList.add('hidden');
      const cb = state.returnCallback;
      state.returnCallback = null;
      if (cb) await cb(damage);
    });

    // Click outside modal closes it (except receipt, which we keep
    // explicit on its close buttons to match the spec)
    $$('.modal-backdrop').forEach(bd => {
      bd.addEventListener('click', (e) => {
        if (e.target === bd && bd.id !== 'receiptModal') {
          bd.classList.add('hidden');
        }
      });
    });
  }

  // ---------------------- Init ----------------------------------------
  function init() {
    setupAuthForms();
    setupCatalogEvents();
    setupNavigation();
    refreshProfileUI();
    // Enforce the workflow: if the user is not signed in, the ONLY thing
    // they see is the sign-in / register screen. The home page, navigation
    // bar, and all other views are hidden until they authenticate.
    if (state.student) {
      loadHomeStats();
      showView('home');
    } else {
      showView('auth');
    }
  }

  async function loadHomeStats() {
    const r = await api('/api/books');
    if (r.ok && r.data && r.data.success) {
      const books = r.data.books || [];
      $('#statBooks').textContent = books.length;
      $('#statAvailable').textContent = books.filter(b => (b.status || '').toUpperCase() === 'AVAILABLE').length;
    } else {
      $('#statBooks').textContent = '--';
      $('#statAvailable').textContent = '--';
    }
  }

  document.addEventListener('DOMContentLoaded', init);
})();
