const form = document.querySelector("#career-form");
const scansElement = document.querySelector("#scans");
const detailElement = document.querySelector("#scan-detail");
const messageElement = document.querySelector("#form-message");
const refreshButton = document.querySelector("#refresh");
const aiStatus = document.querySelector("#ai-status");
let selectedId = null;
let scans = [];
let matches = new Map();
let aiConfigured = false;

const escapeHtml = (value) => String(value ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#039;");

async function request(path, options) {
  const response = await fetch(path, { ...options, headers: { "Content-Type": "application/json", ...(options?.headers ?? {}) } });
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
  messageElement.textContent = "Creating a durable career frontier…";
  try {
    const created = await request("/api/v1/career-scans", { method: "POST", body: JSON.stringify({
      careersUrl: document.querySelector("#careers-url").value,
      maxPages: Number(document.querySelector("#max-pages").value),
      maxDepth: Number(document.querySelector("#max-depth").value),
      requestsPerSecond: Number(document.querySelector("#requests-per-second").value),
    }) });
    selectedId = created.id;
    messageElement.textContent = `Career scan ${created.id.slice(0, 8)} started.`;
    await loadScans();
  } catch (error) { messageElement.textContent = error.message; }
  finally { submit.disabled = false; }
});

refreshButton.addEventListener("click", loadScans);
scansElement.addEventListener("click", async (event) => {
  const button = event.target.closest("button[data-scan-id]");
  if (!button) return;
  selectedId = button.dataset.scanId; renderScans(); await renderDetail();
});

detailElement.addEventListener("click", async (event) => {
  const action = event.target.closest("[data-action]");
  if (!action || !selectedId) return;
  if (action.dataset.action === "match") return matchJobs(action);
  action.disabled = true;
  try { await request(`/api/v1/crawls/${selectedId}/${action.dataset.action}`, { method: "POST" }); await loadScans(); }
  catch (error) { messageElement.textContent = error.message; }
});

async function matchJobs(button) {
  const resumeText = document.querySelector("#resume-text").value.trim();
  if (resumeText.length < 80) { messageElement.textContent = "Paste at least 80 characters of resume text before matching."; return; }
  button.disabled = true; button.textContent = "Matching…";
  try {
    const report = await request(`/api/v1/career-scans/${selectedId}/match`, { method: "POST", body: JSON.stringify({ resumeText, useAi: aiConfigured && document.querySelector("#use-ai").checked }) });
    matches = new Map(report.matches.map((match) => [match.jobId, match]));
    messageElement.textContent = `${report.method}: ${report.notice}`;
    await renderDetail();
  } catch (error) { messageElement.textContent = error.message; button.disabled = false; }
}

async function loadScans() {
  try { scans = await request("/api/v1/career-scans"); if (!selectedId && scans.length) selectedId = scans[0].id; renderScans(); await renderDetail(); }
  catch (error) { scansElement.innerHTML = `<p class="empty">${escapeHtml(error.message)}</p>`; }
}

function renderScans() {
  if (!scans.length) { scansElement.innerHTML = '<p class="empty">No career scans yet.</p>'; return; }
  scansElement.innerHTML = scans.map((scan) => `<button class="job ${scan.id === selectedId ? "selected" : ""}" data-scan-id="${scan.id}">
    <strong>${escapeHtml(scan.rootHost)}</strong><span class="status ${scan.status}">${scan.status}</span>
    <small>${scan.jobsFound} jobs · ${scan.pagesCrawled} pages</small><small>${new Date(scan.createdAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}</small></button>`).join("");
}

async function renderDetail() {
  const scan = scans.find((item) => item.id === selectedId);
  if (!scan) { detailElement.innerHTML = '<p class="empty">Run or select a scan to inspect structured jobs.</p>'; return; }
  const jobs = await request(`/api/v1/career-scans/${scan.id}/jobs`);
  const controls = ["RUNNING", "QUEUED"].includes(scan.status)
    ? '<button class="secondary" data-action="pause">Pause</button><button class="secondary" data-action="cancel">Cancel</button>'
    : ["PAUSED", "FAILED"].includes(scan.status) ? '<button data-action="resume">Resume</button><button class="secondary" data-action="cancel">Cancel</button>' : "";
  detailElement.innerHTML = `<div class="detail-head"><div><h3>${escapeHtml(scan.rootHost)}</h3><div class="detail-meta">${scan.jobsFound} jobs · ${scan.pagesCrawled} pages · ${scan.pagesFailed} failed</div></div>
    <div class="detail-actions">${controls}<a class="button secondary-link" href="/api/v1/career-scans/${scan.id}/jobs.json">JSON</a><a class="button secondary-link" href="/api/v1/career-scans/${scan.id}/jobs.csv">CSV</a><button data-action="match" ${jobs.length ? "" : "disabled"}>Match resume</button></div></div>
    ${jobs.length ? jobs.map(renderJob).join("") : `<p class="empty">${["RUNNING", "QUEUED"].includes(scan.status) ? "Discovering job pages…" : "No job postings were detected. Try the company's direct jobs listing or ATS URL."}</p>`}`;
}

function renderJob(job) {
  const match = matches.get(job.id);
  const skills = job.skills.length ? job.skills.map((skill) => `<span class="chip">${escapeHtml(skill)}</span>`).join("") : '<span class="detail-meta">No recognized skills</span>';
  const description = job.description || "No description extracted.";
  return `<article class="posting"><div class="posting-top"><div><a href="${escapeHtml(job.sourceUrl)}" target="_blank" rel="noreferrer"><h4>${escapeHtml(job.title)}</h4></a>
    <p class="posting-meta">${escapeHtml(job.company || "Company not specified")} · ${escapeHtml(job.location || "Location not specified")} · ${escapeHtml(job.experience || "Experience not specified")}</p></div>
    ${match ? `<span class="score">${match.score}<small>/100</small></span>` : `<span class="method">${escapeHtml(job.extractionMethod)}</span>`}</div><div class="chips">${skills}</div>
    ${match ? `<p class="match-summary">${escapeHtml(match.summary)}<br><strong>Matched:</strong> ${escapeHtml(match.matchedSkills.join(", ") || "none")} · <strong>Missing:</strong> ${escapeHtml(match.missingSkills.join(", ") || "none")}</p>` : ""}
    <p class="description">${escapeHtml(description.slice(0, 650))}${description.length > 650 ? "…" : ""}</p></article>`;
}

async function loadCapabilities() {
  const checkbox = document.querySelector("#use-ai");
  try { const value = await request("/api/v1/career-scans/capabilities"); aiConfigured = value.aiMatchingConfigured; checkbox.disabled = !aiConfigured; aiStatus.textContent = aiConfigured ? `AI ready · ${value.model}` : "Deterministic matching ready · AI key not configured"; }
  catch { checkbox.disabled = true; aiStatus.textContent = "Deterministic matching ready"; }
}

loadCapabilities(); loadScans();
setInterval(() => { if (scans.some((scan) => ["RUNNING", "QUEUED"].includes(scan.status))) loadScans(); }, 1500);
