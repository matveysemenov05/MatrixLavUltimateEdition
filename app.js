// ============================================================================
// APP.JS — ИНТЕРФЕЙС, ДИАЛОГИ ТАУ, ВХОД-ВЫХОД W(s) И ЭКСПОРТ PDF / LATEX
// ============================================================================
const math = new MatrixMath();
window._embeddedReportMatrices = [];

function parseSmartCell(raw) {
  const s = String(raw || "").trim().replace(/[−–—]/g, '-').replace(',', '.').replace(/\s+/g, '');
  if (!s || s === "-") return 0.0;
  if (!Number.isNaN(Number(s))) return Number(s);
  if (s.includes('/')) {
    const [n, d] = s.split('/');
    const nv = parseSmartCell(n), dv = parseSmartCell(d);
    if (nv !== null && dv !== null && Math.abs(dv) > 1e-15) return nv / dv;
  }
  if (s === 'e') return Math.E;
  if (s === '-e') return -Math.E;
  if (s.startsWith('e^')) return Math.exp(Number(s.slice(2)) || 0);
  if (s.startsWith('√')) return Math.sqrt(Math.max(0, Number(s.slice(1)) || 0));
  return 0.0;
}

function formatNumber(v, dec = 3) {
  if (!Number.isFinite(v)) return String(v);
  if (Math.abs(v) < 1e-9) return "0";
  return Number(v.toFixed(dec)).toString();
}

function toSuperscriptStr(str) {
  const map = { '0':'⁰','1':'¹','2':'²','3':'³','4':'⁴','5':'⁵','6':'⁶','7':'⁷','8':'⁸','9':'⁹','+':'⁺','-':'⁻','−':'⁻','n':'ⁿ','k':'ᵏ','m':'ᵐ','T':'ᵀ' };
  return String(str).split('').map(c => map[c] || c).join('');
}

function prettifyWebMath(input) {
  if (!input) return "";
  const subMap = { '0':'₀','1':'₁','2':'₂','3':'₃','4':'₄','5':'₅','6':'₆','7':'₇','8':'₈','9':'₉','+':'₊','-':'₋','−':'₋','i':'ᵢ','j':'ⱼ','k':'ₖ','n':'ₙ','m':'ₘ','c':'꜀','o':'ₒ','u':'ᵤ','a':'ₐ','p':'ₚ' };
  const toSub = (str) => String(str).split('').map(c => subMap[c] || c).join('');
  return String(input)
    .replace(/\^\{([^}]+)\}/g, (_, g) => toSuperscriptStr(g))
    .replace(/\^\(([^)]+)\)/g, (_, g) => "⁽" + toSuperscriptStr(g) + "⁾")
    .replace(/_\{([^}]+)\}/g, (_, g) => toSub(g))
    .replace(/_([0-9ijknmcouap]+)/g, (_, g) => toSub(g));
}

function gcdNum(a0, b0) {
  let a = Math.abs(Math.round(a0)), b = Math.abs(Math.round(b0));
  while (b !== 0) { const t = a % b; a = b; b = t; }
  return a === 0 ? 1 : a;
}

function toSymbolicToken(value, isExpMode = false) {
  if (!Number.isFinite(value)) return { plainText: String(value) };
  if (Math.abs(value) < 1e-9) return { plainText: "0" };
  const nearestInt = Math.round(value);
  if (Math.abs(value - nearestInt) < 1e-6 && Math.abs(nearestInt) < 1e9) {
    return { plainText: String(nearestInt) };
  }

  // Находит лучшую цепную дробь p/q со знаменателем <= maxQ для ЛЮБОГО числа
  function findBestFraction(x, maxQ, relTol) {
    const ax = Math.abs(x);
    if (ax < 1e-7 || ax > 1e7) return null;
    let h1 = 1, h2 = 0, k1 = 0, k2 = 1, b = ax;
    for (let step = 0; step < 18; step++) {
      const a = Math.floor(b), h = a * h1 + h2, k = a * k1 + k2;
      if (k > maxQ) break;
      h2 = h1; h1 = h; k2 = k1; k1 = k;
      const rem = b - a;
      if (rem < 1e-11) break;
      b = 1.0 / rem;
    }
    if (k1 <= 1 || h1 === 0) return null;
    const relErr = Math.abs(ax - h1 / k1) / Math.max(1.0, ax);
    return relErr <= relTol ? [h1, k1] : null;
  }

  const signStr = value < 0 ? "−" : "", absVal = Math.abs(value);

  // 1. РЕЖИМ ЭКСПОНЕНТЫ e^A: гарантированно переводим КАЖДУЮ ячейку в форму с e^k и дробью!
  if (isExpMode) {
    const k0 = Math.max(-20, Math.min(30, Math.round(Math.log(absVal))));
    const localPowers = [];
    for (let d = -3; d <= 2; d++) {
      const k = k0 + d;
      if (k !== 0) localPowers.push(k);
    }
    for (const k of [1, 2, 3, -1, -2]) {
      if (!localPowers.includes(k)) localPowers.push(k);
    }

    // 1а. Ищем точное выражение (a * e^k + b) / q
    for (const k of localPowers) {
      const ePow = Math.exp(k), eStr = k === 1 ? "e" : "e" + toSuperscriptStr(k);
      for (let q = 1; q <= 12; q++) {
        const scaled = value * q;
        for (let a = -16; a <= 16; a++) {
          if (a === 0) continue;
          const b = Math.round(scaled - a * ePow);
          if (Math.abs(b) > 16) continue;
          if (Math.abs(value - (a * ePow + b) / q) / Math.max(1.0, absVal) <= 1e-4) {
            const g = gcdNum(gcdNum(a, b), q), a1 = a / g, b1 = b / g, q1 = q / g;
            const aTerm = a1 === 1 ? eStr : a1 === -1 ? "−" + eStr : a1 + eStr;
            const expr = b1 === 0 ? aTerm : b1 > 0 ? `${aTerm}+${b1}` : `${aTerm}−${Math.abs(b1)}`;
            if (q1 === 1) return { plainText: expr };
            if (b1 === 0) return { isFraction: true, sign: a1 < 0 ? "−" : "", num: String(Math.abs(a1)), den: String(q1), suffix: eStr };
            return { isFraction: true, sign: "", num: expr, den: String(q1), suffix: "" };
          }
        }
      }
    }

    // 1б. Ищем комбинацию двух экспонент (a * e^k1 + b * e^k2) / q вокруг масштаба числа k0
    let bestCombo = null, bestErr = 1e-3;
    for (let i = 0; i < localPowers.length; i++) {
      for (let j = i + 1; j < localPowers.length; j++) {
        const k1 = localPowers[i], k2 = localPowers[j];
        const e1 = Math.exp(k1), e2 = Math.exp(k2);
        const s1 = k1 === 1 ? "e" : "e" + toSuperscriptStr(k1);
        const s2 = k2 === 1 ? "e" : "e" + toSuperscriptStr(k2);
        for (let q = 1; q <= 9; q++) {
          const scaled = value * q;
          for (let a = -9; a <= 9; a++) {
            if (a === 0) continue;
            const b = Math.round((scaled - a * e1) / e2);
            if (b === 0 || Math.abs(b) > 9) continue;
            const err = Math.abs(value - (a * e1 + b * e2) / q) / Math.max(1.0, absVal);
            if (err < bestErr) {
              bestErr = err;
              const g = gcdNum(gcdNum(a, b), q), a1 = a / g, b1 = b / g, q1 = q / g;
              const t1 = a1 === 1 ? s1 : a1 === -1 ? "−" + s1 : a1 + s1;
              const t2 = b1 === 1 ? "+" + s2 : b1 === -1 ? "−" + s2 : b1 > 0 ? `+${b1}${s2}` : `−${Math.abs(b1)}${s2}`;
              bestCombo = q1 === 1
                ? { plainText: `${t1}${t2}` }
                : { isFraction: true, sign: "", num: `${t1}${t2}`, den: String(q1), suffix: "" };
            }
          }
        }
      }
    }
    if (bestCombo) return bestCombo;

    // 1в. Если степени собственные не целые — представляем как дробь (p/q)·e^k0
    const kUse = k0 === 0 ? 1 : k0;
    const eStr = kUse === 1 ? "e" : "e" + toSuperscriptStr(kUse);
    const ratio = absVal / Math.exp(kUse);
    const fracE = findBestFraction(ratio, 32, 1e-2) || [Math.max(1, Math.round(ratio * 10)), 10];
    const g = gcdNum(fracE[0], fracE[1]);
    const p1 = fracE[0] / g, q1 = fracE[1] / g;
    if (q1 === 1) return { plainText: `${signStr}${p1 === 1 ? "" : p1}${eStr}` };
    return { isFraction: true, sign: signStr, num: String(p1), den: String(q1), suffix: eStr };
  }

  // 2. Короткие точные дроби со знаменателем до 16
  const f16 = findBestFraction(absVal, 16, 1e-5);
  if (f16) return { isFraction: true, sign: signStr, num: String(f16[0]), den: String(f16[1]), suffix: "" };

  // 3. Корни √2, √3, √5, √6, √7 (для небольших чисел)
  if (absVal <= 30) {
    for (const rad of [2, 3, 5, 6, 7]) {
      const rVal = Math.sqrt(rad), rStr = "√" + rad;
      for (let q = 1; q <= 12; q++) {
        const scaled = value * q;
        for (let a = -12; a <= 12; a++) {
          if (a === 0) continue;
          const b = Math.round(scaled - a * rVal);
          if (Math.abs(b) > 12) continue;
          if (Math.abs(value - (a * rVal + b) / q) / Math.max(1.0, absVal) <= 1e-6) {
            const g = gcdNum(gcdNum(a, b), q), a1 = a / g, b1 = b / g, q1 = q / g;
            const aTerm = a1 === 1 ? rStr : a1 === -1 ? "−" + rStr : a1 + rStr;
            const expr = b1 === 0 ? aTerm : `${b1}+${aTerm}`.replace("+-", "−").replace("+−", "−");
            if (q1 === 1) return { plainText: expr };
            if (b1 === 0) return { isFraction: true, sign: a1 < 0 ? "−" : "", num: String(Math.abs(a1)), den: String(q1), suffix: rStr };
            return { isFraction: true, sign: "", num: expr, den: String(q1), suffix: "" };
          }
        }
      }
    }
  }

  // 4. Для всех остальных чисел при включённом режиме «Дроби: ВКЛ» — переводим в дробь со знаменателем до 64!
  const fAny = findBestFraction(absVal, 64, 2e-3) || findBestFraction(absVal, 32, 1e-2);
  if (fAny) return { isFraction: true, sign: signStr, num: String(fAny[0]), den: String(fAny[1]), suffix: "" };

  return { plainText: formatNumber(value) };
}

function formatSymbolic(v) {
  const t = toSymbolicToken(v);
  return t.isFraction ? `${t.sign}${t.num}/${t.den}` : t.plainText;
}

function formatMatrixAligned(matrix, symbolic = false) {
  if (!matrix || !matrix.length) return "[ ]";
  const fmt = matrix.map(r => r.map(v => (symbolic ? formatSymbolic(v) : formatNumber(v))));
  const cols = fmt[0].length, widths = Array.from({ length: cols }, (_, c) => Math.max(...fmt.map(r => r[c].length)));
  return fmt.map((row, idx) => {
    const line = row.map((val, c) => val.padStart(widths[c])).join("  ");
    if (fmt.length === 1) return `[ ${line} ]`;
    const l = idx === 0 ? "⎡" : idx === fmt.length - 1 ? "⎣" : "⎢";
    const r = idx === 0 ? "⎤" : idx === fmt.length - 1 ? "⎦" : "⎥";
    return `${l} ${line} ${r}`;
  }).join("\n");
}

function characteristicPolynomialString(mat) {
  const n = mat.length; let b = math.identityMatrix(n); const coeffs = [];
  for (let k = 1; k <= n; k++) {
    const ab = math.multiply(mat, b), tr = ab.reduce((s, r, i) => s + r[i], 0), c = -tr / k;
    coeffs.push(c); b = ab.map((r, i) => r.map((v, j) => v + (i === j ? c : 0)));
  }
  let out = `λ${toSuperscriptStr(n)}`;
  coeffs.forEach((raw, idx) => {
    const p = n - idx - 1; if (Math.abs(raw) < 1e-9) return;
    const sign = raw < 0 ? " − " : " + ", mag = formatSymbolic(Math.abs(raw));
    const vStr = p === 0 ? "" : p === 1 ? "λ" : `λ${toSuperscriptStr(p)}`;
    const showC = !vStr || Math.abs(Math.abs(raw) - 1) > 1e-9;
    out += sign + (showC ? mag : "") + vStr;
  });
  return `p(λ) = ${out}`;
}

const state = {
  mainTab: 0, selTab: 0,
  algDim: [{ r: 3, c: 3 }, { r: 3, c: 3 }, { r: 3, c: 3 }],
  tauN: 3, tauM: 1, tauK: 1,
  matrices: [
    Array.from({ length: 7 }, () => new Array(7).fill("0")),
    Array.from({ length: 7 }, () => new Array(7).fill("0")),
    Array.from({ length: 7 }, () => new Array(7).fill("0"))
  ],
  scalarK: "1", resTitle: "Результат", resText: "", resMatrix: null, resGrid: null, isError: false, symbolicMode: false,
  history: JSON.parse(localStorage.getItem("matrix_lab_history_v1") || "[]")
};

function showToast(msg) {
  const el = document.getElementById("toastBox");
  el.textContent = msg; el.classList.add("show");
  setTimeout(() => el.classList.remove("show"), 2000);
}

function getDims(idx = state.selTab) {
  if (state.mainTab === 0) return state.algDim[idx];
  if (idx === 0) return { r: state.tauN, c: state.tauN };
  if (idx === 1) return { r: state.tauN, c: state.tauM };
  return { r: state.tauK, c: state.tauN };
}

function getParsedMatrix(idx = state.selTab) {
  const { r, c } = getDims(idx);
  return Array.from({ length: r }, (_, i) => Array.from({ length: c }, (_, j) => parseSmartCell(state.matrices[idx][i][j])));
}

