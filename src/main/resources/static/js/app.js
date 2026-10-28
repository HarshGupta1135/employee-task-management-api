'use strict';

/*
 * Minimal single-page client for the Task Management API.
 * Plain JavaScript, no build step. All user-supplied text is rendered with textContent (never innerHTML).
 */

const TOKEN_KEY = 'taskManager.token';
const DEMO_ACCOUNTS = {
    admin: { email: 'admin@taskmanager.com', password: 'Admin@123' },
    alice: { email: 'alice@taskmanager.com', password: 'User@1234' },
    bob: { email: 'bob@taskmanager.com', password: 'User@1234' },
};
const STATUS_LABELS = { TODO: 'To do', IN_PROGRESS: 'In progress', DONE: 'Done' };
const PRIORITY_LABELS = { LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High', CRITICAL: 'Critical' };
const MAX_TOASTS = 3;

const state = {
    token: null,
    user: null,
    users: [],
    page: 0,
    size: 10,
    editingTask: null,
};

const $ = (selector) => document.querySelector(selector);

/* ---------------------------------------------------------------- storage */

const tokenStore = {
    get() {
        try { return localStorage.getItem(TOKEN_KEY); } catch { return null; }
    },
    set(token) {
        try { localStorage.setItem(TOKEN_KEY, token); } catch { /* storage unavailable: session lasts until reload */ }
    },
    clear() {
        try { localStorage.removeItem(TOKEN_KEY); } catch { /* ignore */ }
    },
};

/* -------------------------------------------------------------------- API */

class ApiError extends Error {
    constructor(status, body) {
        super((body && body.message) || `Request failed with status ${status}`);
        this.status = status;
        this.body = body;
        this.handled = false;
    }
}

async function api(path, { method = 'GET', body } = {}) {
    const headers = { Accept: 'application/json' };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (state.token) headers.Authorization = `Bearer ${state.token}`;

    let response;
    try {
        response = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    } catch {
        throw new ApiError(0, { message: 'Cannot reach the server. Is the backend running?' });
    }

    const text = await response.text();
    let data = null;
    if (text) {
        try { data = JSON.parse(text); } catch { data = null; }
    }

    if (!response.ok) {
        const error = new ApiError(response.status, data);
        if (response.status === 401 && state.token) {
            // Token expired or the account was removed: go back to the sign-in screen.
            error.handled = true;
            signOut('Your session has expired. Please sign in again.');
        }
        throw error;
    }
    return data;
}

/* -------------------------------------------------------------- utilities */

function el(tag, props = {}, ...children) {
    const node = document.createElement(tag);
    for (const [key, value] of Object.entries(props)) {
        if (value === undefined || value === null || value === false) continue;
        if (key === 'className') node.className = value;
        else if (key === 'text') node.textContent = value;
        else if (key.startsWith('on') && typeof value === 'function') node.addEventListener(key.slice(2).toLowerCase(), value);
        else if (key === 'value' || key === 'disabled') node[key] = value;
        else node.setAttribute(key, value === true ? '' : String(value));
    }
    for (const child of children.flat()) {
        if (child === undefined || child === null || child === false) continue;
        node.append(child instanceof Node ? child : document.createTextNode(String(child)));
    }
    return node;
}

function toast(message, type = 'info') {
    const node = el('div', {
        className: type === 'error' ? 'toast toast-error' : 'toast',
        role: type === 'error' ? 'alert' : 'status',
        text: message,
    });
    const container = $('#toasts');
    container.append(node);
    while (container.children.length > MAX_TOASTS) container.firstElementChild.remove();
    setTimeout(() => node.remove(), 4000);
}

function showError(error) {
    if (!error.handled) toast(error.message, 'error');
}

function showAlert(node, message, kind = 'error') {
    node.textContent = message || '';
    node.classList.toggle('info', kind === 'info');
    node.hidden = !message;
}

function clearFormErrors(form) {
    form.querySelectorAll('.field-error').forEach((node) => { node.textContent = ''; });
    form.querySelectorAll('.field.invalid').forEach((node) => node.classList.remove('invalid'));
}

/** Shows API validation errors next to the matching inputs; returns messages that had no matching input. */
function applyFormErrors(form, error) {
    const violations = (error.body && error.body.validationErrors) || [];
    const byField = new Map();
    const unmatched = [];
    for (const violation of violations) {
        const target = form.querySelector(`[data-error-for="${CSS.escape(violation.field)}"]`);
        if (!target) {
            unmatched.push(violation.message);
            continue;
        }
        if (!byField.has(target)) byField.set(target, []);
        byField.get(target).push(violation.message);
    }
    byField.forEach((messages, target) => {
        target.textContent = messages.join('. ');
        const field = target.closest('.field');
        if (field) field.classList.add('invalid');
    });
    return { hasFieldErrors: byField.size > 0, unmatched };
}

async function withBusy(form, action) {
    const button = form.querySelector('[type="submit"]');
    button.disabled = true;
    try {
        await action();
    } finally {
        button.disabled = false;
    }
}

/** Today's date as yyyy-MM-dd in the browser's time zone (toISOString would use UTC). */
function todayIso() {
    const now = new Date();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    return `${now.getFullYear()}-${month}-${day}`;
}

function formatDate(isoDate) {
    const [year, month, day] = isoDate.split('-').map(Number);
    return new Date(year, month - 1, day).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
}

function formatInstant(isoInstant) {
    return new Date(isoInstant).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
}

function debounce(fn, delay) {
    let timer;
    return (...args) => {
        clearTimeout(timer);
        timer = setTimeout(() => fn(...args), delay);
    };
}

const isAdmin = () => state.user && state.user.role === 'ADMIN';

/* ------------------------------------------------------------ auth screen */

function showAuth(message) {
    $('#app-view').hidden = true;
    $('#auth-view').hidden = false;
    switchAuthTab('login');
    showAlert($('#auth-message'), message, 'info');
}

function switchAuthTab(tab) {
    const isLogin = tab === 'login';
    $('#tab-login').classList.toggle('active', isLogin);
    $('#tab-register').classList.toggle('active', !isLogin);
    $('#tab-login').setAttribute('aria-selected', String(isLogin));
    $('#tab-register').setAttribute('aria-selected', String(!isLogin));
    $('#login-form').hidden = !isLogin;
    $('#register-form').hidden = isLogin;
    showAlert($('#auth-message'), '');
    clearFormErrors($('#login-form'));
    clearFormErrors($('#register-form'));
}

async function submitAuthForm(event, path, buildBody) {
    event.preventDefault();
    const form = event.currentTarget;
    clearFormErrors(form);
    showAlert($('#auth-message'), '');
    await withBusy(form, async () => {
        try {
            const auth = await api(path, { method: 'POST', body: buildBody(form.elements) });
            form.reset();
            await startSession(auth);
        } catch (error) {
            const { hasFieldErrors, unmatched } = applyFormErrors(form, error);
            if (!hasFieldErrors || unmatched.length) {
                showAlert($('#auth-message'), unmatched.length ? unmatched.join('. ') : error.message);
            }
        }
    });
}

async function startSession(auth) {
    state.token = auth.accessToken;
    state.user = auth.user;
    tokenStore.set(auth.accessToken);
    await enterApp();
}

function signOut(message) {
    state.token = null;
    state.user = null;
    state.users = [];
    tokenStore.clear();
    const dialog = $('#task-dialog');
    if (dialog.open) dialog.close();
    showAuth(message);
}

/* --------------------------------------------------------------- app shell */

async function enterApp() {
    $('#auth-view').hidden = true;
    $('#app-view').hidden = false;

    $('#current-user-name').textContent = state.user.fullName;
    const roleBadge = $('#current-user-role');
    roleBadge.textContent = state.user.role;
    roleBadge.className = `badge badge-${state.user.role}`;

    const admin = isAdmin();
    $('#nav-users').hidden = !admin;
    $('#filter-assignee').hidden = !admin;
    $('#tasks-subtitle').textContent = admin
        ? 'All tasks across the team.'
        : 'Tasks you created or that are assigned to you.';

    resetTaskFilters();
    showView('tasks');
    if (admin) await loadUsers();
    await loadTasks();
}

function showView(view) {
    $('#tasks-view').hidden = view !== 'tasks';
    $('#users-view').hidden = view !== 'users';
    document.querySelectorAll('.nav-link').forEach((link) => {
        link.classList.toggle('active', link.dataset.view === view);
    });
}

/* ------------------------------------------------------------------ tasks */

function resetTaskFilters() {
    $('#filter-search').value = '';
    $('#filter-status').value = '';
    $('#filter-priority').value = '';
    $('#filter-assignee').value = '';
    $('#sort').value = 'createdAt,desc';
    state.page = 0;
}

function taskQuery() {
    const params = new URLSearchParams({ page: String(state.page), size: String(state.size), sort: $('#sort').value });
    const search = $('#filter-search').value.trim();
    if (search) params.set('search', search);
    if ($('#filter-status').value) params.set('status', $('#filter-status').value);
    if ($('#filter-priority').value) params.set('priority', $('#filter-priority').value);
    if (isAdmin() && $('#filter-assignee').value) params.set('assigneeId', $('#filter-assignee').value);
    return params.toString();
}

let latestTaskRequest = 0;

async function loadTasks() {
    const requestId = ++latestTaskRequest;
    try {
        const page = await api(`/api/tasks?${taskQuery()}`);
        if (requestId !== latestTaskRequest) return; // a newer request (e.g. while typing) has superseded this one
        if (page.content.length === 0 && state.page > 0) {
            // The current page no longer exists (e.g. its last task was deleted): step back.
            state.page = Math.max(page.totalPages - 1, 0);
            await loadTasks();
            return;
        }
        renderTasks(page);
    } catch (error) {
        showError(error);
    }
}

function reloadFromFirstPage() {
    state.page = 0;
    loadTasks();
}

function renderTasks(page) {
    $('#task-rows').replaceChildren(...page.content.map(taskRow));
    $('#tasks-empty').hidden = page.content.length > 0;

    const from = page.page * page.size + 1;
    const to = page.page * page.size + page.content.length;
    $('#page-info').textContent = page.totalElements === 0
        ? 'No tasks'
        : `Showing ${from}–${to} of ${page.totalElements}`;
    $('#page-number').textContent = `Page ${page.page + 1} of ${Math.max(page.totalPages, 1)}`;
    $('#prev-page').disabled = page.first;
    $('#next-page').disabled = page.last;
}

function taskRow(task) {
    const statusSelect = el('select', {
            className: `status-select status-${task.status}`,
            'aria-label': `Status of ${task.title}`,
        },
        Object.entries(STATUS_LABELS).map(([value, label]) => el('option', { value, text: label })));
    statusSelect.value = task.status;
    statusSelect.addEventListener('change', () => changeStatus(task, statusSelect));

    const due = task.dueDate
        ? el('span', { className: task.overdue ? 'overdue' : undefined, text: formatDate(task.dueDate) })
        : el('span', { className: 'muted', text: '—' });

    const actions = el('div', { className: 'row-actions' },
        el('button', { type: 'button', className: 'btn btn-ghost btn-sm', text: 'Edit', onclick: () => openTaskDialog(task) }),
        isAdmin() ? el('button', { type: 'button', className: 'btn btn-danger btn-sm', text: 'Delete', onclick: () => deleteTask(task) }) : null);

    return el('tr', { 'data-task-id': task.id },
        el('td', {},
            el('div', { className: 'task-title', text: task.title }),
            task.description ? el('div', { className: 'task-desc', title: task.description, text: task.description }) : null),
        el('td', {}, statusSelect),
        el('td', {}, el('span', { className: `badge priority-${task.priority}`, text: PRIORITY_LABELS[task.priority] })),
        el('td', {}, task.assignee ? task.assignee.fullName : el('span', { className: 'muted', text: 'Unassigned' })),
        el('td', {}, due, task.overdue ? el('span', { className: 'badge overdue-badge', text: 'Overdue' }) : null),
        el('td', { className: 'actions-col' }, actions));
}

async function changeStatus(task, select) {
    const newStatus = select.value;
    select.disabled = true;
    try {
        await api(`/api/tasks/${task.id}/status`, { method: 'PATCH', body: { status: newStatus } });
        toast(`"${task.title}" moved to ${STATUS_LABELS[newStatus]}`);
        await loadTasks();
    } catch (error) {
        select.value = task.status;
        select.disabled = false;
        showError(error);
    }
}

async function deleteTask(task) {
    if (!window.confirm(`Delete "${task.title}"? This cannot be undone.`)) return;
    try {
        await api(`/api/tasks/${task.id}`, { method: 'DELETE' });
        toast('Task deleted');
        await loadTasks();
    } catch (error) {
        showError(error);
    }
}

/* ------------------------------------------------------------ task dialog */

async function openTaskDialog(listTask = null) {
    let task = listTask;
    if (task) {
        try {
            task = await api(`/api/tasks/${task.id}`); // latest data and version for optimistic locking
        } catch (error) {
            showError(error);
            if (error.status === 404) loadTasks();
            return;
        }
    }
    state.editingTask = task;

    const form = $('#task-form');
    form.reset();
    clearFormErrors(form);
    showAlert($('#task-form-alert'), '');
    $('#task-dialog-title').textContent = task ? 'Edit task' : 'New task';
    $('#task-submit').textContent = task ? 'Save changes' : 'Create task';

    const fields = form.elements;
    fields.title.value = task ? task.title : '';
    fields.description.value = task && task.description ? task.description : '';
    fields.priority.value = task ? task.priority : 'MEDIUM';
    fields.status.value = task ? task.status : 'TODO';
    fields.dueDate.value = task && task.dueDate ? task.dueDate : '';
    const today = todayIso();
    fields.dueDate.min = task && task.dueDate && task.dueDate < today ? task.dueDate : today;

    await setUpAssigneeField(task);
    $('#task-dialog').showModal();
    fields.title.focus();
}

async function setUpAssigneeField(task) {
    const select = $('#assignee-select');
    const readonly = $('#assignee-readonly');

    if (!isAdmin()) {
        // Users cannot (re)assign tasks: new tasks are assigned to them, existing assignees stay unchanged.
        select.hidden = true;
        readonly.hidden = false;
        readonly.value = task
            ? (task.assignee ? task.assignee.fullName : 'Unassigned')
            : `${state.user.fullName} (you)`;
        return;
    }

    await loadUsers();
    select.hidden = false;
    readonly.hidden = true;
    const options = state.users.map((user) => el('option', { value: String(user.id), text: `${user.fullName} (${user.email})` }));
    if (task && task.assignee && !state.users.some((user) => user.id === task.assignee.id)) {
        options.push(el('option', { value: String(task.assignee.id), text: task.assignee.fullName }));
    }
    select.replaceChildren(el('option', { value: '', text: 'Unassigned' }), ...options);
    select.value = task && task.assignee ? String(task.assignee.id) : '';
}

async function submitTaskForm(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const task = state.editingTask;
    const fields = form.elements;
    clearFormErrors(form);
    showAlert($('#task-form-alert'), '');

    const payload = {
        title: fields.title.value.trim(),
        description: fields.description.value.trim() || null,
        priority: fields.priority.value,
        status: fields.status.value,
        dueDate: fields.dueDate.value || null,
    };
    if (isAdmin()) {
        payload.assigneeId = fields.assigneeId.value ? Number(fields.assigneeId.value) : null;
    } else if (task) {
        payload.assigneeId = task.assignee ? task.assignee.id : null;
    }
    if (task) payload.version = task.version;

    await withBusy(form, async () => {
        try {
            if (task) {
                await api(`/api/tasks/${task.id}`, { method: 'PUT', body: payload });
                toast('Task updated');
            } else {
                await api('/api/tasks', { method: 'POST', body: payload });
                toast('Task created');
                state.page = 0;
            }
            $('#task-dialog').close();
            await loadTasks();
        } catch (error) {
            if (error.handled) return;
            const { hasFieldErrors, unmatched } = applyFormErrors(form, error);
            if (!hasFieldErrors || unmatched.length) {
                showAlert($('#task-form-alert'), unmatched.length ? unmatched.join('. ') : error.message);
            }
        }
    });
}

/* ------------------------------------------------------------------ users */

async function loadUsers() {
    try {
        const page = await api('/api/users?size=100&sort=fullName,asc');
        state.users = page.content;
        renderAssigneeFilter();
    } catch (error) {
        showError(error);
    }
}

function renderAssigneeFilter() {
    const select = $('#filter-assignee');
    const current = select.value;
    select.replaceChildren(
        el('option', { value: '', text: 'All assignees' }),
        ...state.users.map((user) => el('option', { value: String(user.id), text: user.fullName })));
    select.value = state.users.some((user) => String(user.id) === current) ? current : '';
}

function renderUsers() {
    $('#user-rows').replaceChildren(...state.users.map((user) => {
        const isSelf = user.id === state.user.id;
        const roleSelect = el('select', { 'aria-label': `Role of ${user.fullName}`, disabled: isSelf },
            ['USER', 'ADMIN'].map((role) => el('option', { value: role, text: role })));
        roleSelect.value = user.role;
        roleSelect.addEventListener('change', () => changeRole(user, roleSelect));

        return el('tr', {},
            el('td', {}, el('span', { className: 'task-title', text: user.fullName }), isSelf ? el('span', { className: 'muted', text: ' (you)' }) : null),
            el('td', { text: user.email }),
            el('td', { text: formatInstant(user.createdAt) }),
            el('td', {}, roleSelect));
    }));
}

async function changeRole(user, select) {
    const newRole = select.value;
    if (!window.confirm(`Change ${user.fullName}'s role to ${newRole}?`)) {
        select.value = user.role;
        return;
    }
    try {
        const updated = await api(`/api/users/${user.id}/role`, { method: 'PATCH', body: { role: newRole } });
        user.role = updated.role;
        toast(`${user.fullName} is now ${updated.role}`);
    } catch (error) {
        select.value = user.role;
        showError(error);
    }
}

/* ----------------------------------------------------------------- wiring */

function bindEvents() {
    $('#tab-login').addEventListener('click', () => switchAuthTab('login'));
    $('#tab-register').addEventListener('click', () => switchAuthTab('register'));

    $('#login-form').addEventListener('submit', (event) => submitAuthForm(event, '/api/auth/login',
        (fields) => ({ email: fields.email.value.trim(), password: fields.password.value })));
    $('#register-form').addEventListener('submit', (event) => submitAuthForm(event, '/api/auth/register',
        (fields) => ({ fullName: fields.fullName.value.trim(), email: fields.email.value.trim(), password: fields.password.value })));

    document.querySelectorAll('[data-demo]').forEach((button) => {
        button.addEventListener('click', () => {
            const account = DEMO_ACCOUNTS[button.dataset.demo];
            const form = $('#login-form');
            form.elements.email.value = account.email;
            form.elements.password.value = account.password;
            form.requestSubmit();
        });
    });

    $('#logout-btn').addEventListener('click', () => signOut());

    document.querySelectorAll('.nav-link').forEach((link) => {
        link.addEventListener('click', async () => {
            showView(link.dataset.view);
            if (link.dataset.view === 'users') {
                await loadUsers();
                renderUsers();
            } else {
                await loadTasks();
            }
        });
    });

    $('#new-task-btn').addEventListener('click', () => openTaskDialog());
    $('#task-form').addEventListener('submit', submitTaskForm);
    document.querySelectorAll('#task-dialog [data-close]').forEach((button) => {
        button.addEventListener('click', () => $('#task-dialog').close());
    });

    $('#filter-search').addEventListener('input', debounce(reloadFromFirstPage, 300));
    ['#filter-status', '#filter-priority', '#filter-assignee', '#sort'].forEach((selector) => {
        $(selector).addEventListener('change', reloadFromFirstPage);
    });
    $('#page-size').addEventListener('change', (event) => {
        state.size = Number(event.target.value);
        reloadFromFirstPage();
    });
    $('#prev-page').addEventListener('click', () => {
        if (state.page > 0) {
            state.page -= 1;
            loadTasks();
        }
    });
    $('#next-page').addEventListener('click', () => {
        state.page += 1;
        loadTasks();
    });
}

async function init() {
    bindEvents();
    const token = tokenStore.get();
    if (!token) {
        showAuth();
        return;
    }
    state.token = token;
    try {
        state.user = await api('/api/auth/me');
        await enterApp();
    } catch (error) {
        if (!error.handled) signOut(error.message);
    }
}

init();
