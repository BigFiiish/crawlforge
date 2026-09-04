const form = document.querySelector("#crawl-form");
const jobsElement = document.querySelector("#jobs");
const detailElement = document.querySelector("#job-detail");
const messageElement = document.querySelector("#form-message");
const refreshButton = document.querySelector("#refresh");

let selectedId = null;
let jobs = [];

const escapeHtml = (value) => String(value ?? "")
  .replaceAll("&", "&amp;")
  .replaceAll("<", "&lt;")
  .replaceAll(">", "&gt;")
  .replaceAll('"', "&quot;")
  .replaceAll("'", "&#039;");

async function request(path, options) {
  const response = await fetch(path, {
    ...options,
    headers: { "Content-Type": "application/json", ...(options?.headers ?? {}) },
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: `HTTP ${response.status}` }));
    throw new Error(error.message ?? `HTTP ${response.status}`);
  }
  return response.json();
}

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  const submit = form.querySelector("button[type=submit]");
  submit.disabled = true;
  messageElement.textContent = "Creating a durable frontier…";
  try {
    const created = await request("/api/v1/crawls", {
      method: "POST",
      body: JSON.stringify({
        seedUrl: document.querySelector("#seed-url").value,
        maxPages: Number(document.querySelector("#max-pages").value),
        maxDepth: Number(document.querySelector("#max-depth").value),
        requestsPerSecond: Number(document.querySelector("#requests-per-second").value),
        sameHostOnly: document.querySelector("#same-host").checked,
        respectRobots: document.querySelector("#respect-robots").checked,
      }),
    });
    selectedId = created.id;
    messageElement.textContent = `Started ${created.id.slice(0, 8)}.`;
    await loadJobs();
  } catch (error) {
    messageElement.textContent = error.message;
  } finally {
    submit.disabled = false;
  }
});

refreshButton.addEventListener("click", loadJobs);

jobsElement.addEventListener("click", async (event) => {
  const button = event.target.closest("button[data-job-id]");
  if (!button) return;
  selectedId = button.dataset.jobId;
  renderJobs();
  await renderDetail();
});

detailElement.addEventListener("click", async (event) => {
  const button = event.target.closest("button[data-action]");
  if (!button || !selectedId) return;
  button.disabled = true;
  try {
    await request(`/api/v1/crawls/${selectedId}/${button.dataset.action}`, { method: "POST" });
    await loadJobs();
  } catch (error) {
    messageElement.textContent = error.message;
  }
});

async function loadJobs() {
  try {
    jobs = await request("/api/v1/crawls");
    if (!selectedId && jobs.length) selectedId = jobs[0].id;
    renderJobs();
    await renderDetail();
  } catch (error) {
    jobsElement.innerHTML = `<p class="empty">${escapeHtml(error.message)}</p>`;
  }
}

function renderJobs() {
  if (!jobs.length) {
    jobsElement.innerHTML = '<p class="empty">No crawl jobs yet.</p>';
    return;
  }
  jobsElement.innerHTML = jobs.map((job) => `
    <button class="job ${job.id === selectedId ? "selected" : ""}" data-job-id="${job.id}">
      <strong>${escapeHtml(job.rootHost)}</strong>
      <span class="status ${job.status}">${job.status}</span>
      <small>${job.pagesCrawled}/${job.maxPages} pages · depth ${job.maxDepth}</small>
      <small>${new Date(job.createdAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}</small>
    </button>
  `).join("");
}

async function renderDetail() {
  const job = jobs.find((candidate) => candidate.id === selectedId);
  if (!job) {
    detailElement.innerHTML = '<p class="empty">Select a crawl to inspect its pages.</p>';
    return;
  }
  const pages = await request(`/api/v1/crawls/${job.id}/pages?limit=100`);
  const controls = ["RUNNING", "QUEUED"].includes(job.status)
    ? '<button class="secondary" data-action="pause">Pause</button><button class="secondary" data-action="cancel">Cancel</button>'
    : ["PAUSED", "FAILED"].includes(job.status)
      ? '<button data-action="resume">Resume</button><button class="secondary" data-action="cancel">Cancel</button>'
      : "";

  detailElement.innerHTML = `
    <div class="detail-head">
      <div>
        <h3>${escapeHtml(job.rootHost)}</h3>
        <div class="detail-meta">${job.pagesCrawled} crawled · ${job.pagesFailed} failed · ${job.requestsPerSecond} req/s</div>
      </div>
      <div class="detail-actions">${controls}</div>
    </div>
    ${job.errorMessage ? `<p class="detail-meta">${escapeHtml(job.errorMessage)}</p>` : ""}
    ${pages.length ? pages.map((page) => `
      <article class="page">
        <div class="page-title">
          <a href="${escapeHtml(page.url)}" target="_blank" rel="noreferrer">${escapeHtml(page.title || page.url)}</a>
          <span class="detail-meta">depth ${page.depth}</span>
        </div>
        <p>${escapeHtml(page.textPreview || "No text extracted.")}</p>
      </article>
    `).join("") : '<p class="empty">Waiting for the first HTML page…</p>'}
  `;
}

loadJobs();
setInterval(() => {
  if (jobs.some((job) => job.status === "RUNNING" || job.status === "QUEUED")) loadJobs();
}, 1200);