function setDims(idx, newR, newC) {
  const r = Math.min(7, Math.max(1, newR)), c = Math.min(7, Math.max(1, newC));
  if (state.mainTab === 0) state.algDim[idx] = { r, c };
  else {
    if (idx === 0) state.tauN = Math.min(7, Math.max(2, r));
    else if (idx === 1) { state.tauN = Math.min(7, Math.max(2, r)); state.tauM = c; }
    else { state.tauK = r; state.tauN = Math.min(7, Math.max(2, c)); }
  }
  renderAll();
}

function saveHistory(title, text, mat = null) {
  state.history.unshift({ id: Date.now(), title, text, mat: mat ? mat.map(r => [...r]) : null, time: new Date().toLocaleTimeString() });
  if (state.history.length > 150) state.history.pop();
  localStorage.setItem("matrix_lab_history_v1", JSON.stringify(state.history));
  document.getElementById("historyCount").textContent = state.history.length;
}

function showMatrixResult(mat, title) {
  if (!mat || mat.some(r => r.some(v => !Number.isFinite(v)))) {
    showTextResult(title, `Не удалось выполнить операцию "${title}".\nПроверьте размеры матриц и невырожденность.`, null, null, true);
    return;
  }
  state.resTitle = title; state.resMatrix = mat; state.resGrid = null; state.resText = ""; state.isError = false;
  saveHistory(title, formatMatrixAligned(mat), mat);
  renderResultCard();
}

function showTextResult(title, text, mat = null, grid = null, err = false) {
  state.resTitle = title; state.resText = text; state.resMatrix = mat; state.resGrid = grid; state.isError = err;
  if (!err) saveHistory(title, text, mat);
  renderResultCard();
}

function transferToTarget(targetIdx, mat) {
  if (!mat || !mat.length || !mat[0].length) return;
  const r = Math.min(7, Math.max(1, mat.length)), c = Math.min(7, Math.max(1, mat[0].length));
  for (let i = 0; i < r; i++) for (let j = 0; j < c; j++) state.matrices[targetIdx][i][j] = formatNumber(mat[i][j], 5);
  setDims(targetIdx, r, c); state.selTab = targetIdx;
  renderAll();
  showToast(`Перенесено в ${["A", "B", "C"][targetIdx]} (${r}×${c})`);
}

function renderAll() {
  document.getElementById("historyCount").textContent = state.history.length;
  document.getElementById("tabMain0").className = "cat-pill" + (state.mainTab === 0 ? " active-cyan" : "");
  document.getElementById("tabMain1").className = "cat-pill" + (state.mainTab === 1 ? " active-purple" : "");

  const dA = getDims(0), dB = getDims(1), dC = getDims(2);
  const cA = document.getElementById("chipMatA"), cB = document.getElementById("chipMatB"), cC = document.getElementById("chipMatC");
  cA.textContent = `A (${dA.r}×${dA.c})`; cA.className = "mat-chip" + (state.selTab === 0 ? " sel-A" : "");
  cB.textContent = `B (${dB.r}×${dB.c})`; cB.className = "mat-chip" + (state.selTab === 1 ? " sel-B" : "");
  cC.textContent = `C (${dC.r}×${dC.c})`; cC.className = "mat-chip" + (state.selTab === 2 ? " sel-C" : "");

  const accentColors = ["var(--cyan)", "var(--purple)", "var(--amber)"];
  document.getElementById("bracketLeft").style.background = accentColors[state.selTab];
  document.getElementById("bracketRight").style.background = accentColors[state.selTab];

  renderDimControls(); renderGridInput(); renderTransferRow(); renderOperationsPanel(); renderResultCard();
}

function renderDimControls() {
  const box = document.getElementById("dimControlsContainer"), name = ["A", "B", "C"][state.selTab], { r, c } = getDims();
  if (state.mainTab === 0) {
    const dyn = Math.max(r, c, state.algDim[0].r, 2);
    box.innerHTML = `<div class="dim-row">
      <div class="dim-card"><span class="dim-label">Строк ${name}:</span><div class="counter-wrap">
        <button class="cnt-btn" ${r <= 1 ? "disabled" : ""} onclick="setDims(${state.selTab}, ${r - 1}, ${c})">−</button>
        <span class="cnt-val">${r}</span>
        <button class="cnt-btn" ${r >= 7 ? "disabled" : ""} onclick="setDims(${state.selTab}, ${r + 1}, ${c})">+</button>
      </div></div>
      <div class="dim-card"><span class="dim-label">Столб. ${name}:</span><div class="counter-wrap">
        <button class="cnt-btn" ${c <= 1 ? "disabled" : ""} onclick="setDims(${state.selTab}, ${r}, ${c - 1})">−</button>
        <span class="cnt-val">${c}</span>
        <button class="cnt-btn" ${c >= 7 ? "disabled" : ""} onclick="setDims(${state.selTab}, ${r}, ${c + 1})">+</button>
      </div></div></div>
      <div class="quick-row" style="margin-bottom:8px;">
        <button class="q-chip q-emerald" onclick="setDims(${state.selTab}, ${dyn}, ${dyn})">${dyn}×${dyn} Квадр.</button>
        <button class="q-chip q-cyan" onclick="setDims(${state.selTab}, 1, ${dyn})">1×${dyn} Строка</button>
        <button class="q-chip q-purple" onclick="setDims(${state.selTab}, ${dyn}, 1)">${dyn}×1 Столбец</button>
      </div>`;
  } else {
    if (state.selTab === 0) {
      box.innerHTML = `<div class="dim-row"><div class="dim-card">
        <div><div style="color:var(--cyan);font-weight:700;font-size:12.5px;">Матрица состояния A (${state.tauN}×${state.tauN})</div>
        <div class="dim-label">Порядок системы n для B (n×m) и C (k×n)</div></div>
        <div class="counter-wrap">
          <button class="cnt-btn" ${state.tauN <= 2 ? "disabled" : ""} onclick="setDims(0, ${state.tauN - 1}, ${state.tauN - 1})">−</button>
          <span class="cnt-val">${state.tauN}×${state.tauN}</span>
          <button class="cnt-btn" ${state.tauN >= 7 ? "disabled" : ""} onclick="setDims(0, ${state.tauN + 1}, ${state.tauN + 1})">+</button>
        </div></div></div>`;
    } else if (state.selTab === 1) {
      box.innerHTML = `<div class="dim-row"><div class="dim-card">
        <div><div style="color:var(--purple);font-weight:700;font-size:12.5px;">Матрица управления B (${state.tauN}×${state.tauM})</div>
        <div class="dim-label">Строк n = ${state.tauN} 🔒 • Входов m:</div></div>
        <div class="counter-wrap">
          <button class="cnt-btn" ${state.tauM <= 1 ? "disabled" : ""} onclick="setDims(1, ${state.tauN}, ${state.tauM - 1})">−</button>
          <span class="cnt-val">m=${state.tauM}</span>
          <button class="cnt-btn" ${state.tauM >= 7 ? "disabled" : ""} onclick="setDims(1, ${state.tauN}, ${state.tauM + 1})">+</button>
        </div></div></div>`;
    } else {
      box.innerHTML = `<div class="dim-row"><div class="dim-card">
        <div><div style="color:var(--amber);font-weight:700;font-size:12.5px;">Матрица выхода C (${state.tauK}×${state.tauN})</div>
        <div class="dim-label">Столбцов n = ${state.tauN} 🔒 • Выходов k:</div></div>
        <div class="counter-wrap">
          <button class="cnt-btn" ${state.tauK <= 1 ? "disabled" : ""} onclick="setDims(2, ${state.tauK - 1}, ${state.tauN})">−</button>
          <span class="cnt-val">k=${state.tauK}</span>
          <button class="cnt-btn" ${state.tauK >= 7 ? "disabled" : ""} onclick="setDims(2, ${state.tauK + 1}, ${state.tauN})">+</button>
        </div></div></div>`;
    }
  }
}

function renderGridInput() {
  const grid = document.getElementById("matrixGridInput"), { r, c } = getDims();
  grid.innerHTML = "";
  for (let i = 0; i < r; i++) {
    const rowEl = document.createElement("div"); rowEl.className = "matrix-grid-row";
    for (let j = 0; j < c; j++) {
      const inp = document.createElement("input");
      inp.type = "text"; inp.autocomplete = "off"; inp.className = "matrix-cell";
      inp.value = state.matrices[state.selTab][i][j];
      inp.onfocus = () => { if (inp.value === "0") inp.value = ""; };
      inp.onblur = () => { if (inp.value.trim() === "") inp.value = "0"; state.matrices[state.selTab][i][j] = inp.value.trim(); };
      inp.oninput = () => { state.matrices[state.selTab][i][j] = inp.value; };
      rowEl.appendChild(inp);
    }
    grid.appendChild(rowEl);
  }
}

function renderTransferRow() {
  const row = document.getElementById("transferRow"), cur = state.selTab;
  const o1 = cur === 0 ? 1 : 0, o2 = cur === 2 ? 1 : 2, names = ["A", "B", "C"], cls = ["q-cyan", "q-purple", "q-amber"];
  row.innerHTML = `
    <button class="q-chip ${cls[o1]}" onclick="transferToTarget(${o1}, getParsedMatrix(${cur}))">${names[cur]} ➔ в ${names[o1]}</button>
    <button class="q-chip ${cls[o2]}" onclick="transferToTarget(${o2}, getParsedMatrix(${cur}))">${names[cur]} ➔ в ${names[o2]}</button>
    <button class="q-chip" onclick="swapMatrices(${cur}, ${o1})">${names[cur]} ⇄ ${names[o1]}</button>`;
}

function swapMatrices(i1, i2) {
  const m1 = getParsedMatrix(i1), m2 = getParsedMatrix(i2);
  transferToTarget(i1, m2); transferToTarget(i2, m1); showToast("Матрицы обменяны!");
}

function renderOperationsPanel() {
  const panel = document.getElementById("operationsPanel"), name = ["A", "B", "C"][state.selTab], { r, c } = getDims();
  if (state.mainTab === 0) {
    panel.innerHTML = `
      <div class="sec-header">Унарные операции над [${name} (${r}×${c})]</div>
      <div class="btn-row">
        <button class="op-btn" onclick="opInverse()">${name}⁻¹ (Обратная)</button>
        <button class="op-btn" onclick="showMatrixResult(math.transpose(getParsedMatrix()), 'Транспонирование ${name}ᵀ')">${name}ᵀ (Трансп.)</button>
        <button class="op-btn" onclick="opDet()">det(${name})</button>
      </div>
      <div class="btn-row">
        <button class="op-btn" onclick="showTextResult('Ранг rank(${name})', 'rank(${name}) = ' + math.rank(getParsedMatrix()))">Rank(${name})</button>
        <button class="op-btn" onclick="opTrace()">След Tr(${name})</button>
        <button class="op-btn" onclick="showTextResult('Норма Фробениуса', '‖${name}‖_F = ' + formatNumber(math.norm(getParsedMatrix())))">‖${name}‖_F</button>
        <button class="op-btn" onclick="showTextResult('Спектральная норма', '‖${name}‖_2 = ' + formatNumber(math.spectralNorm(getParsedMatrix())))">‖${name}‖_2</button>
      </div>
      <div class="sec-header">Арифметика матриц A, B, C</div>
      <div class="btn-row">
        <button class="op-btn" onclick="showMatrixResult(math.add(getParsedMatrix(0), getParsedMatrix(1)), 'A + B')">A + B</button>
        <button class="op-btn" onclick="showMatrixResult(math.subtract(getParsedMatrix(0), getParsedMatrix(1)), 'A − B')">A − B</button>
        <button class="op-btn" onclick="showMatrixResult(math.multiply(getParsedMatrix(0), getParsedMatrix(1)), 'A × B')">A × B</button>
      </div>
      <div class="btn-row">
        <button class="op-btn" onclick="opTernary('add', 'A + B + C')">A + B + C</button>
        <button class="op-btn" onclick="opTernary('sub', 'A − B − C')">A − B − C</button>
        <button class="op-btn" onclick="opTernary('mul', 'A × B × C')">A × B × C</button>
      </div>
      <div class="sec-header">Векторные операции (над A и B)</div>
      <div class="btn-row">
        <button class="op-btn purple" onclick="opDot()">a · b (Скалярное)</button>
        <button class="op-btn purple" onclick="showMatrixResult(math.crossProduct(getParsedMatrix(0), getParsedMatrix(1)), 'Векторное a ⨯ b')">a ⨯ b (Векторное 3D)</button>
      </div>
      <div class="scalar-bar">
        <span style="font-size:12px;color:var(--text-dim);">Скаляр k:</span>
        <input class="scalar-input" id="scalarInput" value="${state.scalarK}" oninput="state.scalarK=this.value" />
        <button class="scalar-go" onclick="showMatrixResult(math.multiplyScalar(getParsedMatrix(), parseSmartCell(state.scalarK)), 'Умножение ' + state.scalarK + ' × ${name}')">Вычислить k × ${name}</button>
      </div>`;
  } else {
    const tName = r === c ? name : "A";
    panel.innerHTML = `
      <div class="sec-header">Спектр и канонические формы [${tName} (${state.tauN}×${state.tauN})]</div>
      <div class="btn-row">
        <button class="op-btn purple" onclick="opEigenvalues()">λᵢ (Числа)</button>
        <button class="op-btn purple" onclick="opEigenvectors()">vᵢ (Векторы)</button>
      </div>
      <div class="btn-row">
        <button class="op-btn purple" onclick="opJnf(false)">ЖНФ (Комплексная)</button>
        <button class="op-btn purple" onclick="opJnf(true)">РЖНФ (Вещественная)</button>
      </div>
      <div class="btn-row">
        <button class="op-btn purple" onclick="showTextResult('Характеристический многочлен', characteristicPolynomialString(getParsedMatrix(0)))">Характеристический многочлен p(λ)</button>
      </div>
      <div class="sec-header">Устойчивость и критерии Калмана: A (${state.tauN}×${state.tauN}), B (${state.tauN}×${state.tauM}), C (${state.tauK}×${state.tauN})</div>
      <div class="btn-row">
        <button class="op-btn" onclick="opStabilityCont()">Непрерывная Re(λ) + Гурвиц</button>
        <button class="op-btn" onclick="opStabilityDisc()">Дискретная ρ(A)</button>
      </div>
      <div class="btn-row">
        <button class="op-btn purple" onclick="opControllability()">Управляемость (A,B)</button>
        <button class="op-btn purple" onclick="opObservability()">Наблюдаемость (A,C)</button>
      </div>
      <div class="btn-row">
        <button class="op-btn" onclick="openFlowModal()">Анализ системы (Блок-схема и Разложение Калмана)</button>
      </div>
      <div class="sec-header">Синтез регуляторов и наблюдателей (ТАУ)</div>
      <div class="btn-row">
        <button class="op-btn purple" onclick="openFrobeniusModal()">Фробениус (A,B)</button>
        <button class="op-btn purple" onclick="openModalControlDialog()">Регулятор (K)</button>
        <button class="op-btn purple" onclick="openObserverModal()">Наблюдатель (L)</button>
      </div>
      <div class="btn-row">
        <button class="op-btn purple" onclick="openLqrModal()">LQR-оптимизация (Уравнение Риккати CARE)</button>
      </div>
      <div class="sec-header">Преобразование «Вход–Выход» W(s) ⇄ «Пространство состояний» (A,B,C)</div>
      <div class="btn-row">
        <button class="op-btn" onclick="openTfToSsModal()">Вход–Выход W(s) ➔ (A, B, C)</button>
        <button class="op-btn purple" onclick="openSsToTfModal()">(A, B, C) ➔ Вход–Выход W(s)</button>
      </div>
      <div class="sec-header">Высшие матричные функции от [A (${state.tauN}×${state.tauN})]</div>
      <div class="btn-row">
        <button class="op-btn" onclick="showMatrixResult(math.matrixExponential(getParsedMatrix(0)), 'Экспонента e^A')">e^A</button>
        <button class="op-btn" onclick="showMatrixResult(math.matrixLog(getParsedMatrix(0)), 'Логарифм ln(A)')">ln(A)</button>
        <button class="op-btn" onclick="showMatrixResult(math.matrixSqrt(getParsedMatrix(0)), 'Корень √A')">√A</button>
        <button class="op-btn" onclick="openPowerModal()">A^k</button>
      </div>
      <div class="btn-row">
        <button class="op-btn" onclick="showMatrixResult(math.matrixTrig(getParsedMatrix(0))[0], 'Синус sin(A)')">sin(A)</button>
        <button class="op-btn" onclick="showMatrixResult(math.matrixTrig(getParsedMatrix(0))[1], 'Косинус cos(A)')">cos(A)</button>
      </div>`;
  }
}

