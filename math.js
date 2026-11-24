// ============================================================================
// MATH.JS — МАТЕМАТИЧЕСКОЕ ЯДРО MATRIX LAB PRO
// ============================================================================
class Complex {
  constructor(re, im = 0.0) { this.re = Number(re) || 0.0; this.im = Number(im) || 0.0; }
  plus(b) { return new Complex(this.re + b.re, this.im + b.im); }
  minus(b) { return new Complex(this.re - b.re, this.im - b.im); }
  neg() { return new Complex(-this.re, -this.im); }
  times(b) {
    if (typeof b === 'number') return new Complex(this.re * b, this.im * b);
    return new Complex(this.re * b.re - this.im * b.im, this.re * b.im + this.im * b.re);
  }
  div(b) {
    if (typeof b === 'number') return new Complex(this.re / b, this.im / b);
    if (Math.abs(b.re) >= Math.abs(b.im)) {
      const r = b.im / b.re, d = b.re + b.im * r;
      return new Complex((this.re + this.im * r) / d, (this.im - this.re * r) / d);
    } else {
      const r = b.re / b.im, d = b.im + b.re * r;
      return new Complex((this.re * r + this.im) / d, (this.im * r - this.re) / d);
    }
  }
  abs() { return Math.hypot(this.re, this.im); }
  toString() {
    const fmt = (v) => {
      if (!Number.isFinite(v)) return String(v);
      const cl = Math.abs(v) < 1e-4 ? 0.0 : v;
      return cl.toFixed(3).replace(/\.?0+$/, '') || "0";
    };
    const r = Math.abs(this.re) < 1e-4 ? 0.0 : this.re;
    const im = Math.abs(this.im) < 1e-4 ? 0.0 : this.im;
    if (im === 0.0) return fmt(r);
    if (r === 0.0) return im > 0 ? `${fmt(im)}i` : `-${fmt(-im)}i`;
    return im > 0 ? `${fmt(r)} + ${fmt(im)}i` : `${fmt(r)} - ${fmt(-im)}i`;
  }
}

const MatrixSquareRoot = {
  zero(n) { return Array.from({ length: n }, () => Array.from({ length: n }, () => new Complex(0))); },
  identity(n) { return Array.from({ length: n }, (_, i) => Array.from({ length: n }, (_, j) => new Complex(i === j ? 1 : 0))); },
  copy(a) { return a.map(row => row.map(c => new Complex(c.re, c.im))); },
  add(a, b) { return a.map((r, i) => r.map((v, j) => v.plus(b[i][j]))); },
  subtract(a, b) { return a.map((r, i) => r.map((v, j) => v.minus(b[i][j]))); },
  scale(a, k) { return a.map(r => r.map(v => v.times(k))); },
  transpose(a) { return a[0].map((_, j) => a.map(r => r[j])); },
  multiply(a, b) {
    const n = a.length, res = this.zero(n);
    for (let i = 0; i < n; i++) for (let j = 0; j < n; j++) {
      let s = new Complex(0);
      for (let k = 0; k < n; k++) s = s.plus(a[i][k].times(b[k][j]));
      res[i][j] = s;
    }
    return res;
  },
  norm(a) { let res = 0; for (const r of a) for (const v of r) res = Math.hypot(res, v.abs()); return res; },
  finite(a) { return a.every(r => r.every(c => Number.isFinite(c.re) && Number.isFinite(c.im))); },
  sqrtComplex(z) {
    const m = z.abs(); if (m === 0) return new Complex(0);
    return new Complex(Math.sqrt(Math.max(0, (m + z.re) / 2)), (z.im < 0 ? -1 : 1) * Math.sqrt(Math.max(0, (m - z.re) / 2)));
  },
  inverse(input) {
    const n = input.length, a = this.copy(input), inv = this.identity(n);
    const scales = a.map(r => Math.max(1e-15, Math.max(...r.map(c => c.abs()))));
    for (let col = 0; col < n; col++) {
      let pivotRow = col, best = -1;
      for (let row = col; row < n; row++) {
        const ratio = a[row][col].abs() / scales[row];
        if (ratio > best) { best = ratio; pivotRow = row; }
      }
      const piv = a[pivotRow][col];
      if (piv.abs() <= n * Number.EPSILON * scales[pivotRow] * 16) return null;
      if (pivotRow !== col) {
        [a[col], a[pivotRow]] = [a[pivotRow], a[col]];
        [inv[col], inv[pivotRow]] = [inv[pivotRow], inv[col]];
        [scales[col], scales[pivotRow]] = [scales[pivotRow], scales[col]];
      }
      for (let j = 0; j < n; j++) { a[col][j] = a[col][j].div(piv); inv[col][j] = inv[col][j].div(piv); }
      for (let row = 0; row < n; row++) {
        if (row === col) continue;
        const f = a[row][col]; if (f.abs() === 0) continue;
        for (let j = 0; j < n; j++) {
          a[row][j] = a[row][j].minus(f.times(a[col][j]));
          inv[row][j] = inv[row][j].minus(f.times(inv[col][j]));
        }
      }
    }
    return this.finite(inv) ? inv : null;
  },
  solveUpperTriangular(t) {
    const n = t.length, u = this.zero(n);
    for (let i = 0; i < n; i++) u[i][i] = this.sqrtComplex(t[i][i]);
    for (let gap = 1; gap < n; gap++) {
      for (let i = 0; i < n - gap; i++) {
        const j = i + gap; let s = t[i][j];
        for (let k = i + 1; k < j; k++) s = s.minus(u[i][k].times(u[k][j]));
        const denom = u[i][i].plus(u[j][j]);
        if (denom.abs() <= 1e-14) { if (s.abs() <= 1e-14) u[i][j] = new Complex(0); else return null; }
        else u[i][j] = s.div(denom);
      }
    }
    return u;
  },
  compute(input, tol = 1e-8) {
    const n = input.length, a = input.map(r => r.map(v => new Complex(v)));
    const check = (root) => {
      if (!root || !this.finite(root)) return null;
      const err = this.norm(this.subtract(this.multiply(root, root), a)) / Math.max(1, this.norm(a));
      return (Number.isFinite(err) && err <= tol) ? { root, err } : null;
    };
    // 1. Диагональные матрицы
    if (input.every((r, i) => r.every((v, j) => i === j || v === 0))) {
      const root = this.zero(n);
      for (let i = 0; i < n; i++) root[i][i] = this.sqrtComplex(a[i][i]);
      return check(root);
    }
    // 2. Треугольные матрицы (метод Бьёрка — Хаммарлинга)
    if (input.every((r, i) => r.slice(0, i).every(v => v === 0))) return check(this.solveUpperTriangular(a));
    if (input.every((r, i) => r.slice(i + 1).every(v => v === 0))) {
      const ut = this.solveUpperTriangular(this.transpose(a));
      return ut ? check(this.transpose(ut)) : null;
    }
    // 3. Аналитическая формула Кэли — Гамильтона для матриц 2×2
    if (n === 2) {
      const det = a[0][0].times(a[1][1]).minus(a[0][1].times(a[1][0]));
      const tr = a[0][0].plus(a[1][1]);
      const s0 = this.sqrtComplex(det);
      const cands = [];
      for (const s of [s0, s0.neg()]) {
        const t = this.sqrtComplex(tr.plus(s.times(2.0)));
        if (t.abs() <= 1e-14) continue;
        const root = this.zero(2);
        for (let i = 0; i < 2; i++) for (let j = 0; j < 2; j++) {
          root[i][j] = (i === j ? a[i][j].plus(s) : a[i][j]).div(t);
        }
        const acc = check(root);
        if (acc) cands.push(acc);
      }
      if (cands.length) {
        cands.sort((c1, c2) => {
          const im1 = c1.root.flat().reduce((s, x) => s + Math.abs(x.im), 0);
          const im2 = c2.root.flat().reduce((s, x) => s + Math.abs(x.im), 0);
          return (im1 * 1000 + c1.err) - (im2 * 1000 + c2.err);
        });
        return cands[0];
      }
    }
    // 4. Итерации Денмана — Биверса с масштабированием Хайэма
    let y = this.copy(a), z = this.identity(n);
    for (let it = 0; it < 100; it++) {
      const invY = this.inverse(y), invZ = this.inverse(z);
      if (!invY || !invZ) break;
      const ny = this.norm(y), nz = this.norm(z), niy = this.norm(invY), niz = this.norm(invZ);
      const g = (ny > 0 && nz > 0 && niy > 0 && niz > 0) ? Math.min(10, Math.max(0.1, Math.pow((niy * niz) / (ny * nz), 0.25))) : 1.0;
      const nextY = this.scale(this.add(this.scale(y, g), this.scale(invZ, 1 / g)), 0.5);
      const nextZ = this.scale(this.add(this.scale(z, g), this.scale(invY, 1 / g)), 0.5);
      const acc = check(nextY); if (acc) return acc;
      y = nextY; z = nextZ;
    }
    return null;
  },
  computeReal(input, tol = 1e-8) {
    const res = this.compute(input, tol); if (!res) return null;
    const root = res.root, maxMag = Math.max(...root.flat().map(c => c.abs()), 1.0);
    if (root.some(r => r.some(c => Math.abs(c.im) > 1e-6 * maxMag))) return null;
    return root.map(r => r.map(c => Math.abs(c.re) < 1e-12 ? 0 : c.re));
  }
};

