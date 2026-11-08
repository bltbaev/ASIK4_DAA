# Assignment 4 — analysis

## Environment and commands

- **Language:** Java (`submission.json`: `"language": "Java"`).


Commands (run from the repository root):

```sh
python tools/run.py --setup                      # compile + ping
python tools/run.py --check                      # 44 public cases + repeated mixed batch
python tools/run.py --input examples/lcs.jsonl   # any JSONL request file
python tests/student/stress_test.py              # own tests: edge fixtures, random oracle, scale
python tests/student/stress_test.py --seed 99 --count 300
javac --release 17 -cp Java/build -d Java/build tests/student/SearchBench.java
java -Xmx384m -cp Java/build SearchBench 42      # naive/KMP/RK comparison
```

Results of my runs: `--check` printed `PASS` for all 44 public cases and for
"state reset: two complete batches in one process". `stress_test.py` printed
`edge fixtures: 32 cases, 0 failures`, `random oracle (seed 2026): 3200 cases, 0 failures`,
all 9 scale cases `PASS`, and `ALL PASSED` (also with seed 99).

Only `Java/source/Algorithms.java` was changed; the JSON transport (`Main.java`, `Json.java`) is untouched.
Integers from the parser arrive as `Long`; all outputs are `Long`/`List<Long>`, so costs, weights and counts
are exact 64-bit integers.

## Algorithm explanations

### lcs — longest common subsequence

- **State.** `dp[i][j]` = LCS length of the *suffixes* `a[i..]` and `b[j..]`, for `0 ≤ i ≤ |a|`, `0 ≤ j ≤ |b|`.
  Stored in one flat `int[(|a|+1)(|b|+1)]`.
- **Base cases.** `dp[|a|][j] = dp[i][|b|] = 0` (an empty suffix has no common subsequence). If either string is
  empty the method returns `length=0, sequence=""` before allocating anything.
- **Transition.** If `a[i] == b[j]`: `dp[i][j] = 1 + dp[i+1][j+1]`. Otherwise `dp[i][j] = max(dp[i+1][j], dp[i][j+1])`.
  Mismatch: an optimal common subsequence cannot use both `a[i]` and `b[j]` as its first character (they differ),
  so at least one of them is unused and can be dropped — both options are covered by the max. Match: some optimal
  solution starts with the pair `(a[i], b[j])` (exchange argument: if an optimum starts with `a[i]` matched to a later
  `b[j']`, re-match it to `b[j]`), so `1 + dp[i+1][j+1]` is optimal.
- **Order.** `i` from `|a|-1` down to 0, `j` from `|b|-1` down to 0; the three cells used (`i+1`, `j+1`) are already filled.
- **Reconstruction.** Walk forward from `(0,0)`: on a match append the character and move diagonally; otherwise move to the
  neighbour that holds the larger value (ties go to `i+1`). Each step keeps `dp` equal to the remaining optimum, so the
  output has length `dp[0][0]` and is a subsequence of both strings. Ties may yield a different but equally long sequence.
- **Complexity.** Time `O((|a|+1)(|b|+1))`, auxiliary memory `O((|a|+1)(|b|+1))` ints (≤ ~4 MB at the 10⁶ limit),
  output `O(LCS)`.
- **Test.** `tea_time`/`team` → length 4 `team`: catches a *substring* (contiguous) DP by mistake. `ab`/`ba` → length 1:
  either `a` or `b` is accepted, which checks tie handling.

### merge — ordered adjacent-block merging (interval DP)

- **State.** `cost[l][r]` = minimum total cost to turn the original blocks `l..r` into one block. `split[l][r]` stores an optimal `k`.
- **Base / boundary.** `cost[i][i] = 0`. For `n ≤ 1` return `cost=0, plan=[]`.
- **Transition (last split).** The *last* merge that creates `[l,r]` joins some `[l,k]` and `[k+1,r]`; it costs
  `sum(l..r)` regardless of `k`, and the two sides are independent subproblems:
  `cost[l][r] = min_{l≤k<r}(cost[l][k] + cost[k+1][r]) + sum(l..r)`. Sums come from a `long` prefix array.