function renderSymbolicCellHtml(v, symbolic, isExpMode) {
  if (!symbolic) return `<div class="res-grid-cell">${formatNumber(v)}</div>`;
  const t = toSymbolicToken(v, isExpMode);
  if (!t.isFraction) {
    const isSpec = t.plainText.includes("e") || t.plainText.includes("√");
    return `<div class="res-grid-cell" style="${isSpec ? 'color:var(--cyan);font-weight:800;' : ''}">${t.plainText}</div>`;
  }
  const sfx = t.suffix ? `<span style="color:var(--amber);font-weight:800;margin-left:2px;">${t.suffix}</span>` : "";
  return `<div class="res-grid-cell"><span class="frac-box">${t.sign || ""}<span class="frac-col"><span>${t.num}</span><span class="frac-bar"></span><span>${t.den}</span></span>${sfx}</span></div>`;
}

function getSubspaceCellStyle(r, c, meta) {
  if (!meta) return "";
  const styles = {
    1: "background:rgba(112,225,161,0.18);border:1.5px solid #70E1A1;",
    2: "background:rgba(84,229,229,0.18);border:1.5px solid #54E5E5;",
    3: "background:rgba(255,209,102,0.18);border:1.5px solid #FFD166;",
    4: "background:rgba(248,113,113,0.18);border:1.5px solid #F87171;",
    5: "background:rgba(182,154,255,0.10);border:1px dashed rgba(182,154,255,0.45);"
  };
  if (meta.type === "ctrl_blocks") {
    if (r < meta.r && c < meta.r) return styles[1];
    if (r >= meta.r && c >= meta.r) return styles[4];
    
  } else if (meta.type === "ctrl_cols") return c < meta.r ? styles[1] : styles[4];
  else if (meta.type === "ctrl_rows") return r < meta.r ? styles[1] : styles[4];
  else if (meta.type === "kalman_blocks" || meta.type === "kalman_cols" || meta.type === "kalman_rows") {
    const { n1, n2, n3 } = meta;
    const colZone = c < n1 ? 1 : c < n1 + n2 ? 2 : c < n1 + n2 + n3 ? 3 : 4;
    const rowZone = r < n1 ? 1 : r < n1 + n2 ? 2 : r < n1 + n2 + n3 ? 3 : 4;
    if (meta.type === "kalman_cols") return styles[colZone];
    if (meta.type === "kalman_rows") return styles[rowZone];
    if (rowZone === colZone) return styles[rowZone];
  }
  return "";
}

function buildLegendHtml(meta) {
  if (!meta) return "";
  const pill = (col, bg, txt) => `<span style="display:inline-flex;align-items:center;padding:3px 8px;border-radius:6px;font-size:11px;font-weight:700;color:${col};background:${bg};border:1px solid ${col};">${txt}</span>`;
  if (meta.type === "ctrl_blocks" || meta.type === "ctrl_cols" || meta.type === "ctrl_rows") {
    return `<div style="display:flex;gap:6px;flex-wrap:wrap;justify-content:center;margin:2px 0 8px;">` +
      pill("#70E1A1", "rgba(112,225,161,0.14)", `🟩 Управляемая часть (${meta.r})`) +
      pill("#F87171", "rgba(248,113,113,0.14)", `🟥 Неуправляемая часть (${meta.u})`) + `</div>`;
  }
  if (meta.type === "kalman_blocks" || meta.type === "kalman_cols" || meta.type === "kalman_rows") {
    const items = [];
    if (meta.n1 > 0) items.push(pill("#70E1A1", "rgba(112,225,161,0.14)", `🟩 T₁ (${meta.n1}): упр., набл.`));
    if (meta.n2 > 0) items.push(pill("#54E5E5", "rgba(84,229,229,0.14)", `🟦 T₂ (${meta.n2}): упр., ненабл.`));
    if (meta.n3 > 0) items.push(pill("#FFD166", "rgba(255,209,102,0.14)", `🟨 T₃ (${meta.n3}): неупр., набл.`));
    if (meta.n4 > 0) items.push(pill("#F87171", "rgba(248,113,113,0.14)", `🟥 T₄ (${meta.n4}): неупр., ненабл.`));
    return `<div style="display:flex;gap:6px;flex-wrap:wrap;justify-content:center;margin:2px 0 8px;">${items.join("")}</div>`;
  }
  return "";
}

function parseRichSectionHtml(reportText, symbolic = false, isExpMode = false) {
  window._embeddedReportMatrices = [];
  reportText = prettifyWebMath(reportText);
  const sections = reportText.split(/[═─]{6,}/).map(s => s.trim()).filter(Boolean);
  const isMatLine = (s) => {
    const t = s.trim();
    return t.startsWith("⎡") || t.startsWith("⎢") || t.startsWith("⎣") || t.startsWith("│") || (t.startsWith("[") && t.endsWith("]") && !t.includes(";") && !t.includes("ᵀ"));
  };

  return sections.map(sec => {
    const lines = sec.split(/\r?\n/);
    let html = `<div style="background:#0C1424;border:1px solid #1F2E47;border-radius:13px;padding:12px;margin-bottom:10px;">`;
    let i = 0, lastMeta = null;
    while (i < lines.length) {
      const line = lines[i].trim(); if (!line) { i++; continue; }

      if (isMatLine(line)) {
        const rows = [];
        while (i < lines.length && isMatLine(lines[i])) {
          const clean = lines[i].trim().replace(/^[⎡⎢⎣│\[]+/, '').replace(/[⎤⎥⎦│\]]+$/, '').trim();
          const nums = clean.split(/\s+/).map(parseSmartCell);
          if (nums.length) rows.push(nums);
          i++;
        }
        if (rows.length) {
          const matId = window._embeddedReportMatrices.push(rows) - 1;
          html += `<div class="matrix-scroll" style="margin:6px 0;"><div class="matrix-bracket-wrap">
            <div class="bracket-line" style="background:var(--emerald);width:3.5px;"></div>
            <div class="matrix-grid">` +
            rows.map((r, ri) => `<div class="matrix-grid-row">` + r.map((v, ci) => {
              const zoneStyle = getSubspaceCellStyle(ri, ci, lastMeta);
              const cellHtml = renderSymbolicCellHtml(v, symbolic, isExpMode);
              return zoneStyle ? cellHtml.replace('class="res-grid-cell"', `class="res-grid-cell" style="${zoneStyle}"`) : cellHtml;
            }).join("") + `</div>`).join("") +
            `</div><div class="bracket-line" style="background:var(--emerald);width:3.5px;"></div></div></div>` +
            buildLegendHtml(lastMeta) +
            `<div style="display:flex;gap:6px;justify-content:center;margin:2px 0 8px;">
              <button class="q-chip q-cyan" style="flex:0 0 64px;height:28px;font-size:11px;" onclick="transferToTarget(0, window._embeddedReportMatrices[${matId}])">➔ в A</button>
              <button class="q-chip q-purple" style="flex:0 0 64px;height:28px;font-size:11px;" onclick="transferToTarget(1, window._embeddedReportMatrices[${matId}])">➔ в B</button>
              <button class="q-chip q-amber" style="flex:0 0 64px;height:28px;font-size:11px;" onclick="transferToTarget(2, window._embeddedReportMatrices[${matId}])">➔ в C</button>
              <button class="q-chip" style="flex:0 0 38px;height:28px;font-size:11px;" onclick="copyEmbeddedMat(${matId})">📋</button>
            </div>`;
        }
        lastMeta = null; continue;
      }

      const mCtrlB = line.match(/\[блоки:\s*упр=(\d+),\s*неупр=(\d+)\]/);
      const mCtrlC = line.match(/\[стб:\s*упр=(\d+),\s*неупр=(\d+)\]/);
      const mCtrlR = line.match(/\[стр:\s*упр=(\d+),\s*неупр=(\d+)\]/);
      const mKalB = line.match(/\[калман:\s*T1=(\d+),\s*T2=(\d+),\s*T3=(\d+),\s*T4=(\d+)\]/);
      const mKalC = line.match(/\[стб_калман:\s*T1=(\d+),\s*T2=(\d+),\s*T3=(\d+),\s*T4=(\d+)\]/);
      const mKalR = line.match(/\[стр_калман:\s*T1=(\d+),\s*T2=(\d+),\s*T3=(\d+),\s*T4=(\d+)\]/);
      if (mCtrlB) lastMeta = { type: "ctrl_blocks", r: +mCtrlB[1], u: +mCtrlB[2] };
      else if (mCtrlC) lastMeta = { type: "ctrl_cols", r: +mCtrlC[1], u: +mCtrlC[2] };
      else if (mCtrlR) lastMeta = { type: "ctrl_rows", r: +mCtrlR[1], u: +mCtrlR[2] };
      else if (mKalB) lastMeta = { type: "kalman_blocks", n1: +mKalB[1], n2: +mKalB[2], n3: +mKalB[3], n4: +mKalB[4] };
      else if (mKalC) lastMeta = { type: "kalman_cols", n1: +mKalC[1], n2: +mKalC[2], n3: +mKalC[3], n4: +mKalC[4] };
      else if (mKalR) lastMeta = { type: "kalman_rows", n1: +mKalR[1], n2: +mKalR[2], n3: +mKalR[3], n4: +mKalR[4] };
      else lastMeta = null;

      const cleanLine = line.replace(/\s*\[(блоки|стб|стр|калман|стб_калман|стр_калман):[^\]]+\]/, "");

      if (/^\d+\)/.test(cleanLine) || cleanLine.endsWith("ПРОСТРАНСТВЕ СОСТОЯНИЙ:") || cleanLine.startsWith("ПОСТРОЕНИЕ ") || cleanLine.startsWith("ИТОГОВЫЙ ВЫВОД") || cleanLine.startsWith("ПРЕОБРАЗОВАНИЕ ")) {
        html += `<div style="color:var(--cyan);font-weight:800;font-size:13.5px;margin:4px 0 6px;">${cleanLine}</div>`;
      } else if (cleanLine.startsWith("➔") || cleanLine.startsWith("✅") || cleanLine.startsWith("❌") || cleanLine.startsWith("⚠️")) {
        const bad = cleanLine.includes("НЕ ") || cleanLine.startsWith("❌");
        const good = cleanLine.includes("СТАБИЛИЗИРУЕМА") || cleanLine.includes("ДЕТЕКТИРУЕМА") || cleanLine.includes("УСТОЙЧИВА") || cleanLine.includes("полностью") || cleanLine.startsWith("✅");
        const col = bad ? "#F87171" : good ? "var(--emerald)" : "var(--amber)";
        const bg = bad ? "rgba(248,113,113,0.12)" : good ? "rgba(112,225,161,0.12)" : "rgba(255,209,102,0.12)";
        html += `<div style="background:${bg};border:1px solid ${col};color:${col};border-radius:9px;padding:7px 10px;font-weight:700;font-size:12.5px;margin:5px 0;">${cleanLine}</div>`;
      } else if (cleanLine.includes("[ПФ:") && cleanLine.includes("///")) {
        const prefix = cleanLine.split("[ПФ:")[0].trim();
        const inside = cleanLine.split("[ПФ:")[1].replace(/\]$/, "").trim();
        const [numStr, denStr] = inside.split("///").map(s => s.trim());
        html += `<div style="background:rgba(182,154,255,0.14);border:1px solid var(--purple);border-radius:10px;padding:10px 14px;margin:6px 0;display:flex;align-items:center;justify-content:center;gap:10px;overflow-x:auto;">
          <span style="font-family:monospace;font-weight:800;font-size:14px;color:var(--text-white);white-space:nowrap;">${prefix}</span>
          <span style="display:inline-flex;flex-direction:column;align-items:center;font-family:monospace;font-weight:800;font-size:13.5px;">
            <span style="color:var(--emerald);padding:0 8px;white-space:nowrap;">${numStr}</span>
            <span style="width:100%;height:1.5px;background:var(--cyan);margin:3px 0;"></span>
            <span style="color:var(--cyan);padding:0 8px;white-space:nowrap;">${denStr}</span>
          </span>
        </div>`;
      } else if (cleanLine.startsWith("u =") || cleanLine.startsWith("λ =") || cleanLine.startsWith("p(s) =")) {
        html += `<div style="background:rgba(182,154,255,0.15);border:1px solid var(--purple);color:var(--text-white);border-radius:9px;padding:8px 12px;font-weight:800;font-family:monospace;font-size:13.5px;margin:5px 0;">${cleanLine}</div>`;
      } else {
        const isLbl = cleanLine.endsWith(":");
        html += `<div style="color:${isLbl ? 'var(--amber)' : 'var(--text-white)'};font-weight:${isLbl ? '600' : '400'};font-size:12.5px;margin:3px 0;line-height:1.45;">${cleanLine}</div>`;
      }
      i++;
    }
    html += `</div>`;
    return html;
  }).join("");
}

