"""Own tests for Assignment 4 (Java implementation).

Usage (from repository root, after `python tools/run.py --setup` has built Java):
    python tests/student/stress_test.py            # random oracle + edge + scale tests
    python tests/student/stress_test.py --seed 7   # different random seed

* Small random inputs are compared against independent brute-force oracles
  written here in Python (they do not share code with the Java solution).
* Edge fixtures in tests/student/edge_cases.json are checked exactly.
* Scale cases use maximum published input sizes and check correctness, not only
  completion. Every batch runs in ONE process, so stale state is also detected.
"""
import argparse
import itertools
import json
import pathlib
import random
import subprocess
import sys
import time

ROOT = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
from public_validate import check  # noqa: E402  (validates witnesses/schemas)

JAVA = ['java', '-Xmx384m', '-cp', str(ROOT / 'Java' / 'build'), 'Main']


def run(requests):
    data = ''.join(json.dumps(r, separators=(',', ':')) + '\n' for r in requests)
    t0 = time.perf_counter()
    res = subprocess.run(JAVA, input=data, capture_output=True, text=True, encoding='utf-8', timeout=60)
    elapsed = time.perf_counter() - t0
    if res.returncode:
        raise SystemExit(res.stderr)
    lines = res.stdout.splitlines()
    assert len(lines) == len(requests), 'line count mismatch'
    return [json.loads(l) for l in lines], elapsed


# ---------------------------------------------------------------- oracles
def o_lcs(a, b):
    # classic length table, independent of the Java suffix form
    prev = [0] * (len(b) + 1)
    for ca in a:
        cur = [0]
        for j, cb in enumerate(b):
            cur.append(prev[j] + 1 if ca == cb else max(prev[j + 1], cur[j]))
        prev = cur
    return {'length': prev[-1]}


def o_merge(sizes):
    # brute force over every adjacent merge order (small n only)
    best = None

    def rec(blocks, cost):
        nonlocal best
        if len(blocks) <= 1:
            best = cost if best is None else min(best, cost)
            return
        for i in range(len(blocks) - 1):
            rec(blocks[:i] + [blocks[i] + blocks[i + 1]] + blocks[i + 2:], cost + blocks[i] + blocks[i + 1])
    rec(list(sizes), 0)
    return {'cost': best}


def o_tree(n, w, edges):
    # brute force over all subsets (n <= 12)
    best = 0
    for mask in range(1 << n):
        if any(mask >> u & 1 and mask >> v & 1 for u, v in edges):
            continue
        best = max(best, sum(w[i] for i in range(n) if mask >> i & 1))
    return {'weight': best}


def o_search(text, pattern):
    return {'positions': [i for i in range(len(text) - len(pattern) + 1) if text[i:i + len(pattern)] == pattern]}


def o_suffix(text):
    sa = sorted(range(len(text)), key=lambda i: text[i:])
    lcp = [0] * len(text)
    for i in range(1, len(text)):
        a, b = text[sa[i - 1]:], text[sa[i]:]
        k = 0
        while k < min(len(a), len(b)) and a[k] == b[k]:
            k += 1
        lcp[i] = k
    best = ''
    for L in range(len(text), 0, -1):
        subs = {}
        for i in range(len(text) - L + 1):
            subs[text[i:i + L]] = subs.get(text[i:i + L], 0) + 1
        rep = [s for s, c in subs.items() if c >= 2]
        if rep:
            best = rep[0]
            break
    return {'sa': sa, 'lcp': lcp, 'repeated': best}


def o_aho(patterns, texts):
    return {'counts': [[sum(t.startswith(p, i) for i in range(len(t))) for p in patterns] for t in texts]}


def random_tree(n, rng, wlo=-20, whi=20):
    edges = [[rng.randrange(i), i] for i in range(1, n)]
    perm = list(range(n))
    rng.shuffle(perm)
    edges = [[perm[u], perm[v]] if rng.random() < .5 else [perm[v], perm[u]] for u, v in edges]
    rng.shuffle(edges)
    return {'task': 'tree', 'n': n, 'weights': [rng.randint(wlo, whi) for _ in range(n)], 'edges': edges}


def rstr(rng, n, alpha):
    return ''.join(rng.choice(alpha) for _ in range(n))


def random_cases(rng, count):
    cases = []
    for _ in range(count):
        al = rng.choice(['ab', 'abc', 'aA .', 'xyz!#'])
        a, b = rstr(rng, rng.randint(0, 12), al), rstr(rng, rng.randint(0, 12), al)
        cases.append(({'task': 'lcs', 'a': a, 'b': b}, o_lcs(a, b)))
        sz = [rng.choice([0, 1, 2, 5, 10, 10**9]) for _ in range(rng.randint(0, 6))]
        cases.append(({'task': 'merge', 'sizes': sz}, o_merge(sz)))
        x = random_tree(rng.randint(0, 11), rng) if rng.random() < .95 else {'task': 'tree', 'n': 0, 'weights': [], 'edges': []}
        cases.append((x, o_tree(x['n'], x['weights'], x['edges'])))
        t, p = rstr(rng, rng.randint(0, 25), al[:2]), rstr(rng, rng.randint(0, 4), al[:2])
        for task in ('naive', 'kmp', 'rk'):
            cases.append(({'task': task, 'text': t, 'pattern': p}, o_search(t, p)))
        s = rstr(rng, rng.randint(0, 20), rng.choice(['a', 'ab', 'abc', 'banana ']))
        cases.append(({'task': 'suffix', 'text': s}, o_suffix(s)))
        pats = [rstr(rng, rng.randint(1, 3), 'ab') for _ in range(rng.randint(0, 5))]
        texts = [rstr(rng, rng.randint(0, 15), 'abc') for _ in range(rng.randint(0, 3))]
        cases.append(({'task': 'aho', 'patterns': pats, 'texts': texts}, o_aho(pats, texts)))
    return cases