class MatrixMath {
  identityMatrix(n) { return Array.from({ length: n }, (_, i) => Array.from({ length: n }, (_, j) => (i === j ? 1.0 : 0.0))); }
  isRectangular(a) { return Array.isArray(a) && a.length > 0 && a[0].length > 0 && a.every(r => r.length === a[0].length); }
  add(a, b) {
    if (!this.isRectangular(a) || !this.isRectangular(b) || a.length !== b.length || a[0].length !== b[0].length) return null;
    return a.map((r, i) => r.map((v, j) => v + b[i][j]));
  }
  subtract(a, b) {
    if (!this.isRectangular(a) || !this.isRectangular(b) || a.length !== b.length || a[0].length !== b[0].length) return null;
    return a.map((r, i) => r.map((v, j) => v - b[i][j]));
  }
  multiply(a, b) {
    if (!this.isRectangular(a) || !this.isRectangular(b) || a[0].length !== b.length) return null;
    const r1 = a.length, c1 = a[0].length, c2 = b[0].length;
    const res = Array.from({ length: r1 }, () => new Array(c2).fill(0.0));
    for (let i = 0; i < r1; i++) for (let j = 0; j < c2; j++) {
      let s = 0.0; for (let k = 0; k < c1; k++) s += a[i][k] * b[k][j];
      res[i][j] = s;
    }
    return res;
  }
  multiplyScalar(a, k) { return a.map(r => r.map(v => v * k)); }
  transpose(a) { return a[0].map((_, j) => a.map(r => r[j])); }
  trace(a) { return (!a.length || a.some(r => r.length !== a.length)) ? null : a.reduce((s, r, i) => s + r[i], 0.0); }
  norm(a) { let res = 0.0; for (const r of a) for (const v of r) res = Math.hypot(res, v); return res; }
  oneNorm(a) {
    let best = 0.0;
    for (let j = 0; j < a[0].length; j++) {
      let s = 0.0; for (let i = 0; i < a.length; i++) s += Math.abs(a[i][j]);
      best = Math.max(best, s);
    }
    return best;
  }
  dotProduct(a, b) {
    const fa = a.flat(), fb = b.flat();
    return fa.length !== fb.length ? null : fa.reduce((s, v, i) => s + v * fb[i], 0.0);
  }
  crossProduct(a, b) {
    const fa = a.flat(), fb = b.flat();
    if (fa.length !== 3 || fb.length !== 3) return null;
    return [[fa[1]*fb[2] - fa[2]*fb[1], fa[2]*fb[0] - fa[0]*fb[2], fa[0]*fb[1] - fa[1]*fb[0]]];
  }
  determinant(matrix) {
    const n = matrix.length; if (!n || matrix.some(r => r.length !== n)) return null;
    const a = matrix.map(r => [...r]), maxElem = Math.max(...a.flat().map(Math.abs));
    if (maxElem === 0) return 0.0;
    let sign = 1.0; const pivotTol = n * Number.EPSILON * maxElem * 16.0;
    for (let k = 0; k < n; k++) {
      let p = k;
      for (let i = k + 1; i < n; i++) if (Math.abs(a[i][k]) > Math.abs(a[p][k])) p = i;
      if (Math.abs(a[p][k]) <= pivotTol) return 0.0;
      if (p !== k) { [a[k], a[p]] = [a[p], a[k]]; sign = -sign; }
      for (let i = k + 1; i < n; i++) {
        const f = a[i][k] / a[k][k];
        for (let j = k + 1; j < n; j++) a[i][j] -= f * a[k][j];
      }
    }
    let res = sign; for (let i = 0; i < n; i++) res *= a[i][i];
    return Math.abs(res) < 1e-13 * Math.pow(maxElem, n) ? 0.0 : res;
  }
  inverse(data) {
    const n = data.length; if (!n || data.some(r => r.length !== n)) return null;
    const aug = data.map((r, i) => [...r, ...Array.from({ length: n }, (_, j) => (i === j ? 1.0 : 0.0))]);
    const scales = data.map(r => Math.max(...r.map(Math.abs)));
    if (scales.some(s => s === 0)) return null;
    for (let col = 0; col < n; col++) {
      let pivotRow = col, maxRatio = -1;
      for (let row = col; row < n; row++) {
        const ratio = Math.abs(aug[row][col]) / scales[row];
        if (ratio > maxRatio) { maxRatio = ratio; pivotRow = row; }
      }
      if (Math.abs(aug[pivotRow][col]) <= n * Number.EPSILON * scales[pivotRow] * 16) return null;
      if (pivotRow !== col) {
        [aug[col], aug[pivotRow]] = [aug[pivotRow], aug[col]];
        [scales[col], scales[pivotRow]] = [scales[pivotRow], scales[col]];
      }
      const piv = aug[col][col];
      for (let j = 0; j < 2 * n; j++) aug[col][j] /= piv;
      for (let row = 0; row < n; row++) {
        if (row === col) continue;
        const f = aug[row][col];
        for (let j = 0; j < 2 * n; j++) aug[row][j] -= f * aug[col][j];
        aug[row][col] = 0;
      }
    }
    return aug.map(r => r.slice(n, 2 * n));
  }
  singularValues(input) {
    const m = input.length, n = input[0].length;
    const scale = Math.max(...input.flat().map(Math.abs));
    if (scale === 0) return new Array(Math.min(m, n)).fill(0);
    const b = input.map(r => r.map(v => v / scale)), eps = 8.0 * Number.EPSILON;
    for (let iter = 0; iter < Math.max(40, 10 * n); iter++) {
      let changed = false;
      for (let p = 0; p < n; p++) for (let q = p + 1; q < n; q++) {
        let alpha = 0, beta = 0, gamma = 0;
        for (let i = 0; i < m; i++) {
          const x = b[i][p], y = b[i][q];
          alpha += x * x; beta += y * y; gamma += x * y;
        }
        if (alpha === 0 || beta === 0 || Math.abs(gamma) <= eps * Math.sqrt(alpha * beta)) continue;
        const zeta = (beta - alpha) / (2 * gamma);
        const t = zeta === 0 ? 1 : (zeta >= 0 ? 1 : -1) / (Math.abs(zeta) + Math.hypot(1, zeta));
        const c = 1 / Math.hypot(1, t), sn = c * t;
        for (let i = 0; i < m; i++) {
          const x = b[i][p], y = b[i][q];
          b[i][p] = c * x - sn * y; b[i][q] = sn * x + c * y;
        }
        changed = true;
      }
      if (!changed) break;
    }
    const vals = Array.from({ length: n }, (_, j) => {
      let v = 0; for (let i = 0; i < m; i++) v = Math.hypot(v, b[i][j]);
      return v * scale;
    });
    return vals.sort((a, b) => b - a).slice(0, Math.min(m, n));
  }
  rank(matrix) {
    if (!matrix.length || !matrix[0].length) return 0;
    const sv = this.singularValues(matrix), largest = sv[0] || 0;
    if (largest === 0) return 0;
    const tol = Math.max(1e-10 * largest, Math.max(matrix.length, matrix[0].length) * Number.EPSILON * largest * 64);
    return sv.filter(x => x > tol).length;
  }
  spectralNorm(a) { return this.singularValues(a)[0] || 0.0; }
  solveLinear(aInput, bInput) {
    const rows = aInput.length, n = aInput[0].length, m = bInput[0].length;
    const a = aInput.map(r => [...r]), b = bInput.map(r => [...r]);
    const scales = a.map(r => Math.max(1e-15, Math.max(...r.map(Math.abs))));
    for (let k = 0; k < n; k++) {
      let piv = k;
      for (let i = k + 1; i < rows; i++) if (Math.abs(a[i][k]) / scales[i] > Math.abs(a[piv][k]) / scales[piv]) piv = i;
      if (Math.abs(a[piv][k]) <= Number.EPSILON * scales[piv] * rows * 16) return null;
      if (piv !== k) {
        [a[k], a[piv]] = [a[piv], a[k]]; [b[k], b[piv]] = [b[piv], b[k]]; [scales[k], scales[piv]] = [scales[piv], scales[k]];
      }
      for (let i = k + 1; i < rows; i++) {
        const f = a[i][k] / a[k][k]; a[i][k] = 0;
        for (let j = k + 1; j < n; j++) a[i][j] -= f * a[k][j];
        for (let j = 0; j < m; j++) b[i][j] -= f * b[k][j];
      }
    }
    const x = Array.from({ length: n }, () => new Array(m).fill(0));
    for (let i = n - 1; i >= 0; i--) for (let j = 0; j < m; j++) {
      let v = b[i][j]; for (let k = i + 1; k < n; k++) v -= a[i][k] * x[k][j];
      x[i][j] = v / a[i][i];
    }
    return x;
  }
  matrixPower(a, k) {
    const n = a.length; if (k === 0) return this.identityMatrix(n);
    const base = k < 0 ? this.inverse(a) : a; if (!base) return null;
    let exp = Math.abs(k), res = this.identityMatrix(n), cur = base.map(r => [...r]);
    while (exp > 0) {
      if (exp % 2 === 1) res = this.multiply(res, cur);
      exp = Math.floor(exp / 2);
      if (exp > 0) cur = this.multiply(cur, cur);
    }
    return res;
  }
  matrixSqrt(a) { return MatrixSquareRoot.computeReal(a); }
  matrixLog(a) {
    const n = a.length, id = this.identityMatrix(n);
    let x = a.map(r => [...r]), roots = 0;
    const sqrtDB = (m) => {
      let y = m.map(r => [...r]), z = this.identityMatrix(n);
      for (let it = 0; it < 50; it++) {
        const iy = this.inverse(y), iz = this.inverse(z); if (!iy || !iz) return null;
        const ny = y.map((r, i) => r.map((v, j) => 0.5 * (v + iz[i][j])));
        const nz = z.map((r, i) => r.map((v, j) => 0.5 * (v + iy[i][j])));
        const diff = this.norm(this.subtract(ny, y)); y = ny; z = nz;
        if (diff < 1e-11) break;
      }
      return this.norm(this.subtract(this.multiply(y, y), m)) / Math.max(1, this.norm(m)) < 1e-3 ? y : null;
    };
    while (this.norm(this.subtract(x, id)) > 0.35 && roots < 20) { x = sqrtDB(x); if (!x) return null; roots++; }
    const xm = this.subtract(x, id), xp = this.add(x, id), invXp = this.inverse(xp);
    if (!invXp) return null;
    const y = this.multiply(xm, invXp), y2 = this.multiply(y, y);
    let pwr = y.map(r => [...r]), sum = y.map(r => [...r]);
    for (let k = 1; k <= 200; k++) {
      pwr = this.multiply(pwr, y2);
      const term = this.multiplyScalar(pwr, 1.0 / (2 * k + 1));
      sum = this.add(sum, term);
      if (this.norm(term) < 1e-13) break;
    }
    return this.multiplyScalar(sum, 2.0 * Math.pow(2, roots));
  }
  matrixTrig(a) {
    const n = a.length, id = this.identityMatrix(n);
    let scaled = a.map(r => [...r]), sq = 0;
    while (this.norm(scaled) > 0.5 && sq < 1023) { scaled = this.multiplyScalar(scaled, 0.5); sq++; }
    const a2 = this.multiply(scaled, scaled);
    let sinT = scaled.map(r => [...r]), cosT = this.identityMatrix(n);
    let sinR = scaled.map(r => [...r]), cosR = this.identityMatrix(n);
    for (let k = 1; k <= 100; k++) {
      sinT = this.multiplyScalar(this.multiply(sinT, a2), -1 / ((2 * k) * (2 * k + 1)));
      cosT = this.multiplyScalar(this.multiply(cosT, a2), -1 / ((2 * k - 1) * (2 * k)));
      sinR = this.add(sinR, sinT); cosR = this.add(cosR, cosT);
      if (Math.max(this.norm(sinT), this.norm(cosT)) <= 4 * Number.EPSILON * Math.max(1, this.norm(sinR), this.norm(cosR))) break;
    }
    for (let i = 0; i < sq; i++) {
      const ns = this.multiplyScalar(this.multiply(sinR, cosR), 2.0);
      const nc = this.subtract(this.multiplyScalar(this.multiply(cosR, cosR), 2.0), id);
      sinR = ns; cosR = nc;
    }
    return [sinR, cosR];
  }
  matrixExponential(a) {
    const n = a.length, theta13 = 5.371920351148152, norm1 = this.oneNorm(a);
    const sq = norm1 <= theta13 ? 0 : Math.max(0, Math.ceil(Math.log2(norm1 / theta13)));
    const x = this.multiplyScalar(a, Math.pow(2, -sq));
    const x2 = this.multiply(x, x), x4 = this.multiply(x2, x2), x6 = this.multiply(x4, x2), id = this.identityMatrix(n);
    const b = [64764752532480000, 32382376266240000, 7771770303897600, 1187353796428800,
      129060195264000, 10559470521600, 670442572800, 33522128640, 1323241920, 40840800, 960960, 16380, 182, 1];
    const lin = (...terms) => terms.reduce((acc, [c, m]) => this.add(acc, this.multiplyScalar(m, c)), Array.from({ length: n }, () => new Array(n).fill(0)));
    const u = this.multiply(x, this.add(this.multiply(x6, lin([b[13], x6], [b[11], x4], [b[9], x2])), lin([b[7], x6], [b[5], x4], [b[3], x2], [b[1], id])));
    const v = this.add(this.multiply(x6, lin([b[12], x6], [b[10], x4], [b[8], x2])), lin([b[6], x6], [b[4], x4], [b[2], x2], [b[0], id]));
    let res = this.solveLinear(this.subtract(v, u), this.add(v, u)); if (!res) return null;
    for (let i = 0; i < sq; i++) res = this.multiply(res, res);
    return res;
  }
  eigenvalues(matrix) {
    const n = matrix.length; if (n === 1) return [new Complex(matrix[0][0])];
    const a = matrix.map(r => [...r]);
    for (let k = 0; k < n - 2; k++) {
      let maxIdx = k + 1;
      for (let i = k + 2; i < n; i++) if (Math.abs(a[i][k]) > Math.abs(a[maxIdx][k])) maxIdx = i;
      if (Math.abs(a[maxIdx][k]) > 1e-12) {
        if (maxIdx !== k + 1) {
          [a[maxIdx], a[k + 1]] = [a[k + 1], a[maxIdx]];
          for (let c = 0; c < n; c++) [a[c][maxIdx], a[c][k + 1]] = [a[c][k + 1], a[c][maxIdx]];
        }
        for (let i = k + 2; i < n; i++) {
          const f = a[i][k] / a[k + 1][k];
          for (let j = k; j < n; j++) a[i][j] -= f * a[k + 1][j];
          for (let j = 0; j < n; j++) a[j][k + 1] += f * a[j][i];
        }
      }
    }
    const res = []; let curN = n, maxIt = 250 * n, sinceDef = 0;
    while (curN > 1 && maxIt-- > 0) {
      sinceDef++; const last = curN - 1;
      if (Math.abs(a[last][last - 1]) <= 1e-10 * (Math.abs(a[last - 1][last - 1]) + Math.abs(a[last][last]) + 1e-12)) {
        res.push(new Complex(a[last][last])); curN--; sinceDef = 0; continue;
      }
      if (curN === 2 || Math.abs(a[last - 1][last - 2]) <= 1e-10 * (Math.abs(a[last - 2][last - 2]) + Math.abs(a[last - 1][last - 1]) + 1e-12)) {
        const tr2 = a[last - 1][last - 1] + a[last][last];
        const det2 = a[last - 1][last - 1] * a[last][last] - a[last - 1][last] * a[last][last - 1];
        const disc = tr2 * tr2 - 4 * det2, cd = Math.abs(disc) < 1e-11 ? 0 : disc;
        if (cd >= 0) { const sq = Math.sqrt(cd); res.push(new Complex((tr2 + sq) / 2), new Complex((tr2 - sq) / 2)); }
        else { const im = Math.sqrt(-cd) / 2; res.push(new Complex(tr2 / 2, im), new Complex(tr2 / 2, -im)); }
        curN -= 2; sinceDef = 0; continue;
      }
      let shift;
      if (sinceDef % 15 === 0) shift = a[last][last] + 0.75 * (Math.abs(a[last][last - 1]) + Math.abs(a[last - 1][last - 2]));
      else {
        const tr = a[last - 1][last - 1] + a[last][last];
        const det2 = a[last - 1][last - 1] * a[last][last] - a[last - 1][last] * a[last][last - 1];
        const disc = tr * tr - 4 * det2;
        if (disc >= 0) {
          const s1 = (tr + Math.sqrt(disc)) / 2, s2 = (tr - Math.sqrt(disc)) / 2;
          shift = Math.abs(s1 - a[last][last]) < Math.abs(s2 - a[last][last]) ? s1 : s2;
        } else shift = tr / 2;
      }
      for (let i = 0; i < curN; i++) a[i][i] -= shift;
      const cosL = new Array(curN - 1), sinL = new Array(curN - 1);
      for (let i = 0; i < curN - 1; i++) {
        const r = Math.hypot(a[i][i], a[i + 1][i]);
        if (r > 1e-15) {
          cosL[i] = a[i][i] / r; sinL[i] = -a[i + 1][i] / r;
          for (let j = i; j < curN; j++) {
            const t1 = a[i][j], t2 = a[i + 1][j];
            a[i][j] = cosL[i] * t1 - sinL[i] * t2; a[i + 1][j] = sinL[i] * t1 + cosL[i] * t2;
          }
        } else { cosL[i] = 1; sinL[i] = 0; }
      }
      for (let i = 0; i < curN - 1; i++) for (let j = 0; j <= Math.min(i + 2, curN - 1); j++) {
        const t1 = a[j][i], t2 = a[j][i + 1];
        a[j][i] = cosL[i] * t1 - sinL[i] * t2; a[j][i + 1] = sinL[i] * t1 + cosL[i] * t2;
      }
      for (let i = 0; i < curN; i++) a[i][i] += shift;
    }
    if (curN === 1) res.push(new Complex(a[0][0]));
    return res.sort((x, y) => y.abs() - x.abs());
  }
  rankWithTolerance(matrix, tol = 1e-5) {
    const r = matrix.length; if (!r) return 0;
    const c = matrix[0].length, a = matrix.map(row => [...row]);
    const effTol = tol * Math.max(1, ...a.flat().map(Math.abs));
    let row = 0;
    for (let col = 0; col < c && row < r; col++) {
      let maxRow = row;
      for (let i = row + 1; i < r; i++) if (Math.abs(a[i][col]) > Math.abs(a[maxRow][col])) maxRow = i;
      if (Math.abs(a[maxRow][col]) < effTol) continue;
      [a[row], a[maxRow]] = [a[maxRow], a[row]];
      for (let i = row + 1; i < r; i++) {
        const f = a[i][col] / a[row][col];
        for (let j = col + 1; j < c; j++) a[i][j] -= f * a[row][j];
      }
      row++;
    }
    return row;
  }
  eigenvectorsWithDetails(matrix, eigenvalues) {
    const n = matrix.length, zero = new Complex(0), usedMult = {};

    const prettyNorm = (vec) => {
      const maxA = Math.max(...vec.map(v => v.abs()));
      if (maxA < 1e-12) return vec;
      const nz = vec.filter(v => v.abs() > 1e-4 * maxA);
      let sm = nz.reduce((a, b) => (a.abs() <= b.abs() ? a : b), new Complex(1));
      const fnz = vec.find(v => v.abs() > 1e-4 * maxA);
      if (fnz && sm.re !== 0 && fnz.re / sm.re < 0) sm = sm.neg();
      const cand = vec.map(v => v.div(sm));
      const clean = cand.every(c => Math.abs(c.re - Math.round(c.re)) < 1e-3 && Math.abs(c.im - Math.round(c.im)) < 1e-3 && c.abs() <= 20);
      if (clean) {
        return cand.map(c => new Complex(
          Math.abs(c.re) < 1e-5 ? 0 : Math.round(c.re),
          Math.abs(c.im) < 1e-5 ? 0 : Math.round(c.im)
        ));
      }
      const bg = nz.reduce((a, b) => (a.abs() >= b.abs() ? a : b), new Complex(1));
      return vec.map(v => v.div(bg));
    };

    const nullspaceComplex = (mIn) => {
      const sh = mIn.map(r => r.map(c => new Complex(c.re, c.im)));
      const pivCols = [];
      let pivRow = 0;
      for (let col = 0; col < n && pivRow < n; col++) {
        let best = pivRow;
        for (let r = pivRow + 1; r < n; r++) if (sh[r][col].abs() > sh[best][col].abs()) best = r;
        if (sh[best][col].abs() <= 1e-5) continue;
        [sh[pivRow], sh[best]] = [sh[best], sh[pivRow]];
        const pv = sh[pivRow][col];
        for (let j = col; j < n; j++) sh[pivRow][j] = sh[pivRow][j].div(pv);
        for (let r = 0; r < n; r++) {
          if (r === pivRow) continue;
          const f = sh[r][col];
          if (f.abs() > 0) for (let j = col; j < n; j++) sh[r][j] = sh[r][j].minus(f.times(sh[pivRow][j]));
        }
        pivCols.push(col);
        pivRow++;
      }
      if (pivCols.length === n) pivCols.pop();
      const freeCols = Array.from({ length: n }, (_, i) => i).filter(i => !pivCols.includes(i));
      return freeCols.map(fc => {
        const v = Array.from({ length: n }, () => zero);
        v[fc] = new Complex(1);
        for (let r = 0; r < pivCols.length; r++) {
          let s = zero;
          for (const f of freeCols) s = s.plus(sh[r][f].times(v[f]));
          v[pivCols[r]] = s.neg();
        }
        return v;
      });
    };

    // Прямое решение неоднородной системы (A - λI) * h = rhs для присоединённых векторов жордановой цепочки
    const solveAssociatedSystem = (nMat, rhs) => {
      const aug = Array.from({ length: n }, (_, r) => [
        ...nMat[r].map(c => new Complex(c.re, c.im)),
        new Complex(rhs[r].re, rhs[r].im)
      ]);
      const pivCols = [];
      let pivRow = 0;
      for (let col = 0; col < n && pivRow < n; col++) {
        let best = pivRow;
        for (let r = pivRow + 1; r < n; r++) if (aug[r][col].abs() > aug[best][col].abs()) best = r;
        if (aug[best][col].abs() <= 1e-5) continue;
        [aug[pivRow], aug[best]] = [aug[best], aug[pivRow]];
        const pv = aug[pivRow][col];
        for (let j = col; j <= n; j++) aug[pivRow][j] = aug[pivRow][j].div(pv);
        for (let r = 0; r < n; r++) {
          if (r === pivRow) continue;
          const f = aug[r][col];
          if (f.abs() > 0) for (let j = col; j <= n; j++) aug[r][j] = aug[r][j].minus(f.times(aug[pivRow][j]));
        }
        pivCols.push(col);
        pivRow++;
      }
      for (let r = pivRow; r < n; r++) {
        if (aug[r][n].abs() > 1e-4) return null;
      }
      const h0 = Array.from({ length: n }, () => zero);
      for (let r = 0; r < pivCols.length; r++) h0[pivCols[r]] = aug[r][n];

      const kerBasis = nullspaceComplex(nMat);
      const candidates = [h0];
      for (const kv of kerBasis) {
        for (const c of [1.0, -1.0]) {
          candidates.push(h0.map((v, i) => v.plus(kv[i].times(c))));
        }
      }
      candidates.sort((a, b) => {
        const nza = a.filter(x => x.abs() > 1e-5).length;
        const nzb = b.filter(x => x.abs() > 1e-5).length;
        if (nza !== nzb) return nza - nzb;
        const nega = a.filter(x => x.re < -1e-5).length;
        const negb = b.filter(x => x.re < -1e-5).length;
        if (nega !== negb) return nega - negb;
        const fa = a.findIndex(x => x.abs() > 1e-5);
        const fb = b.findIndex(x => x.abs() > 1e-5);
        return fb - fa;
      });
      return candidates[0];
    };

    const isIndep = (existing, cand) => {
      if (!existing.length) return cand.some(c => c.abs() > 1e-5);
      const cols = existing.length + 1;
      const rm = Array.from({ length: 2 * n }, () => new Array(2 * cols).fill(0));
      const all = [...existing, cand];
      for (let c = 0; c < cols; c++) {
        for (let r = 0; r < n; r++) {
          rm[r][c] = all[c][r].re;
          rm[r][c + cols] = -all[c][r].im;
          rm[r + n][c] = all[c][r].im;
          rm[r + n][c + cols] = all[c][r].re;
        }
      }
      return Math.floor(this.rankWithTolerance(rm, 1e-4) / 2) > existing.length;
    };

    const cache = {};
    return eigenvalues.map((lambda, idx) => {
      let rep = idx;
      for (let p = 0; p < idx; p++) {
        if (eigenvalues[p].minus(lambda).abs() <= 1e-4) { rep = p; break; }
      }
      const mIdx = usedMult[rep] || 0;
      usedMult[rep] = mIdx + 1;

      if (!cache[rep]) {
        const nMat = matrix.map((r, i) => r.map((v, j) => (i === j ? new Complex(v).minus(lambda) : new Complex(v))));
        const list = [];
        for (const rv of nullspaceComplex(nMat)) {
          const nv = prettyNorm(rv);
          if (isIndep(list.map(x => x.vec), nv)) list.push({ vec: nv, lvl: 1 });
        }
        let curP = nMat, deg = 2;
        while (list.length < n && deg <= n) {
          // 1. Сначала ищем точный присоединённый вектор (A - λI) * h = v_prev
          const prevVecs = list.filter(x => x.lvl === deg - 1);
          for (const pv of prevVecs) {
            const hSol = solveAssociatedSystem(nMat, pv.vec);
            if (hSol && isIndep(list.map(x => x.vec), hSol)) {
              list.push({ vec: hSol, lvl: deg });
            }
          }
          // 2. Добираем из Ker((A - λI)^deg), если нужно
          curP = MatrixSquareRoot.multiply(curP, nMat);
          for (const cand of nullspaceComplex(curP)) {
            if (isIndep(list.map(x => x.vec), cand)) {
              list.push({ vec: cand, lvl: deg });
            }
          }
          deg++;
        }
        cache[rep] = list;
      }

      const item = cache[rep][mIdx] || cache[rep][cache[rep].length - 1] || { vec: Array.from({ length: n }, () => zero), lvl: 1 };
      const rawVec = item.vec;
      const cv = item.lvl > 1
        ? rawVec.map(c => new Complex(
            Math.abs(c.re - Math.round(c.re)) < 1e-4 ? Math.round(c.re) : c.re,
            Math.abs(c.im - Math.round(c.im)) < 1e-4 ? Math.round(c.im) : c.im
          ))
        : prettyNorm(rawVec);

      let l2 = 0;
      for (const v of cv) l2 = Math.hypot(l2, v.abs());
      const uVec = cv.map(v => v.div(l2 > 0 ? l2 : 1));

      return {
        index: idx + 1,
        lambda,
        isAssociated: item.lvl > 1,
        chainLevel: item.lvl,
        canon: `[ ${cv.map(v => v.toString()).join(", ")} ]ᵀ`,
        unit: `[ ${uVec.map(v => v.toString()).join(", ")} ]ᵀ`
      };
    });
  }
  clusterEigenvalues(eigs) {
    const clusters = [], scale = Math.max(1, ...eigs.map(e => e.abs())), tol = 1e-4 * scale;
    for (const e of eigs) {
      let found = false;
      for (const c of clusters) {
        if (Math.abs(e.re - c.re) < tol && Math.abs(e.im - c.im) < tol) {
          c.re = (c.re * c.count + e.re) / (c.count + 1); c.im = (c.im * c.count + e.im) / (c.count + 1);
          c.count++; found = true; break;
        }
      }
      if (!found) clusters.push({ re: e.re, im: e.im, count: 1 });
    }
    return clusters;
  }
  getJordanBlocks(matrix, lRe, lIm, mult) {
    if (mult <= 1) return [1];
    const n = matrix.length, nMat = matrix.map((r, i) => r.map((v, j) => new Complex(v - (i === j ? lRe : 0), i === j ? -lIm : 0)));
    const rankC = (m) => {
      const rm = Array.from({ length: 2 * n }, () => new Array(2 * n).fill(0));
      for (let i = 0; i < n; i++) for (let j = 0; j < n; j++) {
        rm[i][j] = m[i][j].re; rm[i][j + n] = -m[i][j].im; rm[i + n][j] = m[i][j].im; rm[i + n][j + n] = m[i][j].re;
      }
      return Math.floor(this.rankWithTolerance(rm, 1e-5) / 2);
    };
    const ranks = [n, rankC(nMat)]; let cur = nMat;
    while (ranks.length <= mult + 1 && ranks[ranks.length - 1] < ranks[ranks.length - 2]) {
      cur = MatrixSquareRoot.multiply(cur, nMat); ranks.push(rankC(cur));
    }
    while (ranks.length <= n + 1) ranks.push(ranks[ranks.length - 1]);
    const blocks = [];
    for (let s = 1; s <= n; s++) {
      const cnt = ranks[s - 1] - 2 * ranks[s] + ranks[s + 1];
      for (let k = 0; k < cnt; k++) blocks.push(s);
    }
    return blocks.reduce((a, b) => a + b, 0) === mult ? blocks.sort((a, b) => b - a) : [mult];
  }
  jordanForm(matrix) {
    const n = matrix.length, raw = this.eigenvalues(matrix), clusters = this.clusterEigenvalues(raw);
    const jnf = Array.from({ length: n }, () => new Array(n).fill("0"));
    let pos = 0, mult = false;
    for (const c of clusters) {
      if (c.count > 1) mult = true;
      for (const bs of this.getJordanBlocks(matrix, c.re, c.im, c.count)) {
        for (let i = 0; i < bs && pos + i < n; i++) {
          jnf[pos + i][pos + i] = new Complex(c.re, c.im).toString();
          if (i < bs - 1 && pos + i + 1 < n) jnf[pos + i][pos + i + 1] = "1";
        }
        pos += bs;
      }
    }
    return [mult ? "Комплексная ЖНФ:" : "Диагональный вид (Простой спектр):", jnf];
  }
  realJordanForm(matrix) {
    const n = matrix.length, raw = this.eigenvalues(matrix), clusters = this.clusterEigenvalues(raw);
    const jnf = Array.from({ length: n }, () => new Array(n).fill("0")), used = new Array(clusters.length).fill(false);
    let pos = 0; const fmt = (v) => (Math.abs(v) < 1e-5 ? "0" : Number(v.toFixed(3)).toString());
    for (let i = 0; i < clusters.length; i++) {
      if (used[i]) continue;
      const c = clusters[i];
      if (Math.abs(c.im) <= 1e-4 * Math.max(1, Math.abs(c.re))) {
        for (const bs of this.getJordanBlocks(matrix, c.re, 0, c.count)) {
          for (let k = 0; k < bs && pos + k < n; k++) {
            jnf[pos + k][pos + k] = fmt(c.re);
            if (k < bs - 1 && pos + k + 1 < n) jnf[pos + k][pos + k + 1] = "1";
          }
          pos += bs;
        }
        used[i] = true;
      } else {
        const conj = clusters.findIndex((x, j) => j > i && !used[j] && Math.abs(x.re - c.re) < 1e-3 && Math.abs(x.im + c.im) < 1e-3);
        const a = fmt(c.re), b = fmt(Math.abs(c.im)), mb = fmt(-Math.abs(c.im));
        for (const bs of this.getJordanBlocks(matrix, c.re, c.im, c.count)) {
          for (let k = 0; k < bs; k++) {
            const cur = pos + 2 * k;
            if (cur + 1 < n) {
              jnf[cur][cur] = a; jnf[cur][cur + 1] = b; jnf[cur + 1][cur] = mb; jnf[cur + 1][cur + 1] = a;
              if (k < bs - 1 && cur + 3 < n) { jnf[cur][cur + 2] = "1"; jnf[cur + 1][cur + 3] = "1"; }
            }
          }
          pos += 2 * bs;
        }
        used[i] = true; if (conj !== -1) used[conj] = true;
      }
    }
    return ["Вещественная Жорданова форма (РЖНФ):", jnf];
  }
  trimZeroColsB(b) {
    let last = 0;
    for (let c = b[0].length - 1; c >= 0; c--) if (b.some(r => Math.abs(r[c]) > 1e-12)) { last = c; break; }
    return b.map(r => r.slice(0, last + 1));
  }
  trimZeroRowsC(c) {
    let last = 0;
    for (let r = c.length - 1; r >= 0; r--) if (c[r].some(v => Math.abs(v) > 1e-12)) { last = r; break; }
    return c.slice(0, last + 1).map(r => [...r]);
  }
  controllability(a, b) {
    const n = a.length, cleanB = this.trimZeroColsB(b), m = cleanB[0].length;
    const wc = Array.from({ length: n }, () => new Array(n * m).fill(0));
    let cur = cleanB;
    for (let k = 0; k < n; k++) {
      for (let i = 0; i < n; i++) for (let j = 0; j < m; j++) wc[i][k * m + j] = Math.abs(cur[i][j]) < 1e-12 ? 0 : cur[i][j];
      if (k < n - 1) cur = this.multiply(a, cur);
    }
    return wc;
  }
  observability(a, c) {
    const n = a.length, cleanC = this.trimZeroRowsC(c), p = cleanC.length;
    const wo = Array.from({ length: n * p }, () => new Array(n).fill(0));
    let cur = cleanC;
    for (let k = 0; k < n; k++) {
      for (let i = 0; i < p; i++) for (let j = 0; j < n; j++) wo[k * p + i][j] = Math.abs(cur[i][j]) < 1e-12 ? 0 : cur[i][j];
      if (k < n - 1) cur = this.multiply(cur, a);
    }
    return wo;
  }
  frobeniusForm(matA, matB) {
    const n = matA.length, cleanB = this.trimZeroColsB(matB);
    let bVec = cleanB.map(r => [r[0]]);
    if (cleanB[0].length > 1) {
      for (let c = 0; c < cleanB[0].length; c++) {
        const cand = cleanB.map(r => [r[c]]);
        if (this.inverse(this.controllability(matA, cand))) { bVec = cand; break; }
      }
    }
    const wcSq = this.controllability(matA, bVec).map(r => r.slice(0, n));
    const wcInv = this.inverse(wcSq);
    if (!wcInv) throw new Error("Система неуправляема (det W_c = 0)! Формы Фробениуса не существует.");
    const tInv = []; let curQ = [...wcInv[n - 1]];
    for (let i = 0; i < n; i++) {
      tInv.push(curQ);
      if (i < n - 1) {
        const nextQ = new Array(n).fill(0);
        for (let j = 0; j < n; j++) for (let k = 0; k < n; k++) nextQ[j] += curQ[k] * matA[k][j];
        curQ = nextQ;
      }
    }
    const tMat = this.inverse(tInv);
    if (!tMat) throw new Error("Матрица преобразования T вырождена.");
    const aTilde = this.multiply(this.multiply(tInv, matA), tMat);
    const bTilde = this.multiply(tInv, bVec);
    return [aTilde, bTilde, tMat];
  }
  realNullspaceBasis(mInput) {
    const rows = mInput.length, cols = mInput[0].length, a = mInput.map(r => [...r]), pivCols = [];
    let pivRow = 0; const tol = 1e-7 * Math.max(1, ...a.flat().map(Math.abs));
    for (let col = 0; col < cols && pivRow < rows; col++) {
      let best = pivRow;
      for (let r = pivRow + 1; r < rows; r++) if (Math.abs(a[r][col]) > Math.abs(a[best][col])) best = r;
      if (Math.abs(a[best][col]) <= tol) continue;
      [a[pivRow], a[best]] = [a[best], a[pivRow]];
      const pv = a[pivRow][col];
      for (let j = col; j < cols; j++) a[pivRow][j] /= pv;
      for (let r = 0; r < rows; r++) {
        if (r === pivRow) continue;
        const f = a[r][col]; if (Math.abs(f) > 0) for (let j = col; j < cols; j++) a[r][j] -= f * a[pivRow][j];
      }
      pivCols.push(col); pivRow++;
    }
    const freeCols = Array.from({ length: cols }, (_, i) => i).filter(c => !pivCols.includes(c));
    return freeCols.map(fc => {
      const v = new Array(cols).fill(0); v[fc] = 1.0;
      for (let idx = 0; idx < pivCols.length; idx++) {
        let s = 0; for (const f of freeCols) s += a[idx][f] * v[f];
        v[pivCols[idx]] = Math.abs(s) < 1e-10 ? 0 : -s;
      }
      return v;
    });
  }
  columnSpaceBasis(m) {
    const rows = m.length, cols = m[0].length, target = this.rank(m);
    if (!target) return [];
    const basis = [];
    for (let c = 0; c < cols && basis.length < target; c++) {
      const raw = m.map(r => r[c]), maxA = Math.max(...raw.map(Math.abs));
      if (maxA < 1e-9) continue;
      const nz = raw.filter(v => Math.abs(v) > 1e-4 * maxA);
      let scale = Math.min(...nz.map(Math.abs));
      if (nz.length === 1) scale = nz[0];
      const vec = raw.map(v => { const sc = v / scale; return Math.abs(sc - Math.round(sc)) < 1e-5 ? Math.round(sc) : sc; });
      const test = Array.from({ length: rows }, (_, r) => [...basis.map(b => b[r]), vec[r]]);
      if (this.rank(test) > basis.length) basis.push(vec);
    }
    return basis.sort((a, b) => {
      const ia = a.findIndex(v => Math.abs(v) > 1e-6), ib = b.findIndex(v => Math.abs(v) > 1e-6);
      return (ia === -1 ? 999 : ia) - (ib === -1 ? 999 : ib);
    });
  }
  isInSubspace(basis, v) {
    if (!basis.length) return v.every(x => Math.abs(x) < 1e-9);
    const n = v.length;
    const m1 = Array.from({ length: n }, (_, r) => basis.map(b => b[r]));
    const m2 = Array.from({ length: n }, (_, r) => [...basis.map(b => b[r]), v[r]]);
    return this.rank(m2) === this.rank(m1);
  }
  intersectSubspaces(uB, vB, n) {
    if (!uB.length || !vB.length) return [];
    const p = uB.length, comb = Array.from({ length: n }, (_, r) => [...uB.map(u => u[r]), ...vB.map(v => -v[r])]);
    const inter = [];
    for (const nv of this.realNullspaceBasis(comb)) {
      const vec = new Array(n).fill(0);
      for (let i = 0; i < p; i++) for (let r = 0; r < n; r++) vec[r] += nv[i] * uB[i][r];
      const maxA = Math.max(...vec.map(Math.abs));
      if (maxA > 1e-7) {
        const nz = vec.filter(x => Math.abs(x) > 1e-4 * maxA);
        let sc = Math.min(...nz.map(Math.abs)); if (nz.length === 1) sc = nz[0];
        const normV = vec.map(x => { const vs = x / sc; return Math.abs(vs - Math.round(vs)) < 1e-4 ? Math.round(vs) : vs; });
        if (!this.isInSubspace(inter, normV)) inter.push(normV);
      }
    }
    return inter;
  }
  kalmanNotebookDecomposition(matA, matB) {
    const n = matA.length, cleanB = this.trimZeroColsB(matB), w = this.controllability(matA, cleanB), r = this.rank(w);
    if (r <= 0 || r >= n) return null;
    const cols = [];
    for (let c = 0; c < w[0].length && cols.length < r; c++) {
      const cand = w.map(row => row[c]); if (cand.every(v => Math.abs(v) < 1e-12)) continue;
      const test = Array.from({ length: n }, (_, row) => [...cols.map(b => b[row]), cand[row]]);
      if (this.rank(test) > cols.length) cols.push(cand);
    }
    if (cleanB[0].length > 1) {
      cols.sort((a, b) => {
        const ia = a.findIndex(v => Math.abs(v) > 1e-6), ib = b.findIndex(v => Math.abs(v) > 1e-6);
        return (ia === -1 ? 999 : ia) - (ib === -1 ? 999 : ib);
      });
    }
    const unitOrder = cols.some(col => col[n - 1] < -1e-9)
      ? [...Array.from({ length: n - 1 }, (_, i) => i + 1), 0]
      : Array.from({ length: n }, (_, i) => n - 1 - i);
    for (const k of unitOrder) {
      if (cols.length === n) break;
      const eVec = Array.from({ length: n }, (_, row) => (row === k ? 1.0 : 0.0));
      const test = Array.from({ length: n }, (_, row) => [...cols.map(b => b[row]), eVec[row]]);
      if (this.rank(test) > cols.length) cols.push(eVec);
    }
    const sMat = Array.from({ length: n }, (_, r) => cols.map(c => c[r])), sInv = this.inverse(sMat);
    return sInv ? [r, sMat, sInv] : null;
  }
  kalmanOrthogonalDecomposition(matA, matB) {
    const n = matA.length, cleanB = this.trimZeroColsB(matB), w = this.controllability(matA, cleanB), r = this.rank(w);
    if (r <= 0 || r >= n) return null;
    const cols = [];
    for (let c = 0; c < w[0].length && cols.length < r; c++) {
      const cand = w.map(row => row[c]); if (cand.every(v => Math.abs(v) < 1e-12)) continue;
      const test = Array.from({ length: n }, (_, row) => [...cols.map(b => b[row]), cand[row]]);
      if (this.rank(test) > cols.length) cols.push(cand);
    }
    try {
      for (const lam of this.eigenvalues(matA)) {
        if (cols.length === n) break;
        if (Math.abs(lam.im) < 1e-6) {
          const sh = matA.map((row, ri) => row.map((v, ci) => v - (ri === ci ? lam.re : 0)));
          for (const rv of this.realNullspaceBasis(sh)) {
            if (cols.length === n) break;
            const test = Array.from({ length: n }, (_, row) => [...cols.map(b => b[row]), rv[row]]);
            if (this.rank(test) > cols.length) cols.push(rv);
          }
        }
      }
    } catch (_) {}
    for (let k = n - 1; k >= 0 && cols.length < n; k--) {
      const eVec = Array.from({ length: n }, (_, row) => (row === k ? 1.0 : 0.0));
      const test = Array.from({ length: n }, (_, row) => [...cols.map(b => b[row]), eVec[row]]);
      if (this.rank(test) > cols.length) cols.push(eVec);
    }
    const sMat = Array.from({ length: n }, (_, row) => cols.map(c => c[row])), sInv = this.inverse(sMat);
    return sInv ? [r, sMat, sInv] : null;
  }
  getControllableModeFlags(matA, matB, allEigs = this.eigenvalues(matA)) {
    const n = matA.length, cleanB = this.trimZeroColsB(matB), r = this.rank(this.controllability(matA, cleanB));
    if (r >= n) return new Array(n).fill(true);
    if (r <= 0) return new Array(n).fill(false);
    const kd = this.kalmanOrthogonalDecomposition(matA, cleanB);
    if (!kd) return Array.from({ length: n }, (_, i) => i < r);
    const [, tMat, tInv] = kd, aT = this.multiply(this.multiply(tInv, matA), tMat);
    const aUn = Array.from({ length: n - r }, (_, i) => Array.from({ length: n - r }, (_, j) => aT[r + i][r + j]));
    const unPoles = this.eigenvalues(aUn), flags = new Array(n).fill(true);
    for (const up of unPoles) {
      let best = -1, bestD = Infinity;
      for (let i = 0; i < n; i++) {
        if (!flags[i]) continue;
        const d = Math.hypot(allEigs[i].re - up.re, allEigs[i].im - up.im);
        if (d < bestD) { bestD = d; best = i; }
      }
      if (best !== -1) flags[best] = false;
    }
    return flags;
  }
  ackermann(matA, matB, desiredPoles) {
    const n = matA.length, cleanB = this.trimZeroColsB(matB), m = cleanB[0].length;
    const wTot = this.controllability(matA, cleanB), rTot = this.rank(wTot);
    if (rTot <= 0) throw new Error("Матрица B нулевая — управление невозможно.");
    if (m > 1) {
      let chosenG = new Array(m).fill(0), bEff = null;
      for (let col = 0; col < m; col++) {
        const sb = cleanB.map(r => [r[col]]);
        if (this.rank(this.controllability(matA, sb)) === rTot) { chosenG[col] = 1; bEff = sb; break; }
      }
      if (!bEff) { chosenG = new Array(m).fill(1); bEff = cleanB.map(r => [r.reduce((a, b) => a + b, 0)]); }
      const k1 = this.ackermann(matA, bEff, desiredPoles);
      return Array.from({ length: m }, (_, r) => Array.from({ length: n }, (_, c) => chosenG[r] * k1[0][c]));
    }
    const ackFull = (ac, bc, poles) => {
      const dim = ac.length; let coeffs = [new Complex(1)];
      for (const p of poles) {
        const next = Array.from({ length: coeffs.length + 1 }, () => new Complex(0));
        for (let i = 0; i < coeffs.length; i++) {
          next[i] = next[i].plus(coeffs[i]); next[i + 1] = next[i + 1].minus(coeffs[i].times(p));
        }
        coeffs = next;
      }
      if (coeffs.some(c => Math.abs(c.im) > 1e-3)) throw new Error("Комплексные полюса должны образовывать сопряжённые пары!");
      const wcInv = this.inverse(this.controllability(ac, bc));
      let poly = Array.from({ length: dim }, () => new Array(dim).fill(0));
      for (let i = 0; i <= dim; i++) poly = this.add(poly, this.multiplyScalar(this.matrixPower(ac, dim - i), coeffs[i].re));
      const last = wcInv[dim - 1], kRow = [new Array(dim).fill(0)];
      for (let j = 0; j < dim; j++) for (let i = 0; i < dim; i++) kRow[0][j] += last[i] * poly[i][j];
      return kRow;
    };
    if (rTot === n) return ackFull(matA, cleanB, desiredPoles);
    const [, tMat, tInv] = this.kalmanOrthogonalDecomposition(matA, cleanB);
    const aT = this.multiply(this.multiply(tInv, matA), tMat), bT = this.multiply(tInv, cleanB);
    const ac = Array.from({ length: rTot }, (_, i) => Array.from({ length: rTot }, (_, j) => aT[i][j]));
    const bc = Array.from({ length: rTot }, (_, i) => [bT[i][0]]);
    const flags = this.getControllableModeFlags(matA, cleanB);
    const target = desiredPoles.length === n ? desiredPoles.filter((_, i) => flags[i]) : desiredPoles.slice(0, rTot);
    const kc = ackFull(ac, bc, target);
    const kTilde = [Array.from({ length: n }, (_, j) => (j < rTot ? kc[0][j] : 0))];
    return this.multiply(kTilde, tInv);
  }
  getObservableModeFlags(matA, matC, allEigs = this.eigenvalues(matA)) {
    const aT = this.transpose(matA), cleanC = this.trimZeroRowsC(matC);
    return this.getControllableModeFlags(aT, this.transpose(cleanC), allEigs);
  }
  luenbergerObserver(matA, matC, poles) {
    const aT = this.transpose(matA), cleanC = this.trimZeroRowsC(matC);
    const cCol = Array.from({ length: matA.length }, (_, r) => [cleanC[0][r]]);
    return this.transpose(this.ackermann(aT, cCol, poles));
  }
  solveLQR(matA, matB, matQ, matR) {
    const n = matA.length, rInv = this.inverse(matR);
    if (!rInv) throw new Error("Матрица R вырождена");
    const bRinvBt = this.multiply(this.multiply(matB, rInv), this.transpose(matB)), aT = this.transpose(matA);
    let hMat = Array.from({ length: 2 * n }, () => new Array(2 * n).fill(0));
    for (let i = 0; i < n; i++) for (let j = 0; j < n; j++) {
      hMat[i][j] = matA[i][j]; hMat[i][j + n] = -bRinvBt[i][j]; hMat[i + n][j] = -matQ[i][j]; hMat[i + n][j + n] = -aT[i][j];
    }
    for (let it = 0; it < 120; it++) {
      const hInv = this.inverse(hMat);
      if (!hInv) throw new Error("Гамильтонова матрица не обратима (проверьте управляемость и Q ≥ 0, R > 0)");
      const g = Math.min(10, Math.max(0.1, Math.sqrt(this.norm(hInv) / this.norm(hMat))));
      const nextH = this.add(this.multiplyScalar(hMat, 0.5 * g), this.multiplyScalar(hInv, 0.5 / g));
      const d = this.norm(this.subtract(hMat, nextH)); hMat = nextH;
      if (d <= 1e-11 * Math.max(1, this.norm(nextH))) break;
    }
    const lhs = Array.from({ length: 2 * n }, () => new Array(n).fill(0)), rhs = Array.from({ length: 2 * n }, () => new Array(n).fill(0));
    for (let i = 0; i < n; i++) for (let j = 0; j < n; j++) {
      lhs[i][j] = hMat[i][j + n]; rhs[i][j] = -(hMat[i][j] + (i === j ? 1 : 0));
      lhs[i + n][j] = hMat[i + n][j + n] + (i === j ? 1 : 0); rhs[i + n][j] = -hMat[i + n][j];
    }
    const pMat = this.solveLinear(lhs, rhs); if (!pMat) throw new Error("Не удалось извлечь решение Риккати P");
    for (let i = 0; i < n; i++) for (let j = 0; j < n; j++) { const avg = 0.5 * (pMat[i][j] + pMat[j][i]); pMat[i][j] = avg; pMat[j][i] = avg; }
    return [pMat, this.multiply(rInv, this.multiply(this.transpose(matB), pMat))];
  }
  formatControlLaw(theta) {
    const row = theta[0] || [], terms = [];
    row.forEach((v, i) => {
      if (Math.abs(v) > 1e-5) {
        const cl = Math.abs(v - Math.round(v)) < 1e-5 ? String(Math.round(v)) : v.toFixed(3).replace(/\.?0+$/, '');
        const sign = v < 0 && terms.length ? " − " : v < 0 ? "−" : terms.length ? " + " : "";
        const absStr = cl.replace(/^-/, ''), coef = absStr === "1" ? "" : absStr;
        terms.push(`${sign}${coef}x_${i + 1}`);
      }
    });
    return terms.length ? terms.join("") : "0";
  }
  fullBlockDiagramAnalysis(matA, matB, matC) {
    const n = matA.length;
    const hasA = matA.some(r => r.some(v => Math.abs(v) > 1e-12));
    const hasB = matB.some(r => r.some(v => Math.abs(v) > 1e-12));
    const hasC = matC.some(r => r.some(v => Math.abs(v) > 1e-12));
    if (!hasA) return `Вывод: матрица A не введена.\nВведите матрицу состояния A (${n}×${n}).`;
    const eigs = this.eigenvalues(matA), eigStr = eigs.map((c, i) => `λ_${i + 1} = ${c}`).join(", ");
    if (!hasB && !hasC) return `АВТОНОМНАЯ СИСТЕМА: x' = Ax\nВывод: матрицы B и C не введены.\n\nСобственные числа матрицы A:\nλ = { ${eigStr} }`;
    const cleanB = hasB ? this.trimZeroColsB(matB) : matB, cleanC = hasC ? this.trimZeroRowsC(matC) : matC, lines = [];
    lines.push("СИСТЕМА В ПРОСТРАНСТВЕ СОСТОЯНИЙ:");
    if (hasB && hasC) {
      lines.push(`⎧ x' = Ax + Bu   (A: ${n}×${n}, B: ${cleanB.length}×${cleanB[0].length})`);
      lines.push(`⎩ y  = Cx        (C: ${cleanC.length}×${cleanC[0].length})`);
    } else if (hasB) lines.push(`x' = Ax + Bu   (A: ${n}×${n}, B: ${cleanB.length}×${cleanB[0].length})`);
    else lines.push(`⎧ x' = Ax\n⎩ y  = Cx   (A: ${n}×${n}, C: ${cleanC.length}×${cleanC[0].length})`);
    lines.push(`\nСобственные числа матрицы A:\nλ = { ${eigStr} }\n════════════════════════════════`);
    let isFullCtrl = false, isStabilizable = false, wcMat = null, isFullObs = false, isDetectable = false, woMat = null;

    if (hasB) {
      lines.push("1) ПРОВЕРКА УПРАВЛЯЕМОСТИ:");
      wcMat = this.controllability(matA, cleanB);
      const rankC = this.rank(wcMat); isFullCtrl = rankC === n;
      lines.push("Матрица управляемости C(A,B) = U = [B, AB, A²B, ...]:\n" + formatMatrixAligned(wcMat));
      if (wcMat.length === wcMat[0].length) lines.push(`det(U) = ${formatNumber(this.determinant(wcMat) || 0)}`);
      lines.push(`rank(U) = ${rankC} (при n = ${n})`);
      if (isFullCtrl) {
        isStabilizable = true;
        lines.push(`➔ Вывод: система полностью управляема (dim управляемого п/п-ва = ${n}). Регулятор построить можно.`);
      } else {
        lines.push("➔ Вывод: система не является полностью управляемой.");
        lines.push(`• Управляемое подпространство: dim = ${rankC}\n• Неуправляемое подпространство: dim = ${n - rankC}`);
        const kd = this.kalmanNotebookDecomposition(matA, cleanB);
        if (kd) {
          const [r, sMat, sInv] = kd;
          const aTilde = this.multiply(this.multiply(sInv, matA), sMat), bTilde = this.multiply(sInv, cleanB);
          const aCtrl = Array.from({ length: r }, (_, i) => Array.from({ length: r }, (_, j) => aTilde[i][j]));
          const aUn = Array.from({ length: n - r }, (_, i) => Array.from({ length: n - r }, (_, j) => aTilde[r + i][r + j]));
          const unEigs = this.eigenvalues(aUn);
          isStabilizable = unEigs.every(e => e.re < -1e-7);
          lines.push("\nПостроим разложение Калмана на управляемое и неуправляемое подпространства:");
          lines.push(`Матрица преобразования S [стб: упр=${r}, неупр=${n - r}]:\n` + formatMatrixAligned(sMat));
          lines.push("Обратная матрица S⁻¹:\n" + formatMatrixAligned(sInv));
          lines.push(`Новая матрица Ã = S⁻¹ A S = [Ac, A12; 0, Au] [блоки: упр=${r}, неупр=${n - r}]:\n` + formatMatrixAligned(aTilde));
          lines.push(`Новая матрица B̃ = S⁻¹ B [стр: упр=${r}, неупр=${n - r}]:\n` + formatMatrixAligned(bTilde));
          lines.push(`Управляемая часть Ac (${r}×${r}):\n` + formatMatrixAligned(aCtrl));
          lines.push(`Неуправляемая часть Au (${n - r}×${n - r}):\n` + formatMatrixAligned(aUn));
          lines.push(`Собственные числа неуправляемой части Au: λ(Au) = { ${unEigs.join(", ")} }`);
          lines.push(isStabilizable
            ? "➔ Собственные числа неуправляемой части Re(λ) < 0 (неуправляемая часть устойчива).\n➔ Вывод: система СТАБИЛИЗИРУЕМА. Регулятор построить можно."
            : "➔ Есть собственные числа неуправляемой части Re(λ) ≥ 0 (неуправляемая часть неустойчива).\n➔ Вывод: система НЕ СТАБИЛИЗИРУЕМА. Регулятор построить нельзя.");
        }
      }
      lines.push("════════════════════════════════");
    }

    if (hasC) {
      lines.push(hasB ? "2) ПРОВЕРКА НАБЛЮДАЕМОСТИ:" : "1) ПРОВЕРКА НАБЛЮДАЕМОСТИ:");
      woMat = this.observability(matA, cleanC);
      const rankO = this.rank(woMat); isFullObs = rankO === n;
      lines.push("Матрица наблюдаемости O = [C; CA; CA²; ...]:\n" + formatMatrixAligned(woMat));
      if (woMat.length === woMat[0].length) lines.push(`det(O) = ${formatNumber(this.determinant(woMat) || 0)}`);
      lines.push(`rank(O) = ${rankO} (при n = ${n})`);
      if (isFullObs) {
        isDetectable = true;
        lines.push(`➔ Вывод: система полностью наблюдаема (dim наблюдаемого п/п-ва = ${n}). Наблюдатель построить можно.`);
      } else {
        lines.push(`➔ Вывод: система не является полностью наблюдаемой.\n• Наблюдаемое п/п-во: dim = ${rankO}\n• Ненаблюдаемое п/п-во: dim = ${n - rankO}`);
        const aT = this.transpose(matA), cT = this.transpose(cleanC), kdO = this.kalmanNotebookDecomposition(aT, cT);
        if (kdO) {
          const [rO, tDual, tInvDual] = kdO;
          const aTD = this.multiply(this.multiply(tInvDual, aT), tDual);
          const aUnO = Array.from({ length: n - rO }, (_, i) => Array.from({ length: n - rO }, (_, j) => aTD[rO + i][rO + j]));
          const unOEigs = this.eigenvalues(aUnO);
          isDetectable = unOEigs.every(e => e.re < -1e-7);
          const tObs = this.transpose(tInvDual), tObsInv = this.transpose(tDual);
          lines.push("\nПостроим разложение на наблюдаемое и ненаблюдаемое подпространства:");
          lines.push("Матрица перехода T_o:\n" + formatMatrixAligned(tObs));
          lines.push("Обратная матрица T_o⁻¹:\n" + formatMatrixAligned(tObsInv));
          lines.push(`Матрица Ã_o = T_o⁻¹ A T_o [блоки: упр=${rO}, неупр=${n - rO}]:\n` + formatMatrixAligned(this.multiply(this.multiply(tObsInv, matA), tObs)));
          lines.push(`Матрица C̃_o = C T_o [стб: упр=${rO}, неупр=${n - rO}]:\n` + formatMatrixAligned(this.multiply(cleanC, tObs)));
          lines.push(`Собственные числа ненаблюдаемой части: λ = { ${unOEigs.join(", ")} }`);
          lines.push(isDetectable
            ? "➔ Ненаблюдаемая часть устойчива (Re(λ) < 0).\n➔ Вывод: система ДЕТЕКТИРУЕМА. Наблюдатель построить можно."
            : "➔ Ненаблюдаемая часть неустойчива (Re(λ) ≥ 0).\n➔ Вывод: система НЕ ДЕТЕКТИРУЕМА. Наблюдатель построить нельзя.");
        }
      }
      lines.push("════════════════════════════════");
    }

    if (hasB && hasC && (!isFullCtrl || !isFullObs) && wcMat && woMat) {
      lines.push("3) ПОЛНОЕ 4-БЛОЧНОЕ РАЗЛОЖЕНИЕ КАЛМАНА T = [T₁ T₂ T₃ T₄] (Конспект 3):");
      const imU = this.columnSpaceBasis(wcMat), kerO = this.realNullspaceBasis(woMat), t2 = this.intersectSubspaces(imU, kerO, n);
      const t1 = []; for (const v of imU) if (!this.isInSubspace([...t2, ...t1], v)) t1.push(v);
      const t4 = []; for (const v of kerO) if (!this.isInSubspace([...t2, ...t4], v)) t4.push(v);
      const t3 = [];
      for (let k = 0; k < n && (t1.length + t2.length + t3.length + t4.length) < n; k++) {
        const ev = Array.from({ length: n }, (_, r) => (r === k ? 1 : 0));
        if (!this.isInSubspace([...t1, ...t2, ...t4, ...t3], ev)) t3.push(ev);
      }
      const fmtV = (lst) => lst.length ? lst.map(v => `[${v.map(formatNumber).join("; ")}]ᵀ`).join(", ") : "∅ (нет)";
      lines.push(`1. Образ Im U(A,B) = span{ ${fmtV(imU)} }`);
      lines.push(`2. Ядро Ker O(A,C) = span{ ${fmtV(kerO)} }`);
      lines.push(`3. Пересечение Im U ∩ Ker O (T₂ — упр., ненабл.): ${fmtV(t2)}`);
      lines.push(`4. Блок T₁ (упр., набл.): ${fmtV(t1)}`);
      lines.push(`5. Блок T₄ (неупр., ненабл.): ${fmtV(t4)}`);
      lines.push(`6. Блок T₃ (неупр., набл.): ${fmtV(t3)}`);
      const allCols = [...t1, ...t2, ...t3, ...t4];
      if (allCols.length === n) {
        const tFull = Array.from({ length: n }, (_, r) => allCols.map(c => c[r])), tInv = this.inverse(tFull);
        if (tInv) {
          const aHat = this.multiply(this.multiply(tInv, matA), tFull);
          const n1 = t1.length, n2 = t2.length, n3 = t3.length, n4 = t4.length;
          lines.push(`\nМатрица замены T = [T₁ T₂ T₃ T₄] [стб_калман: T1=${n1}, T2=${n2}, T3=${n3}, T4=${n4}]:\n` + formatMatrixAligned(tFull));
          lines.push("Обратная матрица T⁻¹:\n" + formatMatrixAligned(tInv));
          lines.push(`Матрица Â = T⁻¹ A T [калман: T1=${n1}, T2=${n2}, T3=${n3}, T4=${n4}]:\n` + formatMatrixAligned(aHat));
          lines.push(`Матрица B̂ = T⁻¹ B [стр_калман: T1=${n1}, T2=${n2}, T3=${n3}, T4=${n4}]:\n` + formatMatrixAligned(this.multiply(tInv, cleanB)));
          lines.push(`Матрица Ĉ = C T [стб_калман: T1=${n1}, T2=${n2}, T3=${n3}, T4=${n4}]:\n` + formatMatrixAligned(this.multiply(cleanC, tFull)));

          lines.push("\nАнализ подпространств по блокам матрицы Â:");
          let off = 0;
          if (n1 > 0) {
            const e1 = this.eigenvalues(Array.from({ length: n1 }, (_, i) => Array.from({ length: n1 }, (_, j) => aHat[off + i][off + j])));
            lines.push(`• Управляемая, наблюдаемая часть (T₁, ${n1}×${n1}): λ = { ${e1.join(", ")} }`);
            off += n1;
          }
          if (n2 > 0) {
            const e2 = this.eigenvalues(Array.from({ length: n2 }, (_, i) => Array.from({ length: n2 }, (_, j) => aHat[off + i][off + j])));
            lines.push(`• Управляемая, ненаблюдаемая часть (T₂, ${n2}×${n2}): λ = { ${e2.join(", ")} } ➔ ${e2.every(e => e.re < -1e-7) ? "устойчива (детектируема)" : "неустойчива (не детектируема)"}`);
            off += n2;
          }
          if (n3 > 0) {
            const e3 = this.eigenvalues(Array.from({ length: n3 }, (_, i) => Array.from({ length: n3 }, (_, j) => aHat[off + i][off + j])));
            lines.push(`• Неуправляемая, наблюдаемая часть (T₃, ${n3}×${n3}): λ = { ${e3.join(", ")} } ➔ ${e3.every(e => e.re < -1e-7) ? "устойчива (стабилизируема)" : "неустойчива (не стабилизируема)"}`);
            off += n3;
          }
          if (n4 > 0) {
            const e4 = this.eigenvalues(Array.from({ length: n4 }, (_, i) => Array.from({ length: n4 }, (_, j) => aHat[off + i][off + j])));
            lines.push(`• Неуправляемая, ненаблюдаемая часть (T₄, ${n4}×${n4}): λ = { ${e4.join(", ")} } ➔ ${e4.every(e => e.re < -1e-7) ? "устойчива" : "неустойчива (не стабилизируема и не детектируема)"}`);
          }
        }
      }
      lines.push("════════════════════════════════");
    }

    if (hasB && hasC) {
      lines.push("ИТОГОВЫЙ ВЫВОД ПО БЛОК-СХЕМЕ:");
      lines.push(!isFullCtrl
        ? (isStabilizable ? "✅ Неуправляемая часть устойчива ➔ Система СТАБИЛИЗИРУЕМА (регулятор построить можно)." : "❌ Неуправляемая часть неустойчива (Re λ ≥ 0) ➔ Система НЕ СТАБИЛИЗИРУЕМА (регулятор построить нельзя).")
        : "✅ Система полностью управляема (регулятор построить можно).");
      lines.push(!isFullObs
        ? (isDetectable ? "✅ Ненаблюдаемая часть устойчива ➔ Система ДЕТЕКТИРУЕМА (наблюдатель построить можно)." : "❌ Ненаблюдаемая часть неустойчива (Re λ ≥ 0) ➔ Система НЕ ДЕТЕКТИРУЕМА (наблюдатель построить нельзя).")
        : "✅ Система полностью наблюдаема (наблюдатель построить можно).");
      lines.push("════════════════════════════════");
    }

    if (hasB && isStabilizable) {
      try {
        const flags = this.getControllableModeFlags(matA, cleanB, eigs);
        const hasUnstableCtrl = eigs.some((e, i) => flags[i] && e.re >= -1e-5);
        const usedReals = eigs.map(e => e.re);
        let nextNeg = -1;
        const targetPoles = eigs.map((orig, idx) => {
          if (!flags[idx]) return orig;
          if (!hasUnstableCtrl) {
            while (usedReals.some(r => Math.abs(r - nextNeg) < 1e-4)) nextNeg -= 1;
            usedReals.push(nextNeg);
            return new Complex(nextNeg--, 0);
          }
          if (orig.re < -1e-5) return orig;
          // Зеркалим λ -> -λ только для ЦЕЛЫХ корней (например, 3 -> -3 в Конспекте 2)
          if (Math.abs(orig.im) < 1e-5 && orig.re > 1e-5 && Math.abs(orig.re - Math.round(orig.re)) < 1e-4 && !usedReals.some(r => Math.abs(r + orig.re) < 1e-4)) {
            const mir = -Math.round(orig.re);
            usedReals.push(mir);
            return new Complex(mir, 0);
          }
          while (usedReals.some(r => Math.abs(r - nextNeg) < 1e-4) && Math.abs(orig.im) < 1e-5) nextNeg -= 1;
          usedReals.push(nextNeg);
          return new Complex(nextNeg--, 0);
        });
        const kAuto = this.ackermann(matA, cleanB, targetPoles), thetaAuto = this.multiplyScalar(kAuto, -1);
        const clSpec = this.eigenvalues(this.subtract(matA, this.multiply(cleanB, kAuto)));
        lines.push("ПОСТРОЕНИЕ РЕГУЛЯТОРА:");
        lines.push(`Целевые полюса управляемых мод: ${targetPoles.filter((_, i) => flags[i]).join(", ")}`);
        lines.push(`Спектр замкнутой системы (A − BK): { ${clSpec.join(", ")} }\n`);
        lines.push("Матрица K (для u = −Kx):\n" + formatMatrixAligned(kAuto));
        lines.push("Матрица Θ = −K (для u = Θx, как в конспекте):\n" + formatMatrixAligned(thetaAuto));
        lines.push(`\nЗакон управления:\nu = Θx = ${this.formatControlLaw(thetaAuto)}`);
      } catch (e) { lines.push(`⚠️ Ошибка расчёта регулятора K: ${e.message}`); }
    }

    if (hasC && isDetectable) {
      try {
        const cRow = [cleanC[0]];
        const flags = this.getObservableModeFlags(matA, cRow, eigs);
        const existingReals = eigs.map(e => e.re);
        let obsNeg = -2;
        const targetObsPoles = eigs.map((orig, idx) => {
          if (!flags[idx]) return orig;
          while (existingReals.some(r => Math.abs(r - obsNeg) < 1e-4)) obsNeg -= 1;
          existingReals.push(obsNeg);
          return new Complex(obsNeg--, 0);
        });
        const lAuto = this.luenbergerObserver(matA, cRow, targetObsPoles);
        const obsClSpec = this.eigenvalues(this.subtract(matA, this.multiply(lAuto, cRow)));
        lines.push("════════════════════════════════");
        lines.push("ПОСТРОЕНИЕ НАБЛЮДАТЕЛЯ ЛЮЕНБЕРГЕРА:");
        lines.push(`Целевые полюса наблюдаемых мод: ${targetObsPoles.filter((_, i) => flags[i]).join(", ")}`);
        lines.push(`Спектр ошибки наблюдения (A − LC): { ${obsClSpec.join(", ")} }\n`);
        lines.push(`Матрица наблюдателя L (${n}×1):\n` + formatMatrixAligned(lAuto));
      } catch (e) { lines.push(`⚠️ Ошибка расчёта наблюдателя L: ${e.message}`); }
    }
    return lines.join("\n");
  }
}