function renderResultCard() {
  const box = document.getElementById("resultCardContainer");
  if (!state.resText && !state.resMatrix && !state.resGrid) { box.innerHTML = ""; return; }
  const hasEmbeddedMatrices = state.resText.includes("⎡") || state.resText.includes("│") || state.resText.includes("══");
  const showSymbolicToggle = !state.isError && (state.resMatrix !== null || hasEmbeddedMatrices);
  const isExpMode = state.resTitle.includes("Экспонента") || state.resTitle.includes("e^");

  let matHtml = "";
  if (state.resGrid) {
    const hasComplex = state.resGrid.some(r => r.some(c => String(c).includes("i") || String(c).length > 7));
    const wStyle = hasComplex ? "width:136px;min-width:136px;max-width:136px;font-size:12px;" : "";
    matHtml = `<div class="matrix-scroll"><div class="matrix-bracket-wrap">
      <div class="bracket-line" style="background:var(--emerald);width:3.5px;"></div>
      <div class="matrix-grid">` +
      state.resGrid.map((row, i) => `<div class="matrix-grid-row">` + row.map((cell, j) => {
        const isJordanOne = cell === "1" && i !== j;
        const cStyle = isJordanOne ? "color:var(--cyan);font-weight:800;" : (i === j ? "font-weight:800;" : "");
        return `<div class="res-grid-cell" style="${wStyle}${cStyle}">${cell}</div>`;
      }).join("") + `</div>`).join("") +
      `</div><div class="bracket-line" style="background:var(--emerald);width:3.5px;"></div></div></div>`;
  } else if (state.resMatrix && !hasEmbeddedMatrices) {
    matHtml = `<div class="matrix-scroll"><div class="matrix-bracket-wrap">
      <div class="bracket-line" style="background:var(--emerald);width:3.5px;"></div>
      <div class="matrix-grid">` +
      state.resMatrix.map(row => `<div class="matrix-grid-row">` + row.map(v => renderSymbolicCellHtml(v, state.symbolicMode, isExpMode)).join("") + `</div>`).join("") +
      `</div><div class="bracket-line" style="background:var(--emerald);width:3.5px;"></div></div></div>`;
  }

  const transferHtml = state.resMatrix && !state.isError && !hasEmbeddedMatrices ? `
    <div style="border-top:1px solid var(--border-col);margin-top:10px;padding-top:8px;">
      <div style="font-size:11px;color:var(--text-dim);margin-bottom:6px;">Перенести матрицу (${state.resMatrix.length}×${state.resMatrix[0].length}) в:</div>
      <div class="quick-row">
        <button class="q-chip q-cyan" onclick="transferToTarget(0, state.resMatrix)">➔ в A</button>
        <button class="q-chip q-purple" onclick="transferToTarget(1, state.resMatrix)">➔ в B</button>
        <button class="q-chip q-amber" onclick="transferToTarget(2, state.resMatrix)">➔ в C</button>
      </div>
    </div>` : "";

  box.innerHTML = `
    <div class="result-card ${state.isError ? "error" : ""}">
      <div class="res-top">
        <div class="res-title">${state.isError ? "⚠️ Ошибка" : "✅ " + prettifyWebMath(state.resTitle)}</div>
        <div class="res-actions">
          ${showSymbolicToggle ? `<button class="small-btn ${state.symbolicMode ? "active" : ""}" onclick="state.symbolicMode=!state.symbolicMode;renderResultCard()">${state.symbolicMode ? "Дроби: ВКЛ" : "a/b · eⁿ"}</button>` : ""}
          <button class="small-btn" onclick="copyResult()">📋</button>
          <button class="small-btn" style="color:var(--cyan);" onclick="openExportChoiceModal()">📤 PDF / LaTeX</button>
          <button class="small-btn" onclick="state.resText='';state.resMatrix=null;state.resGrid=null;renderResultCard()">✕</button>
        </div>
      </div>
      ${state.resText ? (hasEmbeddedMatrices ? parseRichSectionHtml(state.resText, state.symbolicMode, isExpMode) : `<pre class="res-text">${prettifyWebMath(state.resText)}</pre>`) : ""}
      ${matHtml}
      ${transferHtml}
    </div>`;
}

// Операции
function opInverse() {
  const m = getParsedMatrix();
  if (m.length !== m[0].length) return showTextResult("Обратная матрица", "Матрица должна быть квадратной!", null, null, true);
  const inv = math.inverse(m);
  if (!inv) return showTextResult("Обратная матрица", "Матрица вырождена (det = 0)!", null, null, true);
  showMatrixResult(inv, "Обратная матрица");
}
function opDet() {
  const m = getParsedMatrix();
  if (m.length !== m[0].length) return showTextResult("Определитель", "Только для квадратных матриц!", null, null, true);
  showTextResult("Определитель", `det = ${formatNumber(math.determinant(m))}`);
}
function opTrace() {
  const m = getParsedMatrix();
  if (m.length !== m[0].length) return showTextResult("След матрицы", "Только для квадратных матриц!", null, null, true);
  showTextResult("След матрицы", `Tr = ${formatNumber(math.trace(m))}`);
}
function opTernary(type, title) {
  const a = getParsedMatrix(0), b = getParsedMatrix(1), c = getParsedMatrix(2);
  const fn = type === 'add' ? math.add.bind(math) : type === 'sub' ? math.subtract.bind(math) : math.multiply.bind(math);
  const ab = fn(a, b); showMatrixResult(ab ? fn(ab, c) : null, title);
}
function opDot() {
  const d = math.dotProduct(getParsedMatrix(0), getParsedMatrix(1));
  if (d === null) showTextResult("Скалярное произведение", "Размеры векторов A и B не совпадают!", null, null, true);
  else showTextResult("Скалярное произведение a · b", `a · b = ${formatNumber(d)}`);
}
function opEigenvalues() {
  const eigs = math.eigenvalues(getParsedMatrix(0));
  showTextResult("Собственные числа λᵢ (A)", eigs.map((c, i) => `λ_${i + 1} = ${c}`).join("\n"));
}
function opEigenvectors() {
  const raw = getParsedMatrix(0), eigs = math.eigenvalues(raw), info = math.eigenvectorsWithDetails(raw, eigs);
  const txt = info.map(x => `v_${x.index} (λ_${x.index} = ${x.lambda}) — ${x.isAssociated ? "Присоед. вектор (ур. " + x.chainLevel + ")" : "Собственный вектор"}:\n  Ненормированный: ${x.canon}\n  Нормированный:   ${x.unit}`).join("\n\n");
  showTextResult("Собственные и присоед. векторы (A)", txt);
}
function opJnf(real) {
  const [desc, grid] = real ? math.realJordanForm(getParsedMatrix(0)) : math.jordanForm(getParsedMatrix(0));
  showTextResult(real ? "РЖНФ (A)" : "ЖНФ (A)", desc, null, grid);
}
function opStabilityCont() {
  try {
    const matA = getParsedMatrix(0), n = matA.length, eigs = math.eigenvalues(matA), maxRe = Math.max(...eigs.map(e => e.re));
    let bMat = math.identityMatrix(n); const aCoeffs = [1.0];
    for (let k = 1; k <= n; k++) {
      const ab = math.multiply(matA, bMat), tr = ab.reduce((s, r, i) => s + r[i], 0), ck = -tr / k;
      const cleanC = Math.abs(ck) < 1e-9 ? 0 : (Math.abs(ck - Math.round(ck)) < 1e-6 ? Math.round(ck) : ck);
      aCoeffs.push(cleanC);
      if (k < n) bMat = ab.map((r, ri) => r.map((v, ci) => v + (ri === ci ? cleanC : 0)));
    }
    const hurwitzMat = Array.from({ length: n }, (_, i) => Array.from({ length: n }, (_, j) => {
      const idx = 2 * j - i + 1; return idx >= 0 && idx <= n ? aCoeffs[idx] : 0.0;
    }));
    const deltas = Array.from({ length: n }, (_, k) => {
      const sz = k + 1, sub = Array.from({ length: sz }, (_, r) => Array.from({ length: sz }, (_, c) => hurwitzMat[r][c]));
      const d = math.determinant(sub) || 0;
      return Math.abs(d) < 1e-9 ? 0 : (Math.abs(d - Math.round(d)) < 1e-6 ? Math.round(d) : d);
    });
    const allPos = deltas.every(d => d > 1e-8), lines = [];
    lines.push("АНАЛИЗ УСТОЙЧИВОСТИ НЕПРЕРЫВНОЙ СИСТЕМЫ:");
    lines.push(maxRe < -1e-8 ? "✅ СИСТЕМА АСИМПТОТИЧЕСКИ УСТОЙЧИВА (все Re λᵢ < 0)"
      : maxRe > 1e-8 ? "❌ СИСТЕМА НЕУСТОЙЧИВА (есть Re λᵢ > 0)"
      : "⚠️ СИСТЕМА НА ГРАНИЦЕ УСТОЙЧИВОСТИ (есть Re λᵢ = 0)");
    lines.push("\n1) Спектральный критерий (собственные числа A):");
    eigs.forEach((e, i) => lines.push(`• λ_${i + 1} = ${e}`));
    lines.push("════════════════════════════════");
    lines.push("2) АЛГЕБРАИЧЕСКИЙ КРИТЕРИЙ ГУРВИЦА:");
    lines.push(`Характеристический многочлен:\np(s) = ${formatPolyS(aCoeffs)}`);
    lines.push(`\nМатрица Гурвица H (${n}×${n}):\n` + formatMatrixAligned(hurwitzMat));
    lines.push("Главные диагональные миноры (определители Гурвица):");
    deltas.forEach((d, i) => lines.push(`• Δ_${i + 1} = ${formatSymbolic(d)}  ${d > 1e-8 ? "> 0 (✅)" : d < -1e-8 ? "< 0 (❌)" : "= 0 (⚠️)"}`));
    lines.push(allPos
      ? "✅ Все определители Гурвица Δ₁..Δₙ > 0 ➔ по критерию Гурвица система АСИМПТОТИЧЕСКИ УСТОЙЧИВА."
      : "❌ Условие Δₖ > 0 нарушено ➔ по критерию Гурвица система НЕ является асимптотически устойчивой.");
    showTextResult("Устойчивость (Спектр и Критерий Гурвица)", lines.join("\n"), hurwitzMat);
  } catch (e) { showTextResult("Ошибка", e.message, null, null, true); }
}
function opStabilityDisc() {
  const eigs = math.eigenvalues(getParsedMatrix(0)), rho = Math.max(...eigs.map(e => e.abs()));
  const v = rho < 1 - 1e-8 ? "✅ УСТОЙЧИВА (|λᵢ| < 1)" : rho > 1 + 1e-8 ? "❌ НЕУСТОЙЧИВА (|λᵢ| > 1)" : "⚠️ НА ГРАНИЦЕ ЕДИНИЧНОГО КРУГА";
  showTextResult("Спектральный радиус ρ(A)", `ρ(A) = ${formatNumber(rho, 4)}\n${v}\n\n` + eigs.map((e, i) => `λ_${i + 1} = ${e}`).join("\n"));
}
function opControllability() {
  const a = getParsedMatrix(0), b = getParsedMatrix(1), wc = math.controllability(a, b), r = math.rank(wc);
  showTextResult("Матрица управляемости W_c", `rank(W_c) = ${r} из ${a.length}\n` + (r === a.length ? "✅ Система ПОЛНОСТЬЮ управляема" : `❌ Система НЕ полностью управляема (rank = ${r})`), wc);
}
function opObservability() {
  const a = getParsedMatrix(0), c = getParsedMatrix(2), wo = math.observability(a, c), r = math.rank(wo);
  showTextResult("Матрица наблюдаемости W_o", `rank(W_o) = ${r} из ${a.length}\n` + (r === a.length ? "✅ Система ПОЛНОСТЬЮ наблюдаема" : `❌ Система НЕ полностью наблюдаема (rank = ${r})`), wo);
}
function opFullBlockDiagram(useB, useC) {
  try {
    const a = getParsedMatrix(0);
    const b = useB ? getParsedMatrix(1) : Array.from({ length: a.length }, () => [0]);
    const c = useC ? getParsedMatrix(2) : [new Array(a.length).fill(0)];
    showTextResult("Полный анализ системы (Блок-схема и Калман)", math.fullBlockDiagramAnalysis(a, b, c));
  } catch (e) { showTextResult("Ошибка анализа", e.message, null, null, true); }
}

function openModal(title, bodyHtml, footHtml) {
  document.getElementById("modalTitle").textContent = title;
  document.getElementById("modalBody").innerHTML = bodyHtml;
  document.getElementById("modalFoot").innerHTML = footHtml;
  document.getElementById("modalBackdrop").classList.add("open");
}
function closeModal() { document.getElementById("modalBackdrop").classList.remove("open"); }
document.getElementById("modalCloseBtn").onclick = closeModal;

function openFlowModal() {
  const hasB = getParsedMatrix(1).some(r => r.some(v => Math.abs(v) > 1e-12));
  const hasC = getParsedMatrix(2).some(r => r.some(v => Math.abs(v) > 1e-12));
  openModal("Анализ системы (Блок-схема)",
    `<p style="font-size:13px;color:var(--text-dim);">Матрица A (${state.tauN}×${state.tauN}) используется всегда. Отметьте используемые матрицы:</p>
     <label style="display:flex;align-items:center;gap:8px;margin:10px 0;cursor:pointer;"><input type="checkbox" id="chkFlowB" ${hasB ? "checked" : ""}/> Матрица B (${state.tauN}×${state.tauM}) введена</label>
     <label style="display:flex;align-items:center;gap:8px;margin:10px 0;cursor:pointer;"><input type="checkbox" id="chkFlowC" ${hasC ? "checked" : ""}/> Матрица C (${state.tauK}×${state.tauN}) введена</label>`,
    `<button class="small-btn" onclick="closeModal()">Отмена</button>
     <button class="op-btn" style="height:36px;flex:0 0 120px;" onclick="opFullBlockDiagram(document.getElementById('chkFlowB').checked, document.getElementById('chkFlowC').checked);closeModal()">Выполнить</button>`);
}