- **Order.** By increasing interval length, so both shorter sides are ready.
- **Correctness.** Every valid merge sequence for `[l,r]` has a unique last merge, so it is counted under one `k`;
  optimal substructure holds because the operations inside `[l,k]` and inside `[k+1,r]` never interact.
  Unlike matrix-chain, the cost of a merge is the total size of the interval, not a product of dimensions.
  Greedy "merge the two smallest blocks" is wrong because those blocks may not be adjacent; e.g. `[1,100,1]` cannot
  merge the two 1s first. Optimum: 101 + 102 = 203.
- **Reconstruction.** Post-order traversal of the `split` tree with an explicit stack: left child, right child, then
  `[l,k,r]`. A parent must be printed after its children because when `[l,k,r]` is executed the blocks `[l,k]` and
  `[k+1,r]` must already exist. Exactly `n-1` merges are produced (one per internal node of a binary tree with `n` leaves).
- **Complexity.** `O(n³)` time, `O(n²)` auxiliary memory (`long` and `int` tables, 200×200), `O(n)` output.
  Maximum cost ≈ 200·200·10⁹ = 4·10¹³ fits in `long`.
- **Test.** `[10⁹,10⁹,10⁹,10⁹]` → 8·10⁹: detects 32-bit overflow. `[0,0,0,0]`: many optimal plans, any valid one passes.

### tree — maximum-weight independent set on a tree

- **State.** For a vertex `v` (tree rooted at 0): `take[v]` = best weight in `v`'s subtree with `v` selected,
  `skip[v]` = best weight with `v` not selected.
- **Transition.** `take[v] = w[v] + Σ skip[c]`, `skip[v] = Σ max(take[c], skip[c])` over children `c`.
  Leaves: `take = w`, `skip = 0`. The empty set is allowed, so `skip` never goes below 0 and all-negative trees give 0.
- **Order.** Adjacency is stored in CSR arrays; an iterative BFS from vertex 0 gives an order where each parent is
  before its children. Processing that order *in reverse* is a valid post-order (children are finished before
  parents) and avoids recursion, so a 100 000-vertex chain cannot overflow the stack.
- **Correctness.** If `v` is taken, no child may be taken (edge constraint), and children's subtrees are otherwise
  independent; if `v` is skipped, each child is free. The answer is `max(take[root], skip[root])`. Any root gives the
  same optimum because the DP is exact for every rooting.
- **Reconstruction.** Top-down in BFS order: if the parent was chosen, the child is forced to be skipped; otherwise the
  child is chosen iff `take > skip` (ties choose skip, still optimal). This follows exactly the decisions behind the
  optimum, so the selected set sums to the reported weight and has no adjacent pair; each vertex is visited once.
- **Complexity.** `O(n+1)` time and auxiliary memory (arrays of size `n` and `2(n-1)`), sums in `long`
  (max 10⁵·10⁹ = 10¹⁴).
- **Test.** Deep chain of 100 000 vertices with weight 10⁹ → 5·10¹³: detects recursion stack overflow and int overflow.
  Vertex 1 attached as a leaf under 0 (`edges` given as `[1,0]`) checks that edge direction does not matter.

### naive — baseline exact search

- **Invariant.** For each start `s` with `s + m ≤ n` (last valid start is `n-m`), compare `t[s+j]` with `p[j]` until a mismatch
  or `j = m`; report `s` when all `m` match. Every start is tried, so overlaps are found.
- **Boundaries.** `m = 0` → all boundaries `0..n` (`n+1` positions, including `n`). `m > n` → the loop does not run → `[]`.
- **Complexity.** `O(n·m + n + 1)` time, `O(1)` auxiliary memory, `O(z+1)` output. No `indexOf`/`contains` used.
- **Test.** `aaaaa`/`aaa` → `[0,1,2]` (overlaps); `abc`/`""` → `[0,1,2,3]` (final boundary included).

### kmp — Knuth–Morris–Pratt

- **Prefix function.** `pi[i]` = length of the longest proper prefix of `p[0..i]` that is also a suffix of it.
  Computed by extending `k = pi[i-1]`; on a mismatch fall back `k = pi[k-1]`.
- **Search invariant.** `k` = length of the longest prefix of `p` that is a suffix of the text read so far.
  On mismatch `t[i] ≠ p[k]` fall back `k = pi[k-1]` (the next shorter border is the next candidate; nothing shorter
  is skipped). After a full match (`k = m`) report `i-m+1` and set `k = pi[m-1]`, which keeps overlapping matches
  (e.g. `abababa`/`aba` → `[0,2,4]`).
