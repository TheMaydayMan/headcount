// Signing in. Studio is served by the Minecraft server, so everything but these files needs the password that
// /hca studio shows. signIn() covers the page with a small form and resolves once the server has taken the password.
// It's also used when a session runs out mid-visit, so unsaved edits stay where they are.

let pending = null;

const css = `
.signin { position: fixed; inset: 0; z-index: 1000; display: grid; place-items: center; padding: 16px; background: var(--bg); }
.signin form { width: min(380px, 100%); background: var(--surface); border: 1px solid var(--line); border-radius: var(--r); box-shadow: var(--shadow); padding: 26px 24px 22px; display: flex; flex-direction: column; gap: 14px; }
.signin h1 { margin: 0; font-size: 22px; font-weight: 600; display: flex; align-items: center; gap: 10px; }
.signin h1 svg { width: 30px; height: 30px; flex: none; }
.signin p { margin: 0; color: var(--ink-2); line-height: 1.45; }
.signin code { font-family: var(--mono); font-size: 13.5px; background: var(--sunken); padding: 1px 5px; border-radius: 4px; }
.signin input { width: 100%; font-family: var(--mono); letter-spacing: .04em; }
.signin .err { color: var(--bad); min-height: 1.4em; font-size: 14px; }
.signin .btn { width: 100%; min-height: 40px; }
`;

function mark() {
  // The same head as the tab icon (index.html).
  const ns = "http://www.w3.org/2000/svg";
  const svg = document.createElementNS(ns, "svg");
  svg.setAttribute("viewBox", "0 0 16 16");
  svg.setAttribute("shape-rendering", "crispEdges");
  svg.innerHTML = "<rect width='16' height='16' rx='3.5' style='fill:var(--accent)' shape-rendering='geometricPrecision'/><rect x='2' y='2' width='12' height='12' fill='#f1d3b3'/>"
    + "<path fill='#162027' d='M2 2h12v3H2zM2 5h1v2H2zM13 5h1v2h-1zM5 8h1v2H5zM10 8h1v2h-1z'/><path fill='#fff' d='M4 8h1v2H4zM11 8h1v2h-1z'/><path fill='#a8674f' d='M6 11h4v1H6z'/>";
  return svg;
}

/** Shows the sign-in form; resolves when signed in. Calls made while it's open wait for the same sign-in. */
export function signIn(message) {
  if (pending) return pending;
  pending = new Promise((resolve) => {
    if (!document.getElementById("signin-css")) {
      const st = document.createElement("style");
      st.id = "signin-css";
      st.textContent = css;
      document.head.append(st);
    }
    const wrap = document.createElement("div");
    wrap.className = "signin";
    const form = document.createElement("form");
    const h = document.createElement("h1");
    h.append(mark(), "Headcount Studio");
    const p = document.createElement("p");
    p.innerHTML = message || "Enter the password that <code>/hca studio</code> shows in game or in the server console.";
    const input = document.createElement("input");
    Object.assign(input, { type: "password", className: "input", autocomplete: "current-password", placeholder: "xxxx-xxxx-xxxx-xxxx", required: true });
    input.setAttribute("aria-label", "Password");
    const err = document.createElement("div");
    err.className = "err";
    err.setAttribute("role", "alert");
    const go = document.createElement("button");
    go.className = "btn primary";
    go.type = "submit";
    go.textContent = "Sign in";
    form.append(h, p, input, err, go);
    wrap.append(form);
    document.body.append(wrap);
    input.focus();
    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      go.disabled = true;
      err.textContent = "";
      try {
        const r = await fetch("/api/login", { method: "POST", headers: { "Content-Type": "application/json", "X-Studio": "1" }, body: JSON.stringify({ password: input.value }) });
        const j = await r.json().catch(() => ({}));
        if (!r.ok) throw new Error(j.error || r.statusText);
        wrap.remove();
        pending = null;
        resolve();
      } catch (ex) {
        err.textContent = ex.message === "Failed to fetch" ? "Can't reach the server. Is it running?" : ex.message;
        input.select();
      } finally {
        go.disabled = false;
      }
    });
  });
  return pending;
}

/** Before the app loads: sign in first if this browser isn't. */
export async function ensureSignedIn() {
  let s = null;
  try { s = await (await fetch("/api/session")).json(); } catch (e) { /* the request below shows the problem */ }
  if (s && s.hosted && !s.signedIn) {
    await signIn();
    // The stylesheet asked for Minecraft's font before there was a session, and browsers don't ask twice.
    try { document.fonts.add(await new FontFace("Minecraft Studio", "url(/mc/font/minecraft.ttf)", { display: "block" }).load()); } catch (e) { /* not downloaded yet */ }
  }
}

export async function signOut() {
  try { await fetch("/api/logout", { method: "POST", headers: { "Content-Type": "application/json", "X-Studio": "1" }, body: "{}" }); } catch (e) { /* gone anyway */ }
}