const dialogState = {
  poles: ["-1", "-1", "-1", "-1", "-1", "-1", "-1"],
  obsPoles: ["-2", "-2", "-2", "-2", "-2", "-2", "-2"],
  lqrQ: Array.from({ length: 7 }, (_, r) => Array.from({ length: 7 }, (_, c) => (r === c ? "1" : "0"))),
  lqrR: Array.from({ length: 7 }, (_, r) => Array.from({ length: 7 }, (_, c) => (r === c ? "1" : "0")))
};

function buildModalMatrixHtml(gridId, rows, cols, getCellFn, accentColor) {
  let html = `<div class="matrix-scroll" style="margin:4px 0 10px;"><div class="matrix-bracket-wrap">
    <div class="bracket-line" style="background:${accentColor};"></div><div class="matrix-grid">`;
  for (let i = 0; i < rows; i++) {
    html += `<div class="matrix-grid-row">`;
    for (let j = 0; j < cols; j++) {
      html += `<input type="text" autocomplete="off" class="matrix-cell" style="width:44px;height:42px;font-size:15px;"
        value="${getCellFn(i, j)}"
        onfocus="if(this.value==='0')this.value=''"
        onblur="if(this.value.trim()==='')this.value='0';onModalCellInput('${gridId}',${i},${j},this.value.trim())"
        oninput="onModalCellInput('${gridId}',${i},${j},this.value)" />`;
    }
    html += `</div>`;
  }
  return html + `</div><div class="bracket-line" style="background:${accentColor};"></div></div></div>`;
}

function onModalCellInput(gridId, r, c, val) {
  if (gridId === 'mainA') {
    state.matrices[0][r][c] = val; renderGridInput();
    if (document.getElementById("modalPolesLiveBox")) updateModalPolesLiveSection();
    if (document.getElementById("modalObsPolesLiveBox")) updateObsPolesLiveSection();
  } else if (gridId === 'mainB1' || gridId === 'mainB') {
    state.matrices[1][r][c] = val; renderGridInput();
    if (document.getElementById("modalPolesLiveBox")) updateModalPolesLiveSection();
  } else if (gridId === 'mainC' || gridId === 'mainC1') {
    state.matrices[2][r][c] = val; renderGridInput();
    if (document.getElementById("modalObsPolesLiveBox")) updateObsPolesLiveSection();
  } else if (gridId === 'lqrQ') dialogState.lqrQ[r][c] = val;
  else if (gridId === 'lqrR') dialogState.lqrR[r][c] = val;
}

function openFrobeniusModal() {
  const n = state.tauN;
  openModal("Форма Фробениуса–Люенбергера",
    `<div style="display:flex;justify-content:center;align-items:center;gap:10px;margin-bottom:12px;">
      <span style="font-size:14px;font-weight:600;">Размер n:</span>
      <button class="cnt-btn" ${n <= 2 ? "disabled" : ""} onclick="setDims(0, ${n - 1}, ${n - 1});openFrobeniusModal()">−</button>
      <span style="font-weight:800;font-size:15px;">${n}×${n}</span>
      <button class="cnt-btn" ${n >= 7 ? "disabled" : ""} onclick="setDims(0, ${n + 1}, ${n + 1});openFrobeniusModal()">+</button>
    </div>
    <div style="text-align:center;color:var(--cyan);font-weight:700;font-size:13px;">МАТРИЦА A (${n}×${n})</div>
    ${buildModalMatrixHtml('mainA', n, n, (i, j) => state.matrices[0][i][j], 'var(--cyan)')}
    <div style="text-align:center;color:var(--purple);font-weight:700;font-size:13px;margin-top:6px;">МАТРИЦА B (${n}×1)</div>
    ${buildModalMatrixHtml('mainB1', n, 1, (i) => state.matrices[1][i][0], 'var(--purple)')}`,
    `<button class="small-btn" onclick="closeModal()">Отмена</button>
     <button class="op-btn purple" style="height:36px;flex:0 0 130px;" onclick="runFrobeniusFromModal()">Рассчитать</button>`);
}

function runFrobeniusFromModal() {
  try {
    const n = state.tauN, a = getParsedMatrix(0), b = Array.from({ length: n }, (_, r) => [parseSmartCell(state.matrices[1][r][0])]);
    const [aT, bT, tM] = math.frobeniusForm(a, b);
    closeModal();
    showTextResult("Форма Фробениуса–Люенбергера", `Матрица Ã:\n${formatMatrixAligned(aT)}\n\nМатрица B̃:\n${formatMatrixAligned(bT)}\n\nМатрица преобразования T (x = Tz):\n${formatMatrixAligned(tM)}`, aT);
  } catch (e) { showToast(e.message || "Ошибка формы Фробениуса"); }
}

function openModalControlDialog() {
  const n = state.tauN;
  try {
    const eigs = math.eigenvalues(getParsedMatrix(0));
    for (let i = 0; i < n; i++) if (eigs[i]) dialogState.poles[i] = eigs[i].re >= 0 ? String(-Math.max(1, Math.round(Math.abs(eigs[i].re)))) : eigs[i].toString().replace(/\s+/g, '');
  } catch (_) {}
  renderModalControlContent();
}

function renderModalControlContent() {
  const n = state.tauN;
  openModal("Модальное управление (K)",
    `<div style="display:flex;align-items:center;gap:10px;margin-bottom:10px;">
      <span style="font-size:14px;font-weight:600;">Размер n:</span>
      <button class="cnt-btn" ${n <= 2 ? "disabled" : ""} onclick="setDims(0, ${n - 1}, ${n - 1});renderModalControlContent()">−</button>
      <span style="font-weight:800;font-size:15px;">${n}×${n}</span>
      <button class="cnt-btn" ${n >= 7 ? "disabled" : ""} onclick="setDims(0, ${n + 1}, ${n + 1});renderModalControlContent()">+</button>
    </div>
    <div style="color:var(--cyan);font-weight:700;font-size:13px;">A (${n}×${n})</div>
    ${buildModalMatrixHtml('mainA', n, n, (i, j) => state.matrices[0][i][j], 'var(--cyan)')}
    <div style="color:var(--purple);font-weight:700;font-size:13px;">B (${n}×1)</div>
    ${buildModalMatrixHtml('mainB1', n, 1, (i) => state.matrices[1][i][0], 'var(--purple)')}
    <div id="modalPolesLiveBox"></div>`,
    `<button class="small-btn" onclick="closeModal()">Отмена</button>
     <button class="op-btn purple" style="height:36px;flex:0 0 130px;" onclick="runModalControlCalc()">Рассчитать</button>`);
  updateModalPolesLiveSection();
}

function updateModalPolesLiveSection() {
  const container = document.getElementById("modalPolesLiveBox"); if (!container) return;
  const n = state.tauN; let eigs = [], flags = new Array(n).fill(true), ctrlRank = n;
  try {
    const aMat = getParsedMatrix(0), bMat = Array.from({ length: n }, (_, r) => [parseSmartCell(state.matrices[1][r][0])]);
    eigs = math.eigenvalues(aMat); flags = math.getControllableModeFlags(aMat, bMat, eigs); ctrlRank = flags.filter(Boolean).length;
  } catch (_) {}
  const stTxt = ctrlRank === n ? `✅ Полностью управляема (rank W_c = ${ctrlRank})` : ctrlRank > 0 ? `⚠️ Частично управляема: управляемых мод ${ctrlRank} из ${n}` : `❌ Неуправляема`;
  let rowsHtml = "";
  for (let i = 0; i < n; i++) {
    const eig = eigs[i], isCtrl = flags[i] !== false, tag = ctrlRank < n ? (isCtrl ? " [упр.]" : " [неупр.]") : "";
    rowsHtml += `<div class="pole-row">
      <span style="font-size:12px;color:${isCtrl ? 'var(--cyan)' : 'var(--text-dim)'};font-weight:${isCtrl ? '700' : '400'};">λ_${i + 1} = ${eig || '?'}${tag}</span>
      ${isCtrl ? `<input class="pole-input" value="${dialogState.poles[i] || '-1'}" oninput="dialogState.poles[${i}]=this.value" />`
               : `<div style="width:130px;padding:8px 10px;border-radius:8px;background:#161F30;border:1px solid var(--border-col);color:var(--text-dim);font-size:12px;">${eig || 'фикс.'} 🔒</div>`}
    </div>`;
  }
  container.innerHTML = `<div style="color:${ctrlRank === n ? 'var(--emerald)' : 'var(--amber)'};font-size:11.5px;font-weight:600;margin:6px 0 8px;">${stTxt}</div>${rowsHtml}`;
}

function parseSinglePole(str) {
  const s = String(str || "-1").trim().replace(/\s+/g, '').replace(/[−–]/g, '-');
  if (!s.includes('i')) return new Complex(Number(s));
  const body = s.slice(0, -1); let split = -1;
  for (let i = 1; i < body.length; i++) if (body[i] === '+' || body[i] === '-') split = i;
  if (split === -1) return new Complex(0, body === '' || body === '+' ? 1 : body === '-' ? -1 : Number(body));
  const re = Number(body.slice(0, split)), imS = body.slice(split);
  return new Complex(re, imS === '+' ? 1 : imS === '-' ? -1 : Number(imS));
}



function openObserverModal() {
  const n = state.tauN;
  try {
    const eigs = math.eigenvalues(getParsedMatrix(0));
    for (let i = 0; i < n; i++) if (eigs[i]) dialogState.obsPoles[i] = eigs[i].re >= 0 ? String(-Math.max(2, Math.round(Math.abs(eigs[i].re)) + 1)) : eigs[i].toString().replace(/\s+/g, '');
  } catch (_) {}
  renderObserverModalContent();
}

function renderObserverModalContent() {
  const n = state.tauN;
  openModal("Наблюдатель Люенбергера (L)",
    `<div style="display:flex;align-items:center;gap:10px;margin-bottom:10px;">
      <span style="font-size:14px;font-weight:600;">Размер n:</span>
      <button class="cnt-btn" ${n <= 2 ? "disabled" : ""} onclick="setDims(0, ${n - 1}, ${n - 1});renderObserverModalContent()">−</button>
      <span style="font-weight:800;font-size:15px;">${n}×${n}</span>
      <button class="cnt-btn" ${n >= 7 ? "disabled" : ""} onclick="setDims(0, ${n + 1}, ${n + 1});renderObserverModalContent()">+</button>
    </div>
    <div style="color:var(--cyan);font-weight:700;font-size:13px;">A (${n}×${n})</div>
    ${buildModalMatrixHtml('mainA', n, n, (i, j) => state.matrices[0][i][j], 'var(--cyan)')}
    <div style="color:var(--amber);font-weight:700;font-size:13px;">C (1×${n})</div>
    ${buildModalMatrixHtml('mainC1', 1, n, (_, j) => state.matrices[2][0][j], 'var(--amber)')}
    <div id="modalObsPolesLiveBox"></div>`,
    `<button class="small-btn" onclick="closeModal()">Отмена</button>
     <button class="op-btn purple" style="height:36px;flex:0 0 140px;" onclick="runObserverFromModal()">Рассчитать L</button>`);
  updateObsPolesLiveSection();
}

function updateObsPolesLiveSection() {
  const box = document.getElementById("modalObsPolesLiveBox"); if (!box) return;
  const n = state.tauN; let eigs = [], flags = new Array(n).fill(true), obsRank = n;
  try {
    const aMat = getParsedMatrix(0), cMat = [Array.from({ length: n }, (_, j) => parseSmartCell(state.matrices[2][0][j]))];
    eigs = math.eigenvalues(aMat); flags = math.getControllableModeFlags(math.transpose(aMat), math.transpose(cMat), eigs); obsRank = flags.filter(Boolean).length;
  } catch (_) {}
  const stTxt = obsRank === n ? `✅ Полностью наблюдаема (rank W_o = ${obsRank})` : obsRank > 0 ? `⚠️ Частично наблюдаема: наблюдаемых мод ${obsRank} из ${n}` : `❌ Ненаблюдаема`;
  let rowsHtml = "";
  for (let i = 0; i < n; i++) {
    const eig = eigs[i], isObs = flags[i] !== false, tag = obsRank < n ? (isObs ? " [набл.]" : " [ненабл.]") : "";
    rowsHtml += `<div class="pole-row">
      <span style="font-size:12px;color:${isObs ? 'var(--amber)' : 'var(--text-dim)'};font-weight:${isObs ? '700' : '400'};">λ_${i + 1} = ${eig || '?'}${tag}</span>
      ${isObs ? `<input class="pole-input" style="border-color:var(--amber);" value="${dialogState.obsPoles[i] || '-2'}" oninput="dialogState.obsPoles[${i}]=this.value" />`
              : `<div style="width:130px;padding:8px 10px;border-radius:8px;background:#161F30;border:1px solid var(--border-col);color:var(--text-dim);font-size:12px;">${eig || 'фикс.'} 🔒</div>`}
    </div>`;
  }
  box.innerHTML = `<div style="color:${obsRank === n ? 'var(--emerald)' : 'var(--amber)'};font-size:11.5px;font-weight:600;margin:6px 0 8px;">${stTxt}</div>${rowsHtml}`;
}