def scale_cases(rng):
    out = []
    a = rstr(rng, 1000, 'ACGT'); b = rstr(rng, 1000, 'ACGT')
    out.append(('lcs 1000x1000', {'task': 'lcs', 'a': a, 'b': b}, o_lcs(a, b)))
    sz = [rng.randint(0, 10**9) for _ in range(200)]
    # O(n^3) oracle in Python for n=200 is fine
    pre = [0]
    for s in sz:
        pre.append(pre[-1] + s)
    dp = [[0] * 200 for _ in range(200)]
    for ln in range(2, 201):
        for l in range(0, 201 - ln):
            r = l + ln - 1
            dp[l][r] = min(dp[l][k] + dp[k + 1][r] for k in range(l, r)) + pre[r + 1] - pre[l]
    out.append(('merge n=200 large sizes', {'task': 'merge', 'sizes': sz}, {'cost': dp[0][199]}))
    n = 100000
    chain = {'task': 'tree', 'n': n, 'weights': [10**9] * n, 'edges': [[i, i + 1] for i in range(n - 1)]}
    out.append(('tree deep chain 1e5', chain, {'weight': 10**9 * (n // 2)}))
    star = {'task': 'tree', 'n': n, 'weights': [10**9] + [-1] * (n - 1), 'edges': [[0, i] for i in range(1, n)]}
    out.append(('tree star 1e5', star, {'weight': 10**9}))
    t = 'a' * 200000; p = 'a' * 100000
    out.append(('kmp 200k/100k all a', {'task': 'kmp', 'text': t, 'pattern': p}, {'positions': list(range(100001))}))
    t = rstr(rng, 10000, 'ab'); p = 'ab' * 1000
    out.append(('rk 10000x2000', {'task': 'rk', 'text': t, 'pattern': p}, o_search(t, p)))
    t = 'a' * 3000; p = 'a' * 1500
    out.append(('naive 3000x1500 all a', {'task': 'naive', 'text': t, 'pattern': p}, {'positions': list(range(1501))}))
    s = 'a' * 50000
    out.append(('suffix all a 50000', {'task': 'suffix', 'text': s},
                {'sa': list(range(49999, -1, -1)), 'lcp': list(range(50000)), 'repeated': 'a' * 49999}))
    pats = ['a' * (i % 10 + 1) for i in range(10000)]
    texts = ['a' * 2000] * 100
    row = [2000 - len(q) + 1 for q in pats]
    out.append(('aho dense 10000 patterns x 100 texts', {'task': 'aho', 'patterns': pats, 'texts': texts}, {'counts': [row] * 100}))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--seed', type=int, default=2026)
    ap.add_argument('--count', type=int, default=400)
    args = ap.parse_args()
    rng = random.Random(args.seed)
    failures = 0

    edge = json.loads((ROOT / 'tests' / 'student' / 'edge_cases.json').read_text(encoding='utf-8'))
    answers, _ = run([c['input'] for c in edge])
    for c, y in zip(edge, answers):
        if not check(c['input'], y, c['output']):
            failures += 1
            print('FAIL edge', c['name'], '->', y)
    print(f'edge fixtures: {len(edge)} cases, {failures} failures')

    cases = random_cases(rng, args.count)
    answers, _ = run([x for x, _ in cases])  # one mixed process: detects stale state
    f0 = failures
    for (x, e), y in zip(cases, answers):
        if x['task'] == 'suffix':
            ok = check(x, y, e)
        elif x['task'] in ('naive', 'kmp', 'rk', 'aho'):
            ok = check(x, y, e) if x['task'] != 'aho' else y['counts'] == e['counts']
        else:
            ok = check(x, y, e)
        if not ok:
            failures += 1
            print('FAIL random', json.dumps(x)[:200], '->', json.dumps(y)[:200])
    print(f'random oracle (seed {args.seed}): {len(cases)} cases, {failures - f0} failures')

    for name, x, e in scale_cases(rng):
        (y,), sec = run([x])
        ok = y['counts'] == e['counts'] if x['task'] == 'aho' else check(x, y, e)
        failures += not ok
        print(f'{"PASS" if ok else "FAIL"} scale {name}: {sec:.2f}s (incl. JVM start)')
    print('ALL PASSED' if not failures else f'{failures} FAILURES')
    return int(bool(failures))


if __name__ == '__main__':
    sys.exit(main())