- **Boundaries.** `m = 0` returns `0..n` before any indexing of `p` or `pi`; `m > n` returns `[]`.
- **Complexity.** `O(n + m + z + 1)` time: `k` grows at most once per text character, and each fallback decreases it,
  so total fallbacks ≤ n (similarly ≤ m in the prefix function). `O(m+1)` auxiliary memory (`pi`), `O(z+1)` output.
- **Test.** 200 000 `a`'s with a 100 000-`a` pattern → 100 001 positions in ~0.2 s, and `aaaaa`/`aa` → `[0,1,2,3]`
  (a version that resets `k=0` after a match would miss overlaps).

### rk — Rabin–Karp (base 31, mod 101)

- **Hash.** `h(s) = (s[0]·31^{m-1} + s[1]·31^{m-2} + … + s[m-1]) mod 101`, ASCII codes, leftmost character has the highest power.
- **Rolling update.** `high = 31^{m-1} mod 101`. Moving the window from `s` to `s+1`:
  `h ← (h − t[s]·high) mod 101`, add 101 if negative (Java `%` can return a negative remainder),
  then `h ← (h·31 + t[s+m]) mod 101`. All values stay < 101·127, so `long` is more than enough.
- **Verification.** Equal hash only makes a *candidate*: the characters are compared before reporting.
  `ab` and `dj` both hash to 75 (checked: 97·31+98 = 3105 ≡ 75; 100·31+106 = 3206 ≡ 75), so `abdj`/`ab` → `[0]` and `djdj`/`ab` → `[]`.
- **Boundaries.** `m = 0` → `0..n`; `m > n` → `[]`, both handled before forming a window.
- **Complexity.** For `1 ≤ m ≤ n`: `O(n + m + c·m)` where `c` = number of equal-hash candidate windows.
  With only 101 hash values, about 1/101 of random windows collide, and on repetitive inputs every window can match,
  so the worst case is `O(n·m)`; it is **not** deterministic linear time. Auxiliary memory `O(1)` besides output (no prefix-hash arrays).
- **Test.** `rk-collision-only` (`djdj` / `ab`) detects a version that trusts the hash. `rk-negative-remainder`
  (text starts with `~` = 126, so `h − 126·high` goes negative) detects a missing `+101`.

### suffix — suffix array by prefix doubling, LCP by Kasai

**Prefix doubling.**
- **Invariant.** After the round with length `k`, `rank[i]` is the class of the first `2k` characters of suffix `i`
  (shorter suffixes simply end earlier; no sentinel is added), and `sa` is sorted by that prefix.
- **Round 0.** Counting sort by the first character (ASCII < 128) and assign ranks.
- **Round k.** Sort suffixes by the pair `(rank[i], rank[i+k])`, where a missing second half (`i+k ≥ n`) is the
  smallest key (−1). Implemented as two stable counting sorts (LSD radix: by second key, then by first key),
  then re-rank by comparing pairs of neighbours. Pairs order the `2k`-prefixes correctly because two prefixes of
  length `2k` compare by their first halves and, on a tie, by their second halves; a missing half means the suffix
  ended, and a shorter string that is a prefix of another is smaller.
- **Stop** when all ranks are distinct or `k ≥ n`.
- **Complexity.** Each round `O(n)` (counting sort over ≤ n classes), `⌈log₂ n⌉` rounds → `O(n log(n+1))` time,
  `O(n+1)` auxiliary arrays. Suffix strings are never materialised.

**Kasai.**
- `lcp[r]` compares `sa[r-1]` and `sa[r]`, `lcp[0] = 0`. Process suffixes in text order `i = 0..n-1` and keep `h`.
- **Reuse.** If suffix `i` shares `h` characters with its predecessor `j` in `sa`, then suffix `i+1` shares at least
  `h−1` with suffix `j+1`, which is ≤ the predecessor of `i+1` in sorted order; so `h` decreases by at most 1 per step.
  `h` increases at most `2n` times in total → `O(n)` time.