function openLqrModal() {
  const n = state.tauN, m = state.tauM;
  openModal("LQR — оптимальное управление",
    `<div class="dim-row">
      <div class="dim-card"><span class="dim-label">Состояния (n):</span><div class="counter-wrap">
        <button class="cnt-btn" ${n <= 2 ? "disabled" : ""} onclick="setDims(0, ${n - 1}, ${n - 1});openLqrModal()">−</button>
        <span class="cnt-val" style="color:var(--cyan);">${n}</span>
        <button class="cnt-btn" ${n >= 7 ? "disabled" : ""} onclick="setDims(0, ${n + 1}, ${n + 1});openLqrModal()">+</button>
      </div></div>
      <div class="dim-card"><span class="dim-label">Столбцов B (m):</span><div class="counter-wrap">
        <button class="cnt-btn" ${m <= 1 ? "disabled" : ""} onclick="setDims(1, ${n}, ${m - 1});openLqrModal()">−</button>
        <span class="cnt-val" style="color:var(--purple);">${m}</span>
        <button class="cnt-btn" ${m >= 7 ? "disabled" : ""} onclick="setDims(1, ${n}, ${m + 1});openLqrModal()">+</button>
      </div></div>
    </div>
    <div style="color:var(--cyan);font-weight:700;font-size:13px;">A (${n}×${n})</div>
    ${buildModalMatrixHtml('mainA', n, n, (i, j) => state.matrices[0][i][j], 'var(--cyan)')}
    <div style="color:var(--purple);font-weight:700;font-size:13px;">B (${n}×${m})</div>
    ${buildModalMatrixHtml('mainB', n, m, (i, j) => state.matrices[1][i][j], 'var(--purple)')}
    <div style="color:var(--amber);font-weight:700;font-size:13px;">Q (${n}×${n}, Q ≥ 0)</div>
    ${buildModalMatrixHtml('lqrQ', n, n, (i, j) => dialogState.lqrQ[i][j], 'var(--amber)')}
    <div style="color:var(--purple);font-weight:700;font-size:13px;">R (${m}×${m}, R > 0)</div>
    ${buildModalMatrixHtml('lqrR', m, m, (i, j) => dialogState.lqrR[i][j], 'var(--purple)')}`,
    `<button class="small-btn" onclick="closeModal()">Отмена</button>
     <button class="op-btn purple" style="height:36px;flex:0 0 130px;" onclick="runLqrFromModal()">Рассчитать</button>`);
}

function runLqrFromModal() {
  try {
    const n = state.tauN, m = state.tauM, a = getParsedMatrix(0), b = getParsedMatrix(1);
    const q = Array.from({ length: n }, (_, i) => Array.from({ length: n }, (_, j) => parseSmartCell(dialogState.lqrQ[i][j])));
    const r = Array.from({ length: m }, (_, i) => Array.from({ length: m }, (_, j) => parseSmartCell(dialogState.lqrR[i][j])));
    const [pMat, kMat] = math.solveLQR(a, b, q, r);
    closeModal();
    showTextResult("LQR — Оптимальное управление", `Решение уравнения Риккати P (${n}×${n}):\n${formatMatrixAligned(pMat)}\n\nМатрица обратной связи K = R⁻¹BᵀP (${m}×${n}):\n${formatMatrixAligned(kMat)}\n\nЗакон управления: u = −Kx`, kMat);
  } catch (e) { showToast(e.message || "Ошибка расчёта LQR"); }
}

const tfDialogState = { n: 3, num: ["0", "0", "0", "1", "0", "0", "0", "0"], den: ["1", "6", "11", "6", "0", "0", "0", "0"], autoLoad: true };

function formatPolyS(coeffs, vName = "s") {
  const d = coeffs.length - 1, terms = [];
  coeffs.forEach((v, i) => {
    const p = d - i; if (Math.abs(v) < 1e-8) return;
    const absStr = formatSymbolic(Math.abs(v));
    const sign = v < 0 && terms.length ? " − " : v < 0 ? "−" : terms.length ? " + " : "";
    const pStr = p === 0 ? "" : p === 1 ? vName : vName + toSuperscriptStr(p);
    const cStr = p > 0 && absStr === "1" ? "" : (p > 0 && absStr.includes("/") ? `(${absStr})` : absStr);
    terms.push(`${sign}${cStr}${pStr}`);
  });
  return terms.length ? terms.join("") : "0";
}

function formatDiffEq(den, num) {
  const side = (arr, fn) => {
    const d = arr.length - 1, t = [];
    arr.forEach((v, i) => {
      const ord = d - i; if (Math.abs(v) < 1e-8) return;
      const absStr = formatSymbolic(Math.abs(v));
      const sign = v < 0 && t.length ? " − " : v < 0 ? "−" : t.length ? " + " : "";
      const drv = ord === 0 ? fn : ord === 1 ? `${fn}'` : ord === 2 ? `${fn}''` : ord === 3 ? `${fn}'''` : `${fn}^(${ord})`;
      t.push(`${sign}${absStr === "1" ? "" : absStr + "·"}${drv}`);
    });
    return t.length ? t.join("") : "0";
  };
  return `${side(den, "y")} = ${side(num, "u")}`;
}


function openTfToSsModal(skipSync = false) {
  if (!skipSync) {
    try {
      const matA = getParsedMatrix(0), matB = getParsedMatrix(1), matC = getParsedMatrix(2);
      const hasAll = matA.some(r => r.some(v => Math.abs(v) > 1e-12)) &&
                     matB.some(r => r.some(v => Math.abs(v) > 1e-12)) &&
                     matC.some(r => r.some(v => Math.abs(v) > 1e-12));
      if (hasAll) {
        const cleanB = math.trimZeroColsB(matB), cleanC = math.trimZeroRowsC(matC), n = matA.length;
        const fMats = [], pCoeffs = new Array(n).fill(0);
        let fCur = math.identityMatrix(n);
        for (let k = 1; k <= n; k++) {
          fMats.push(fCur);
          const af = math.multiply(matA, fCur), tr = af.reduce((s, r, idx) => s + r[idx], 0), pk = -tr / k;
          pCoeffs[k - 1] = Math.abs(pk) < 1e-9 ? 0 : (Math.abs(pk - Math.round(pk)) < 1e-6 ? Math.round(pk) : pk);
          if (k < n) fCur = af.map((r, ri) => r.map((v, ci) => v + (ri === ci ? pCoeffs[k - 1] : 0)));
        }
        const denCoeffs = [1.0, ...pCoeffs], bCol = cleanB.map(r => [r[0]]), cRow = [cleanC[0]];
        const numCoeffs = fMats.map(f => {
          const v = math.multiply(math.multiply(cRow, f), bCol)[0][0];
          return Math.abs(v) < 1e-8 ? 0 : (Math.abs(v - Math.round(v)) < 1e-6 ? Math.round(v) : v);
        });
        tfDialogState.n = n;
        tfDialogState.den = denCoeffs.map(v => formatNumber(v, 5));
        tfDialogState.num = ["0", ...numCoeffs.map(v => formatNumber(v, 5))];
      }
    } catch (_) {}
  }

  const n = tfDialogState.n;
  let numBoxes = "", denBoxes = "";
  for (let i = 0; i <= n; i++) {
    const p = n - i, lbl = p === 0 ? "1" : p === 1 ? "s" : `s${toSuperscriptStr(p)}`;
    numBoxes += `<div style="display:flex;flex-direction:column;align-items:center;gap:3px;">
      <span style="font-size:10.5px;color:var(--text-dim);font-weight:700;">${prettifyWebMath('b_' + p)} (${lbl})</span>
      <input type="text" class="matrix-cell" style="width:58px;height:40px;font-size:14px;border-color:rgba(112,225,161,0.55);"
        value="${tfDialogState.num[i] ?? '0'}"
        onfocus="if(this.value==='0')this.value=''"
        onblur="if(this.value.trim()==='')this.value='0';tfDialogState.num[${i}]=this.value.trim();updateTfPreview()"
        oninput="tfDialogState.num[${i}]=this.value;updateTfPreview()" />
    </div>`;
    denBoxes += `<div style="display:flex;flex-direction:column;align-items:center;gap:3px;">
      <span style="font-size:10.5px;color:var(--text-dim);font-weight:700;">${prettifyWebMath('a_' + p)} (${lbl})</span>
      <input type="text" class="matrix-cell" style="width:58px;height:40px;font-size:14px;border-color:rgba(84,229,229,0.55);"
        value="${tfDialogState.den[i] ?? '0'}"
        onfocus="if(this.value==='0')this.value=''"
        onblur="if(this.value.trim()==='')this.value='0';tfDialogState.den[${i}]=this.value.trim();updateTfPreview()"
        oninput="tfDialogState.den[${i}]=this.value;updateTfPreview()" />
    </div>`;
  }

  openModal(
    "Вход–Выход W(s) ➔ (A, B, C)",
    `<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">
      <span style="font-size:13.5px;font-weight:600;">Порядок знаменателя n:</span>
      <div class="counter-wrap">
        <button class="cnt-btn" ${n <= 1 ? "disabled" : ""} onclick="changeTfOrder(${n - 1})">−</button>
        <span class="cnt-val" style="color:var(--cyan);">n = ${n}</span>
        <button class="cnt-btn" ${n >= 7 ? "disabled" : ""} onclick="changeTfOrder(${n + 1})">+</button>
      </div>
    </div>
    <div style="color:var(--emerald);font-weight:700;font-size:12.5px;margin-bottom:4px;">ЧИСЛИТЕЛЬ B(s):</div>
    <div class="matrix-scroll" style="justify-content:flex-start;gap:6px;padding-bottom:8px;">${numBoxes}</div>
    <div style="color:var(--cyan);font-weight:700;font-size:12.5px;margin:8px 0 4px;">ЗНАМЕНАТЕЛЬ A(s):</div>
    <div class="matrix-scroll" style="justify-content:flex-start;gap:6px;padding-bottom:8px;">${denBoxes}</div>
    <div id="tfLivePreviewBox" style="margin-top:8px;"></div>
    <label style="display:flex;align-items:center;gap:8px;margin-top:10px;font-size:12px;cursor:pointer;">
      <input type="checkbox" ${tfDialogState.autoLoad ? "checked" : ""} onchange="tfDialogState.autoLoad=this.checked" />
      Загрузить полученные матрицы A, B, C на главный экран
    </label>`,
    `<button class="small-btn" onclick="closeModal()">Отмена</button>
     <button class="op-btn" style="height:36px;flex:0 0 140px;" onclick="runTfToSsCalc()">Преобразовать</button>`
  );
  updateTfPreview();
}

function changeTfOrder(newN) {
  if (newN > tfDialogState.n) {
    tfDialogState.num.unshift("0");
    tfDialogState.den.unshift("1");
  } else if (newN < tfDialogState.n) {
    tfDialogState.num.shift();
    tfDialogState.den.shift();
  }
  tfDialogState.n = newN;
  openTfToSsModal(true);
}

function changeTfOrder(newN) {
  if (newN > tfDialogState.n) { tfDialogState.num.unshift("0"); tfDialogState.den.unshift("1"); }
  else if (newN < tfDialogState.n) { tfDialogState.num.shift(); tfDialogState.den.shift(); }
  tfDialogState.n = newN; openTfToSsModal(true);
}

function updateTfPreview() {
  const box = document.getElementById("tfLivePreviewBox"); if (!box) return;
  const n = tfDialogState.n;
  const num = Array.from({ length: n + 1 }, (_, i) => parseSmartCell(tfDialogState.num[i]));
  const den = Array.from({ length: n + 1 }, (_, i) => parseSmartCell(tfDialogState.den[i]));
  box.innerHTML = `<div style="background:#0B1322;border:1px solid rgba(182,154,255,0.45);border-radius:10px;padding:10px;text-align:center;">
    <div style="color:var(--emerald);font-weight:800;font-family:monospace;font-size:13px;">${formatPolyS(num)}</div>
    <div style="width:85%;height:1.3px;background:var(--cyan);margin:4px auto;"></div>
    <div style="color:var(--cyan);font-weight:800;font-family:monospace;font-size:13px;">${formatPolyS(den)}</div>
  </div>`;
}



function openSsToTfModal() {
  const n = state.tauN, m = state.tauM, k = state.tauK;
  openModal("(A, B, C) ➔ Вход–Выход W(s)",
    `<p style="font-size:11.5px;color:var(--text-dim);margin:0 0 8px;">Проверьте или заполните матрицы A, B и C на одном экране:</p>
    <div style="display:flex;justify-content:center;align-items:center;gap:10px;margin-bottom:10px;">
      <span style="font-size:13.5px;font-weight:600;">Порядок n:</span>
      <button class="cnt-btn" ${n <= 2 ? "disabled" : ""} onclick="setDims(0, ${n - 1}, ${n - 1});openSsToTfModal()">−</button>
      <span style="font-weight:800;color:var(--cyan);">${n}×${n}</span>
      <button class="cnt-btn" ${n >= 7 ? "disabled" : ""} onclick="setDims(0, ${n + 1}, ${n + 1});openSsToTfModal()">+</button>
    </div>
    <div style="color:var(--cyan);font-weight:700;font-size:12.5px;text-align:center;">МАТРИЦА СОСТОЯНИЯ A (${n}×${n})</div>
    ${buildModalMatrixHtml('mainA', n, n, (i, j) => state.matrices[0][i][j], 'var(--cyan)')}
    <div style="color:var(--purple);font-weight:700;font-size:12.5px;text-align:center;">МАТРИЦА ВХОДА B (${n}×${m})</div>
    ${buildModalMatrixHtml('mainB', n, m, (i, j) => state.matrices[1][i][j], 'var(--purple)')}
    <div style="color:var(--amber);font-weight:700;font-size:12.5px;text-align:center;">МАТРИЦА ВЫХОДА C (${k}×${n})</div>
    ${buildModalMatrixHtml('mainC', k, n, (i, j) => state.matrices[2][i][j], 'var(--amber)')}`,
    `<button class="small-btn" onclick="closeModal()">Отмена</button>
     <button class="op-btn purple" style="height:36px;flex:0 0 150px;" onclick="closeModal();runSsToTfCalc()">Рассчитать W(s)</button>`);
}

function runModalControlCalc() {
  try {
    const n = state.tauN, a = getParsedMatrix(0), b = Array.from({ length: n }, (_, r) => [parseSmartCell(state.matrices[1][r][0])]);
    const eigs = math.eigenvalues(a), flags = math.getControllableModeFlags(a, b, eigs);
    const r = flags.filter(Boolean).length;
    const poles = eigs.map((orig, i) => flags[i] ? parseSinglePole(dialogState.poles[i]) : orig);
    const kMat = math.ackermann(a, b, poles), theta = math.multiplyScalar(kMat, -1);
    const cl = math.eigenvalues(math.subtract(a, math.multiply(b, kMat)));
    const isStab = cl.every(c => c.re < -1e-7);
    closeModal();
    let header = "";
    if (r < n) {
      header = `Разложение Калмана: rank(W_c) = ${r} < ${n}\nПеремещено управляемых мод: ${r}\n` +
        (isStab ? "✅ Система СТАБИЛИЗИРУЕМА и стабилизирована!\n\n" : "⚠️ Неуправляемое подпространство содержит неустойчивые моды!\n\n");
    }
    showTextResult("Модальное управление (K и Θ)",
      header +
      `Спектр замкнутой системы:\n` + cl.map((c, i) => `λ*_${i + 1} = ${c}`).join("\n") +
      `\n────────────────────────────────\nМатрица Θ = −K (для u = Θx, как в конспекте):\n${formatMatrixAligned(theta)}\n\nЗакон управления:\nu = Θx = ${math.formatControlLaw(theta)}\n────────────────────────────────\nМатрица K (для u = −Kx):\n${formatMatrixAligned(kMat)}`, kMat);
  } catch (e) { showToast(e.message || "Ошибка расчёта"); }
}

function runObserverFromModal() {
  try {
    const n = state.tauN, aMat = getParsedMatrix(0), cMat = [Array.from({ length: n }, (_, j) => parseSmartCell(state.matrices[2][0][j]))];
    const eigs = math.eigenvalues(aMat), flags = math.getObservableModeFlags(aMat, cMat, eigs);
    const r = flags.filter(Boolean).length;
    const poles = eigs.map((orig, i) => flags[i] ? parseSinglePole(dialogState.obsPoles[i]) : orig);
    const lMat = math.luenbergerObserver(aMat, cMat, poles), clSpec = math.eigenvalues(math.subtract(aMat, math.multiply(lMat, cMat)));
    const isDetOk = clSpec.every(c => c.re < -1e-7);
    closeModal();
    let header = "";
    if (r < n) {
      header = `Разложение Калмана: rank(W_o) = ${r} < ${n}\n` +
        (isDetOk ? "✅ Система ДЕТЕКТИРУЕМА, ошибка наблюдения затухает!\n\n" : "⚠️ Ненаблюдаемое подпространство содержит неустойчивые моды!\n\n");
    }
    showTextResult("Наблюдатель Люенбергера (L)",
      header +
      `Спектр ошибки наблюдения (A − LC):\n` + clSpec.map((c, i) => `μ_${i + 1} = ${c}`).join("\n") +
      `\n════════════════════════════════\nМатрица наблюдателя L (${n}×1):\n${formatMatrixAligned(lMat)}`, lMat);
  } catch (e) { showToast(e.message || "Ошибка расчёта L"); }
}

function polyDivMod(uIn, vIn) {
  const trim = (p) => { const idx = p.findIndex(x => Math.abs(x) > 1e-7); return idx === -1 ? [0] : p.slice(idx); };
  const u = trim(uIn), v = trim(vIn);
  if (v.length === 1 && Math.abs(v[0]) < 1e-9) return [[0], u];
  if (u.length < v.length || (u.length === 1 && Math.abs(u[0]) < 1e-7)) return [[0], u];
  const q = new Array(u.length - v.length + 1).fill(0), rem = [...u], vLead = v[0];
  for (let i = 0; i < q.length; i++) {
    const coef = rem[i] / vLead; q[i] = coef;
    for (let j = 0; j < v.length; j++) rem[i + j] -= coef * v[j];
  }
  return [trim(q), trim(rem)];
}

function polyGcd(aIn, bIn) {
  const trim = (p) => { const idx = p.findIndex(x => Math.abs(x) > 1e-6); return idx === -1 ? [0] : p.slice(idx); };
  let a = trim(aIn), b = trim(bIn);
  const scaleB = Math.max(1, ...b.map(Math.abs));
  if (b.length === 1 && Math.abs(b[0]) < 1e-6 * scaleB) return [1];
  for (let step = 0; step < 15; step++) {
    const [, r] = polyDivMod(a, b);
    const maxR = Math.max(0, ...r.map(Math.abs)), maxA = Math.max(1, ...a.map(Math.abs));
    if (maxR <= 1e-5 * maxA) {
      const lead = b[0];
      return b.map(x => x / lead);
    }
    a = b; b = r;
  }
  return [1];
}

function runTfToSsCalc() {
  try {
    const nIn = tfDialogState.n;
    const rawNumIn = Array.from({ length: nIn + 1 }, (_, i) => parseSmartCell(tfDialogState.num[i]));
    const rawDenIn = Array.from({ length: nIn + 1 }, (_, i) => parseSmartCell(tfDialogState.den[i]));
    const firstDen = rawDenIn.findIndex(v => Math.abs(v) > 1e-10);
    if (firstDen === -1) throw new Error("Знаменатель A(s) не может быть равен нулю!");
    const rawDen = rawDenIn.slice(firstDen), n = rawDen.length - 1;
    const firstNum = rawNumIn.findIndex(v => Math.abs(v) > 1e-10);
    const rawNum = firstNum === -1 ? [0] : rawNumIn.slice(firstNum), m = rawNum.length - 1;
    if (m > n) throw new Error(`Степень числителя (m = ${m}) не должна превышать степень знаменателя (n = ${n})!`);

    const an = rawDen[0], monicDen = rawDen.map(v => v / an);
    const paddedNum = Array.from({ length: n + 1 }, (_, i) => (i >= (n + 1) - rawNum.length ? rawNum[i - ((n + 1) - rawNum.length)] / an : 0.0));
    const aAsc = Array.from({ length: n }, (_, k) => monicDen[n - k]), bAsc = Array.from({ length: n + 1 }, (_, k) => paddedNum[n - k]);
    const dVal = bAsc[n], betaAsc = Array.from({ length: n }, (_, k) => bAsc[k] - aAsc[k] * dVal);

    const aCtrl = Array.from({ length: n }, (_, i) => Array.from({ length: n }, (_, j) => (i < n - 1 ? (j === i + 1 ? 1 : 0) : (Math.abs(aAsc[j]) < 1e-12 ? 0 : -aAsc[j]))));
    const bCtrl = Array.from({ length: n }, (_, i) => [i === n - 1 ? 1 : 0]);
    const cCtrl = [Array.from({ length: n }, (_, j) => (Math.abs(betaAsc[j]) < 1e-12 ? 0 : betaAsc[j]))];
    const aObs = math.transpose(aCtrl), bObs = math.transpose(cCtrl), cObs = math.transpose(bCtrl);
    const poles = math.eigenvalues(aCtrl), isStable = poles.every(p => p.re < -1e-7);

    const lines = [];
    lines.push("ПРЕОБРАЗОВАНИЕ «ВХОД–ВЫХОД» ➔ «ПРОСТРАНСТВО СОСТОЯНИЙ»:");
    lines.push("Исходная передаточная функция (коэффициент передачи):");
    lines.push(`W(s) = [ПФ: ${formatPolyS(rawNum)} /// ${formatPolyS(rawDen)}]`);
    if (Math.abs(an - 1.0) > 1e-7) {
      lines.push(`После нормировки на старший коэффициент a_${n} = ${formatSymbolic(an)}:`);
      lines.push(`W(s) = [ПФ: ${formatPolyS(paddedNum)} /// ${formatPolyS(monicDen)}]`);
    }
    lines.push(`\nДифференциальное уравнение «вход–выход»:\n${formatDiffEq(monicDen, paddedNum)}`);
    lines.push(`Порядок системы: n = ${n}, степень числителя: m = ${m}`);
    if (Math.abs(dVal) > 1e-8) lines.push(`Выделена прямая связь (при m = n): D = ${formatSymbolic(dVal)}`);
    lines.push("════════════════════════════════");
    lines.push("1) УПРАВЛЯЕМАЯ КАНОНИЧЕСКАЯ ФОРМА (ФРОБЕНИУСА):");
    lines.push(Math.abs(dVal) > 1e-8 ? "⎧ x' = A_c x + B_c u\n⎩ y  = C_c x + D u" : "⎧ x' = A_c x + B_c u\n⎩ y  = C_c x");
    lines.push(`Матрица состояния A_c (${n}×${n}) [блоки: упр=${n}, неупр=0]:\n` + formatMatrixAligned(aCtrl));
    lines.push(`Матрица входа B_c (${n}×1) [стр: упр=${n}, неупр=0]:\n` + formatMatrixAligned(bCtrl));
    lines.push(`Матрица выхода C_c (1×${n}):\n` + formatMatrixAligned(cCtrl));
    if (Math.abs(dVal) > 1e-8) lines.push(`Коэффициент прямой передачи D = [ ${formatSymbolic(dVal)} ]`);
    lines.push("════════════════════════════════");
    lines.push("2) НАБЛЮДАЕМАЯ КАНОНИЧЕСКАЯ ФОРМА (ДВОЙСТВЕННАЯ):");
    lines.push(`Матрица состояния A_o = A_cᵀ (${n}×${n}):\n` + formatMatrixAligned(aObs));
    lines.push(`Матрица входа B_o = C_cᵀ (${n}×1):\n` + formatMatrixAligned(bObs));
    lines.push(`Матрица выхода C_o = B_cᵀ (1×${n}):\n` + formatMatrixAligned(cObs));
    lines.push("════════════════════════════════");
    lines.push("ПОЛЮСА ПЕРЕДАТОЧНОЙ ФУНКЦИИ (корни A(s) = 0):");
    lines.push(`λ = { ${poles.map((c, i) => `λ_${i + 1} = ${c}`).join(", ")} }`);
    lines.push(isStable
      ? "✅ Все полюса в левой полуплоскости (Re λ < 0) ➔ система УСТОЙЧИВА."
      : "⚠️ Есть полюса с Re λ ≥ 0 ➔ система НЕУСТОЙЧИВА.");

    if (tfDialogState.autoLoad) {
      if (n >= 2) { state.tauN = n; state.tauM = 1; state.tauK = 1; }
      state.algDim[0] = { r: n, c: n }; state.algDim[1] = { r: n, c: 1 }; state.algDim[2] = { r: 1, c: n };
      for (let i = 0; i < n; i++) {
        state.matrices[1][i][0] = formatNumber(bCtrl[i][0], 5);
        state.matrices[2][0][i] = formatNumber(cCtrl[0][i], 5);
        for (let j = 0; j < n; j++) state.matrices[0][i][j] = formatNumber(aCtrl[i][j], 5);
      }
      renderAll();
    }
    closeModal();
    showTextResult("Вход–Выход W(s) ➔ Пространство состояний", lines.join("\n"), aCtrl);
  } catch (e) { showToast(e.message || "Ошибка преобразования"); }
}

function runSsToTfCalc() {
  try {
    const matA = getParsedMatrix(0), cleanB = math.trimZeroColsB(getParsedMatrix(1)), cleanC = math.trimZeroRowsC(getParsedMatrix(2)), n = matA.length;
    const fMats = [], pCoeffs = new Array(n).fill(0); let fCur = math.identityMatrix(n);
    for (let k = 1; k <= n; k++) {
      fMats.push(fCur);
      const af = math.multiply(matA, fCur), tr = af.reduce((s, r, idx) => s + r[idx], 0), pk = -tr / k;
      pCoeffs[k - 1] = Math.abs(pk) < 1e-9 ? 0 : (Math.abs(pk - Math.round(pk)) < 1e-6 ? Math.round(pk) : pk);
      if (k < n) fCur = af.map((r, ri) => r.map((v, ci) => {
        const val = v + (ri === ci ? pCoeffs[k - 1] : 0);
        return Math.abs(val) < 1e-9 ? 0 : (Math.abs(val - Math.round(val)) < 1e-6 ? Math.round(val) : val);
      }));
    }
    const denCoeffs = [1.0, ...pCoeffs], bCol = cleanB.map(r => [r[0]]), cRow = [cleanC[0]];
    const numCoeffs = fMats.map(f => {
      const v = math.multiply(math.multiply(cRow, f), bCol)[0][0];
      return Math.abs(v) < 1e-8 ? 0 : (Math.abs(v - Math.round(v)) < 1e-6 ? Math.round(v) : v);
    });
    tfDialogState.n = n; tfDialogState.den = denCoeffs.map(v => formatNumber(v, 5)); tfDialogState.num = ["0", ...numCoeffs.map(v => formatNumber(v, 5))];

    // Проверка сократимости полюсов и нулей (НОД Евклида)
    const gcdP = numCoeffs.some(v => Math.abs(v) > 1e-8) ? polyGcd(denCoeffs, numCoeffs) : [1];
    const hasCancel = gcdP.length > 1;
    const [redDen, remDen] = hasCancel ? polyDivMod(denCoeffs, gcdP) : [denCoeffs, [0]];
    const [redNum, remNum] = hasCancel ? polyDivMod(numCoeffs, gcdP) : [numCoeffs, [0]];
    const validCancel = hasCancel && Math.max(0, ...remDen.map(Math.abs)) < 1e-4 && Math.max(0, ...remNum.map(Math.abs)) < 1e-4;

    const lines = [];
    lines.push("ПРЕОБРАЗОВАНИЕ «ПРОСТРАНСТВО СОСТОЯНИЙ» ➔ «ВХОД–ВЫХОД»:");
    lines.push("Формула коэффициента передачи: W(s) = C·(sI − A)⁻¹·B = B(s) / A(s)");
    lines.push("════════════════════════════════");
    lines.push("1) ЗНАМЕНАТЕЛЬ A(s) = det(sI − A) (по методу Фаддеева — Леверье):");
    lines.push(`Коэффициенты: [ ${denCoeffs.map(formatSymbolic).join(", ")} ]\nA(s) = ${formatPolyS(denCoeffs)}`);
    lines.push("════════════════════════════════");
    lines.push("2) ЧИСЛИТЕЛЬ B(s) = C·adj(sI − A)·B:");
    lines.push(`Присоединённая матрица adj(sI − A) = F_1·s^${n - 1} + ... + F_${n}, где β_{n-k} = C·F_k·B:`);
    for (let k = 1; k <= n; k++) {
      const pwr = n - k, sTag = pwr === 0 ? "1" : pwr === 1 ? "s" : `s${toSuperscriptStr(pwr)}`;
      lines.push(`• При ${sTag}: C·F_${k}·B = ${formatSymbolic(numCoeffs[k - 1])}`);
    }
    lines.push(`Коэффициенты числителя: [ ${numCoeffs.map(formatSymbolic).join(", ")} ]\nB(s) = ${formatPolyS(numCoeffs)}`);
    lines.push("════════════════════════════════");
    lines.push("3) ИТОГОВЫЙ КОЭФФИЦИЕНТ ПЕРЕДАЧИ W(s):");
    lines.push(`W(s) = [ПФ: ${formatPolyS(numCoeffs)} /// ${formatPolyS(denCoeffs)}]`);

    if (validCancel) {
      lines.push("\n⚠️ Обнаружен общий множитель числителя и знаменателя (неуправляемые/ненаблюдаемые моды):");
      lines.push(`НОД(B(s), A(s)) = ${formatPolyS(gcdP)}`);
      lines.push("Сокращённая передаточная функция (минимальная реализация):");
      lines.push(`W_мин(s) = [ПФ: ${formatPolyS(redNum)} /// ${formatPolyS(redDen)}]`);
    }

    if (cleanB[0].length > 1 || cleanC.length > 1) {
      lines.push(`\nМатрица передаточных функций W_{i,j}(s) для всех каналов (${cleanC.length}×${cleanB[0].length}):`);
      for (let r = 0; r < cleanC.length; r++) {
        const cr = [cleanC[r]];
        for (let c = 0; c < cleanB[0].length; c++) {
          const bc = cleanB.map(row => [row[c]]);
          const mij = fMats.map(f => {
            const v = math.multiply(math.multiply(cr, f), bc)[0][0];
            return Math.abs(v) < 1e-8 ? 0 : v;
          });
          lines.push(`• W_{${r + 1},${c + 1}}(s) = (${formatPolyS(mij)}) / (${formatPolyS(denCoeffs)})`);
        }
      }
    }

    lines.push("════════════════════════════════");
    lines.push("4) ДИФФЕРЕНЦИАЛЬНОЕ УРАВНЕНИЕ «ВХОД–ВЫХОД»:");
    lines.push(formatDiffEq(validCancel ? redDen : denCoeffs, validCancel ? redNum : numCoeffs));
    showTextResult("Коэффициент передачи W(s) из (A, B, C)", lines.join("\n"));
  } catch (e) { showToast(e.message || "Ошибка вычисления W(s)"); }
}