- **Longest repeat.** A substring occurs at two distinct starts iff it is a common prefix of two suffixes. The longest
  common prefix of any two suffixes is reached by some *adjacent* pair in `sa` (suffixes between them in sorted order
  share at least that prefix), so `max(lcp)` is the longest repeat length; I output `text[sa[r] .. sa[r]+max)`.
  Overlapping occurrences are allowed (`banana` → `ana`). If `max(lcp) = 0`, `repeated = ""`.
- **Test.** `aaaa` → `sa=[3,2,1,0]`, `lcp=[0,1,2,3]`, `aaa`: catches a sentinel/empty suffix in the output and wrong
  shorter-suffix ordering. `dcba` → all `lcp=0`, `repeated=""`. 50 000 equal letters runs in 0.2 s (comparison of
  materialised suffixes would be quadratic).

### aho — Aho–Corasick dictionary counting

- **Trie.** Flat `int` goto table of `states × 95` (printable ASCII 32–126), size bounded by total pattern length + 1.
  `term[i]` = terminal state of dictionary position `i`. **Duplicates** reach the same state, so they automatically
  receive equal counts while each keeps its own output column.
- **Failure links.** BFS from the root: `fail[u]` = state of the longest proper suffix of `u`'s string that is in the trie.
  Missing transitions are filled with `go[fail[v]][c]`, turning the trie into a full DFA (root's missing edges loop to root).
  BFS order guarantees that `fail[v]` (shallower) is finished before `v`'s children.
- **Counting (reverse-failure propagation).** For each text, reset `visits[]` to 0, walk the automaton and add 1 to the
  state reached after each character. Then process states in *reverse BFS order* and do `visits[fail[u]] += visits[u]`.
  Afterwards `visits[s]` = number of text positions where the string of `s` ends (every state whose string ends at that
  position lies on the failure chain of the current state). This counts suffix patterns (e.g. `he` inside `she`) and
  overlaps, without walking an output list for each match, so dense inputs stay linear.
- **Independent texts.** `visits` is cleared for every text; the automaton is built once per request and no state
  survives between requests. Empty dictionary → one empty row per text; no texts → `[]`; empty text → a row of zeros.
- **Complexity.** Construction `O(L + 95·S + p)` time and `O(95·S + p)` memory; each text `O(|text| + S + p)`;
  output `O(q·(p+1)+1)`.
- **Test.** `["aa","a","aa","baa"]` on `baaa` → `[2,3,2,1]`: duplicates, suffix patterns and overlaps at once.
  Dense case: 10 000 patterns `a…a` × 100 texts of 2000 `a`'s finishes in 0.42 s; output-link traversal would perform
  ~10⁹ steps here.

## Own tests

Files: `tests/student/edge_cases.json` (32 fixtures with expected output), `tests/student/stress_test.py`
(runner + independent Python brute-force oracles + scale cases), `tests/student/SearchBench.java` (comparison).
All fixtures and random cases are sent to **one** Java process, which also detects stale state between requests.
Expected values of the fixtures were cross-checked against the Python oracles.

|Command|Input or fixture path|Expected result|Actual result / log|What it checks|
|---|---|---|---|---|
|lcs|`edge_cases.json` lcs-both-empty / lcs-one-empty|`0, ""`|`0, ""` PASS|empty inputs|
|lcs|`{"a":"tea_time","b":"team"}`|`4, "team"`|`4, "team"` PASS|subsequence, not substring|
|lcs|`{"a":"ab","b":"ba"}`|length 1, `a` or `b`|`1, "b"` PASS (validator accepts any witness)|ties / alternative witness|
|lcs|`{"a":"aAaA","b":"AAaa"}`|length 2|`2, "AA"` PASS|case sensitivity, repeated characters|
|lcs|scale: random 1000×1000 over ACGT|length from Python DP oracle|PASS, 0.09 s incl. JVM|max size, memory|
|merge|`sizes: []`, `[7]`|`0, []`|`0, []` PASS|0/1 blocks|
|merge|`[0,0,0,0]`|cost 0, 3 merges|PASS|zeros, multiple optimal plans|
|merge|`[1,100,1]`|203|203, plan `[[1,1,2],[0,0,2]]` PASS (also optimal)|adjacency vs. arbitrary greedy|
|merge|`[10⁹ ×4]`|8000000000|8000000000 PASS|64-bit cost|
|merge|scale: n=200 random sizes ≤10⁹|cost from Python O(n³) oracle|PASS, 0.09 s|max n, plan chronology (validator replays it)|
|tree|n=0; n=1 weight −5; all negative|weight 0, `[]`|PASS|empty set allowed|
|tree|`[0,0,0]` star|0|PASS|zero ties|
|tree|`[1,10,1,10]`, edges `[[1,0],[0,2],[2,3]]`|20|`20, [1,3]` PASS|edge direction / root choice|
|tree|scale: chain of 100 000, weights 10⁹|5·10¹³|PASS, 0.35 s|no recursion overflow, 64-bit sum|
|tree|scale: star of 100 000, centre 10⁹, leaves −1|10⁹|PASS, 0.29 s|high degree vertex|
|naive|`"abc"`, pattern `""`|`[0,1,2,3]`|PASS|empty pattern, final boundary|
|naive|`"A a, a! a"`, `" a"`|`[1,4,7]`|PASS|case, spaces, punctuation|
|kmp|`""`, `""`|`[0]`|PASS|empty text and pattern|
|kmp|`"ab"`, `"abc"`|`[]`|PASS|pattern longer than text|
|kmp|`"aaaaa"`, `"aa"`|`[0,1,2,3]`|PASS|fallback after full match keeps overlaps|
|kmp|scale: `a`×200000 / `a`×100000|100001 positions|PASS, 0.20 s|linear time at max size|
|rk|`"abdj"`, `"ab"`|`[0]`|PASS|known collision ab/dj = 75|
|rk|`"djdj"`, `"ab"`|`[]`|PASS|equal hash is only a candidate|
|rk|`"~~ !~ !"`, `" !"`|`[2,5]`|PASS|negative remainder normalization|
|rk|`""`, `"a"`|`[]`|PASS|empty text|
|naive/kmp/rk|random: 400 × 3 identical text/pattern pairs (alphabet of 2)|Python slicing oracle|0 failures|identical outputs on common domain|
|suffix|`""`; `"z"`|`[],[],""`; `[0],[0],""`|PASS|empty / one char|
|suffix|`"aaaa"`|`[3,2,1,0]`,`[0,1,2,3]`,`aaa`|PASS|all equal, no sentinel, overlapping repeat|
|suffix|`"dcba"`|`lcp` all 0, `""`|PASS|all distinct|
|suffix|`"abxcd abycd"`|repeated of length 2 (`ab` or `cd`)|`ab` PASS|tied longest repeats, space character|
|suffix|scale: `a`×50000|`sa` descending, `lcp=[0..49999]`|PASS, 0.20 s|worst case for doubling rounds|
|aho|patterns `[]`, texts `["abc",""]`|`[[],[]]`|PASS|empty dictionary|
|aho|texts `[]`|`[]`|PASS|no texts|
|aho|`["aa","a","aa","baa"]` on `["baaa","","baaa"]`|`[[2,3,2,1],[0,0,0,0],[2,3,2,1]]`|PASS|duplicates, suffix patterns, overlaps, reset between texts|
|aho|scale: 10000 patterns × 100 texts of 2000 `a`|row `2001−len`|PASS, 0.42 s|dense matches need propagation|
|all|`stress_test.py` random oracle, seed 2026: 3200 mixed requests in one process|brute force|0 failures|stale state, all tasks|
|all|`tools/run.py --check`|public expected|44 PASS + state reset PASS|public contract|

## Search comparison

- **Generator:** `tests/student/SearchBench.java`, `java.util.Random` with seed `42` (per-case seed derived from it).
- **Cases (same text/pattern given to all three methods):**
  - *random*: text uniform over 26 lowercase letters; pattern = substring copied from a random text position (so at least one match).
  - *repetitive*: text = `a`ⁿ, pattern = `a`^(m−1)`b` (no match, but every window matches m−1 characters before failing).
- **Sizes:** (n, m) = (750, 10), (1500, 50), (3000, 200), all inside the common domain n, m ≤ 3000.
- **Correctness first:** before timing, outputs of naive, KMP and RK are compared; all were identical (`outputs identical = true`).
- **Timing scope:** only the call `Algorithms.naive/kmp/rk(map)` on a pre-built input map (no JSON, no I/O, no JVM start-up,
  no compilation), measured with `System.nanoTime()`, unit µs. Same boundary for all methods.
- **Warm-up/JIT:** 20 unmeasured calls per method and case before 5 measured calls; the median of the 5 is reported.
- **Hardware/runtime:** Intel Xeon @ 2.10 GHz (2 vCPU, shared cloud VM), OpenJDK 21.0.12.1, compiled `--release 17`.
- Raw output saved in `tests/student/bench_output.txt`.

|Case|n|m|Seed/generator|Algorithm|Run 1|Run 2|Run 3|Run 4|Run 5|Median (µs)|Correct outputs checked|
|---|---:|---:|---|---|---:|---:|---:|---:|---:|---:|---|
|random|750|10|42 / SearchBench|naive|49|49|49|49|76|49|yes, 1 match|
|random|750|10|42 / SearchBench|kmp|50|47|46|46|47|47|yes|
|random|750|10|42 / SearchBench|rk|93|97|73|90|74|90|yes|
|random|1500|50|42 / SearchBench|naive|95|99|95|102|140|99|yes, 1 match|
|random|1500|50|42 / SearchBench|kmp|101|111|102|94|98|101|yes|
|random|1500|50|42 / SearchBench|rk|150|159|134|167|161|159|yes|
|random|3000|200|42 / SearchBench|naive|92|127|154|123|154|127|yes, 1 match|
|random|3000|200|42 / SearchBench|kmp|204|351|212|230|230|230|yes|
|random|3000|200|42 / SearchBench|rk|125|61|59|58|88|61|yes|
|repetitive|750|10|42 / SearchBench|naive|96|94|94|807|167|96|yes, 0 matches|
|repetitive|750|10|42 / SearchBench|kmp|37|37|37|67|42|37|yes|
|repetitive|750|10|42 / SearchBench|rk|23|26|29|21|22|23|yes|
|repetitive|1500|50|42 / SearchBench|naive|389|462|407|411|392|407|yes, 0 matches|
|repetitive|1500|50|42 / SearchBench|kmp|109|149|84|71|134|109|yes|
|repetitive|1500|50|42 / SearchBench|rk|56|52|50|68|116|56|yes|
|repetitive|3000|200|42 / SearchBench|naive|336|399|277|201|227|277|yes, 0 matches|
|repetitive|3000|200|42 / SearchBench|kmp|162|163|154|191|158|162|yes|
|repetitive|3000|200|42 / SearchBench|rk|101|96|165|105|101|101|yes|

**Observations.**
- On *random* text, naive is as fast as KMP: a random window usually mismatches at the first or second character
  (probability 1/26 to continue), so naive does ~n comparisons, close to its best case. KMP has similar work plus
  the prefix-function overhead. RK pays for the modular rolling update on every window, so it is not faster at small m.
- On *repetitive* text, naive does about m comparisons per window (`a…ab` fails only at the last character),
  i.e. ~(n−m+1)·m ≈ 560 000 comparisons for (3000, 200); KMP stays linear because `k` falls back via `pi` instead of
  restarting. The naive median grows faster than KMP's (96→407 µs vs 37→109 µs from 750 to 1500).
  The (3000, 200) naive median (277) is lower than for (1500, 50), which shows that these microsecond timings
  are noisy (JIT tiering, shared VM): **medians are observations, not a proof of asymptotic complexity.**
- RK is fast on the repetitive case because the hash of `a…ab` differs from the hash of `a…a`, so there are no
  candidate windows to verify — the cost is just one rolling update per window.
- **Effect of modulus 101.** With only 101 possible hash values, about 1 in 101 non-matching random windows is a
  false candidate and must be verified character by character (that is why the `ab`/`dj` collision test exists).
  An adversary can easily make *every* window a candidate (e.g. pattern `a…a` on text `a…a`, where each window is
  also a true match), giving `O(n·m)` time. This deliberately small modulus is a collision laboratory; production
  implementations use a 61/64-bit modulus or double hashing and random bases, which make collisions very rare
  in practice — but even then the worst case is not deterministically linear, and verification is still needed for exact results.

## Sources and AI assistance

- **Sources:** the assignment specification and `docs/LECTURE_CLARIFICATIONS_EN_RU.md`, lecture 8 (DP patterns),
  lectures 9–10.
- **AI assistance:** I used Claude  to understand the conditions of the assignment and to understand the algorithms to analyze the results.