function openPowerModal() {
  openModal("Степень матрицы A^k",
    `<p style="font-size:13px;color:var(--text-dim);">Введите целую степень k (от -50 до 500):</p>
     <input class="pole-input" id="pwrKInp" value="3" style="width:100%;" />`,
    `<button class="small-btn" onclick="closeModal()">Отмена</button>
     <button class="op-btn" style="height:36px;flex:0 0 120px;" onclick="showMatrixResult(math.matrixPower(getParsedMatrix(0), parseInt(document.getElementById('pwrKInp').value)||1), 'Степень A^k');closeModal()">Вычислить</button>`);
}

function copyEmbeddedMat(idx) {
  const m = window._embeddedReportMatrices[idx]; if (!m) return;
  navigator.clipboard?.writeText(m.map(r => r.join("\t")).join("\n"));
  showToast("Матрица скопирована!");
}

function openExportChoiceModal() {
  openModal(
    "Экспорт и отправка решения",
    `<p style="font-size:12.5px;color:var(--text-dim);margin-bottom:10px;">Выберите формат документа:</p>
     <div style="display:flex;flex-direction:column;gap:8px;">
       <button class="op-btn" style="height:42px;" onclick="closeModal();exportWebPdfPrint()">📕 Сохранить / Печать PDF с формулами (LaTeX-стиль)</button>
       <button class="op-btn purple" style="height:42px;" onclick="closeModal();copyWebLatex()">📐 Скопировать код LaTeX (\\begin{bmatrix})</button>
       <button class="op-btn" style="height:42px;background:#1E293B;border-color:#334155;color:#fff;" onclick="closeModal();shareCurrentResult()">📄 Поделиться / Скопировать как текст</button>
     </div>`,
    `<button class="small-btn" onclick="closeModal()">Закрыть</button>`
  );
}

function shareCurrentResult() {
  const cleanTxt = prettifyWebMath(state.resText || (state.resMatrix ? formatMatrixAligned(state.resMatrix, state.symbolicMode) : ""))
    .replace(/\s*\[(блоки|стб|стр|калман|стб_калман|стр_калман):[^\]]+\]/g, "")
    .replace(/\[ПФ:\s*([^/]+?)\s*\/\/\/\s*([^\]]+?)\]/g, "($1) / ($2)");
  const full = `${prettifyWebMath(state.resTitle)}\n\n${cleanTxt}`;
  if (navigator.share) {
    navigator.share({ title: state.resTitle, text: full }).catch(() => {});
  } else {
    navigator.clipboard?.writeText(full);
    showToast("Решение скопировано!");
  }
}

function copyWebLatex() {
  const raw = prettifyWebMath(state.resText || (state.resMatrix ? formatMatrixAligned(state.resMatrix, true) : ""))
    .replace(/\s*\[(блоки|стб|стр|калман|стб_калман|стр_калман):[^\]]+\]/g, "");
  const lines = raw.split(/\r?\n/);
  const isMat = (s) => {
    const t = s.trim();
    return t.startsWith("⎡") || t.startsWith("⎢") || t.startsWith("⎣") || t.startsWith("│") || (t.startsWith("[") && t.endsWith("]") && !t.includes(";") && !t.includes("ᵀ"));
  };
  const out = [`% Сгенерировано в MATRIX LAB PRO`, `\\section*{${prettifyWebMath(state.resTitle)}}`];
  let i = 0;
  while (i < lines.length) {
    const l = lines[i].trim();
    if (!l) { i++; continue; }
    if (isMat(l)) {
      out.push("\\[ \\begin{bmatrix}");
      const rows = [];
      while (i < lines.length && isMat(lines[i])) {
        const cl = lines[i].trim().replace(/^[⎡⎢⎣│\[]+/, '').replace(/[⎤⎥⎦│\]]+$/, '').trim();
        rows.push("  " + cl.split(/\s+/).join(" & "));
        i++;
      }
      out.push(rows.join(" \\\\\n"));
      out.push("\\end{bmatrix} \\]");
      continue;
    }
    if (l.includes("[ПФ:") && l.includes("///")) {
      const pref = l.split("[ПФ:")[0].trim();
      const [num, den] = l.split("[ПФ:")[1].replace(/\]$/, "").split("///").map(s => s.trim());
      out.push(`\\[ ${pref} \\frac{${num}}{${den}} \\]`);
    } else if (!l.startsWith("══") && !l.startsWith("──")) {
      out.push(`\\noindent ${l}\\\\[4pt]`);
    }
    i++;
  }
  navigator.clipboard?.writeText(out.join("\n"));
  showToast("Код LaTeX скопирован!");
}

function exportWebPdfPrint() {
  const resEl = document.querySelector(".result-card");
  if (!resEl) return;
  const win = window.open("", "_blank");
  if (!win) return showToast("Разрешите всплывающие окна для сохранения PDF");
  win.document.write(`<!DOCTYPE html><html><head><meta charset="UTF-8"><title>${prettifyWebMath(state.resTitle)}</title>
    <style>
      body { font-family: "Cambria Math", "Times New Roman", Georgia, serif; color: #0F172A; padding: 28px; max-width: 820px; margin: 0 auto; }
      h2 { border-bottom: 2px solid #0F172A; padding-bottom: 8px; margin-bottom: 16px; }
      .res-top, .quick-row, .q-chip { display: none !important; }
      .matrix-scroll { display: flex; justify-content: flex-start; margin: 8px 0; }
      .matrix-bracket-wrap { display: inline-flex; align-items: stretch; border-left: 2px solid #0F172A; border-right: 2px solid #0F172A; border-radius: 4px; padding: 4px 8px; }
      .bracket-line { display: none; }
      .matrix-grid { display: flex; flex-direction: column; gap: 4px; }
      .matrix-grid-row { display: flex; gap: 8px; }
      .res-grid-cell { min-width: 64px; padding: 4px 8px; text-align: center; font-weight: bold; font-size: 14px; border: 1px solid #CBD5E1; border-radius: 6px; }
      .frac-box { display: inline-flex; align-items: center; gap: 2px; }
      .frac-col { display: inline-flex; flex-direction: column; align-items: center; font-size: 11px; line-height: 1.1; }
      .frac-bar { width: 100%; height: 1.2px; background: #0F172A; margin: 1px 0; }
    </style></head><body>
    <h2>MATRIX LAB PRO — ${prettifyWebMath(state.resTitle)}</h2>
    ${resEl.innerHTML}
    <script>window.onload = () => { setTimeout(() => { window.print(); }, 250); };<\/script>
  </body></html>`);
  win.document.close();
}

// Быстрые кнопки и вкладки
document.getElementById("tabMain0").onclick = () => { state.mainTab = 0; renderAll(); };
document.getElementById("tabMain1").onclick = () => { state.mainTab = 1; renderAll(); };
document.getElementById("chipMatA").onclick = () => { state.selTab = 0; renderAll(); };
document.getElementById("chipMatB").onclick = () => { state.selTab = 1; renderAll(); };
document.getElementById("chipMatC").onclick = () => { state.selTab = 2; renderAll(); };

document.getElementById("btnRandom").onclick = () => {
  const { r, c } = getDims();
  for (let i = 0; i < r; i++) for (let j = 0; j < c; j++) state.matrices[state.selTab][i][j] = String(Math.floor(Math.random() * 19) - 9);
  renderGridInput();
};
document.getElementById("btnIdentity").onclick = () => {
  const { r, c } = getDims();
  for (let i = 0; i < r; i++) for (let j = 0; j < c; j++) state.matrices[state.selTab][i][j] = i === j ? "1" : "0";
  renderGridInput();
};
document.getElementById("btnClearMat").onclick = () => {
  for (let i = 0; i < 7; i++) for (let j = 0; j < 7; j++) state.matrices[state.selTab][i][j] = "0";
  renderGridInput();
};
document.getElementById("btnCopyMat").onclick = () => {
  const txt = getParsedMatrix().map(r => r.join("\t")).join("\n");
  navigator.clipboard?.writeText(txt);
  showToast("Матрица скопирована!");
};
document.getElementById("btnPasteMat").onclick = async () => {
  const raw = (await navigator.clipboard?.readText?.().catch(() => null)) || prompt("Вставьте числа матрицы:");
  if (!raw) return;
  const rows = raw.replace(/[−–]/g, '-').split(/\r?\n/).map(l => l.match(/[+-]?\d+(?:[./]\d+)?/g)).filter(Boolean);
  if (!rows.length) return showToast("Не удалось распознать числа");
  const nr = Math.min(7, rows.length), nc = Math.min(7, Math.max(...rows.map(r => r.length)));
  for (let i = 0; i < nr; i++) for (let j = 0; j < nc; j++) state.matrices[state.selTab][i][j] = rows[i][j] || "0";
  setDims(state.selTab, nr, nc);
  showToast(`Вставлено (${nr}×${nc})`);
};
function copyResult() {
  const txt = prettifyWebMath(state.resText || (state.resMatrix ? formatMatrixAligned(state.resMatrix, state.symbolicMode) : ""))
    .replace(/\s*\[(блоки|стб|стр|калман|стб_калман|стр_калман):[^\]]+\]/g, "")
    .replace(/\[ПФ:\s*([^/]+?)\s*\/\/\/\s*([^\]]+?)\]/g, "($1) / ($2)");
  navigator.clipboard?.writeText(txt);
  showToast("Результат скопирован!");
}
function cleanHistoryText(raw) {
  return prettifyWebMath(raw || "")
    .replace(/\s*\[(блоки|стб|стр|калман|стб_калман|стр_калман):[^\]]+\]/g, "")
    .replace(/\[ПФ:\s*([^/]+?)\s*\/\/\/\s*([^\]]+?)\]/g, "($1) / ($2)");
}

function restoreHistoryItem(idx) {
  const h = state.history[idx];
  if (!h) return;
  state.resTitle = h.title;
  state.resText = h.text;
  state.resMatrix = h.mat;
  state.resGrid = null;
  state.isError = false;
  closeModal();
  renderResultCard();
  showToast("Загружено из истории!");
}

function copyHistoryItem(idx) {
  const h = state.history[idx];
  if (!h) return;
  navigator.clipboard?.writeText(`${prettifyWebMath(h.title)}\n\n${cleanHistoryText(h.text)}`);
  showToast("Запись скопирована!");
}

function exportHistoryItem(idx) {
  const h = state.history[idx];
  if (!h) return;
  // Загружаем запись в карточку результата и сразу открываем окно выбора PDF / LaTeX / Текст
  state.resTitle = h.title;
  state.resText = h.text;
  state.resMatrix = h.mat;
  state.resGrid = null;
  state.isError = false;
  renderResultCard();
  openExportChoiceModal();
}

document.getElementById("btnOpenHistory").onclick = () => {
  const html = state.history.length ? state.history.map((h, idx) => `
    <div style="background:#0F172A;border:1px solid var(--border-col);border-radius:12px;padding:12px;margin-bottom:10px;">
      <div style="display:flex;justify-content:space-between;align-items:center;gap:6px;flex-wrap:wrap;margin-bottom:6px;">
        <span style="font-size:12.5px;color:var(--cyan);font-weight:800;">${prettifyWebMath(h.title)}</span>
        <div style="display:flex;align-items:center;gap:5px;">
          <span style="color:var(--text-dim);font-size:11px;margin-right:4px;">${h.time}</span>
          <button class="small-btn" onclick="restoreHistoryItem(${idx})" title="Открыть в карточке результата">👁 Открыть</button>
          <button class="small-btn" onclick="copyHistoryItem(${idx})" title="Скопировать">📋</button>
          <button class="small-btn" style="color:var(--cyan);border-color:var(--cyan);" onclick="exportHistoryItem(${idx})">📤 PDF / LaTeX</button>
        </div>
      </div>
      <pre class="res-text" style="margin:6px 0;font-size:12px;max-height:180px;overflow-y:auto;">${cleanHistoryText(h.text)}</pre>
      ${h.mat ? `<div class="quick-row" style="margin-top:8px;">
        <button class="q-chip q-cyan" onclick="transferToTarget(0, state.history[${idx}].mat);closeModal()">➔ в A</button>
        <button class="q-chip q-purple" onclick="transferToTarget(1, state.history[${idx}].mat);closeModal()">➔ в B</button>
        <button class="q-chip q-amber" onclick="transferToTarget(2, state.history[${idx}].mat);closeModal()">➔ в C</button>
      </div>` : ""}
    </div>`).join("") : `<p style="color:var(--text-dim);text-align:center;">История пуста</p>`;
  openModal("История вычислений", html,
    `<button class="small-btn" onclick="state.history=[];localStorage.removeItem('matrix_lab_history_v1');renderAll();closeModal()">🗑️ Очистить</button>
     <button class="small-btn" onclick="closeModal()">Закрыть</button>`);
};

renderAll();